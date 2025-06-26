package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import me.qoomon.gitversioning.commons.GitDescription;
import me.qoomon.gitversioning.commons.GitSituation;
import me.qoomon.gitversioning.commons.Lazy;
import me.qoomon.maven.gitversioning.Configuration.PatchDescription;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.*;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.*;
import static me.qoomon.gitversioning.commons.GitRefType.*;
import static me.qoomon.gitversioning.commons.StringUtil.*;
import static me.qoomon.maven.gitversioning.BuildProperties.projectArtifactId;
import static me.qoomon.maven.gitversioning.GitVersioningMojo.asPlugin;
import static me.qoomon.maven.gitversioning.MavenUtil.*;
import static org.apache.maven.shared.utils.StringUtils.leftPad;
import static org.apache.maven.shared.utils.StringUtils.repeat;
import static org.apache.maven.shared.utils.StringUtils.rightPad;
import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Wrapper for {@link ModelProcessor} to adapt versions.
 */
@Named("core-default")
@Singleton
public class GitVersioningModelProcessor implements ModelProcessor {

    private static final Pattern VERSION_PATTERN = Pattern.compile(".*?(?<version>(?<core>(?<major>\\d+)(?:\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?)?)(?:-(?<label>.*))?)|");

    private static final String OPTION_NAME_GIT_REF = "git.ref";
    private static final String OPTION_NAME_GIT_TAG = "git.tag";
    private static final String OPTION_NAME_GIT_BRANCH = "git.branch";
    private static final String OPTION_NAME_DISABLE = "versioning.disable";
    private static final String OPTION_UPDATE_POM = "versioning.updatePom";
    private static final String OPTION_CONFIG_FILE_NAME = "versioning.configFile";

    static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";

    final private Logger logger = getLogger(GitVersioningModelProcessor.class);

    // gets injected by setter, see below
    private ModelProcessor delegatedModelProcessor;

    @Inject
    void setDelegatedModelProcessor(List<ModelProcessor> modelProcessors) {
        this.delegatedModelProcessor = modelProcessors.stream()
                // Avoid circular dependency
                .filter(modelProcessor -> !Objects.equals(modelProcessor, this))
                .findFirst()
                // There is normally always at least one implementation available: org.apache.maven.model.building.DefaultModelProcessor
                .orElseThrow(() -> new NoSuchElementException("Unable to find default ModelProcessor"));
        logger.debug("Delegated ModelProcessor: {}", this.delegatedModelProcessor);
    }

    @Inject
    private SessionScope sessionScope;

    private boolean initialized = false;

    private Configuration config;

    // --- following fields will be initialized by init() method -------------------------------------------------------
    private MavenSession mavenSession; // can't be injected, cause it's not available before model read
    private File mvnDirectory;
    private GitSituation gitSituation;

    private boolean disabled = false;
    private GitVersionDetails gitVersionDetails;
    boolean updatePom = false;

    private Map<String, Supplier<String>> globalFormatPlaceholderMap;
    private Set<GAV> relatedProjects;


    // ---- other fields -----------------------------------------------------------------------------------------------

    private final Map<File, Model> sessionModelCache = new HashMap<>();

    @Override
    public File locatePom(File projectDirectory) {
        return delegatedModelProcessor.locatePom(projectDirectory);
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        // clone model before return to prevent concurrency issues
        return processModel(delegatedModelProcessor.read(input, options), options).clone();
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        // clone model before return to prevent concurrency issues
        return processModel(delegatedModelProcessor.read(input, options), options).clone();
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        // clone model before return to prevent concurrency issues
        return processModel(delegatedModelProcessor.read(input, options), options).clone();
    }


    private void init(Model projectModel) throws IOException {
        if (logger.isInfoEnabled()) {
            logger.info("");
            logger.info(extensionLogHeader(BuildProperties.projectGAV()));
        }

        // In case another ModelProcessor is used (like for example polyglot extension),
        // we need to work on the POM possibly translated into XML
        File pomFile = locatePom(projectModel.getProjectDirectory());
        if (pomFile == null) {
            logger.debug("skip - project model does not belong to a local project");
            disabled = true;
            return;
        }

        if (!pomFile.isFile()) {
            logger.debug("skip - pom file does not exist {}", pomFile);
            disabled = true;
            return;
        }

        // check if session is available
        try {
            mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
        } catch (OutOfScopeException ex) {
            logger.warn("skip - no maven session present");
            disabled = true;
            return;
        }

        logger.debug("pom file: {}", pomFile);
        mvnDirectory = findMvnDirectory(pomFile);
        logger.debug(".mvn directory: {}", mvnDirectory);

        String configFileName = getCommandOption(OPTION_CONFIG_FILE_NAME);
        if(configFileName == null){
            configFileName = projectArtifactId() + ".xml";
        }

        final File configFile = new File(mvnDirectory, configFileName);
        logger.debug("read config from {}", configFile);
        config = readConfig(configFile);

        // check if extension is disabled by command option
        final String commandOptionDisable = getCommandOption(OPTION_NAME_DISABLE);
        if (commandOptionDisable != null) {
            disabled = parseBoolean(commandOptionDisable);
            if (disabled) {
                logger.info("skip - versioning is disabled by command option");
                return;
            }
        } else {
            // check if extension is disabled by config option
            disabled = config.disable != null && config.disable;
            if (disabled) {
                logger.info("skip - versioning is disabled by config option");
                return;
            }
        }

        // determine git situation
        gitSituation = getGitSituation(pomFile);
        if (gitSituation == null) {
            logger.warn("skip - project is not part of a git repository");
            disabled = true;
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("git situation:");
            logger.debug("  root directory: {}", gitSituation.getRootDirectory());
            logger.debug("  head commit: {}", gitSituation.getRev());
            logger.debug("  head commit timestamp: {}", gitSituation.getTimestamp());
            logger.debug("  head branch: {}", gitSituation.getBranch());
            logger.debug("  head tags: {}", gitSituation.getTags());
            logger.debug("  head description: {}", gitSituation.getDescription());
        }

        // determine git version details
        gitVersionDetails = getGitVersionDetails(gitSituation, config);
        if (gitVersionDetails == null) {
            logger.warn("skip - no matching <ref> configuration and no <rev> configuration defined");
            logger.warn("git refs:");
            logger.warn("  branch: {}", gitSituation.getBranch());
            logger.warn("  tags: {}", gitSituation.getTags());
            logger.warn("defined ref configurations:");
            config.refs.list.forEach(ref -> logger.warn("  {} - pattern: {}", rightPad(ref.type.name(), 6), ref.pattern));
            disabled = true;
            return;
        }

        logger.info("matching ref: {} - {}", gitVersionDetails.getRefType().name(), gitVersionDetails.getRefName());
        final RefPatchDescription patchDescription = gitVersionDetails.getPatchDescription();
        logger.info("ref configuration: {} - pattern: {}", gitVersionDetails.getRefType().name(), patchDescription.pattern);
        if (patchDescription.describeTagPattern != null && !patchDescription.describeTagPattern.equals(".*")) {
            logger.info("  describeTagPattern: {}", patchDescription.describeTagPattern);
            gitSituation.setDescribeTagPattern(patchDescription.describeTagPattern());
        }
        if (patchDescription.describeTagFirstParent != null) {
            logger.info("  describeTagFirstParent: {}", patchDescription.describeTagFirstParent);
            gitSituation.setFirstParent(patchDescription.describeTagFirstParent);
        }
        if (patchDescription.version != null) {
            logger.info("  version: {}", patchDescription.version);
        }
        if (!patchDescription.properties.isEmpty()) {
            logger.info("  properties: ");
            patchDescription.properties.forEach((key, value) -> logger.info("    {} - {}", key, value));
        }

        globalFormatPlaceholderMap = generateGlobalFormatPlaceholderMap(gitSituation, gitVersionDetails, mavenSession);

        if (!patchDescription.userProperties.isEmpty()) {
            logger.info("  userProperties: ");
            String projectVersion = GAV.of(projectModel).getVersion();
            patchDescription.userProperties.forEach((key, value) -> {
                logger.info("    {} - {}", key, value);
                mavenSession.getUserProperties().put(key, getGitPropertyValue(value, "", projectVersion));
            });
        }
        updatePom = getUpdatePomOption(patchDescription);
        if (updatePom) {
            logger.info("  updatePom: {}", updatePom);
        }

        // determine related projects
        relatedProjects = determineRelatedProjects(projectModel);
        if (logger.isDebugEnabled()) {
            logger.debug(buffer().strong("related projects:").toString());
            relatedProjects.forEach(gav -> logger.debug("  {}", gav));
        }

        logger.info("");
    }

    // ---- model processing -------------------------------------------------------------------------------------------

    public Model processModel(Model projectModel, Map<String, ?> options) throws IOException {
        // set model pom file
        final Source pomSource = (Source) options.get(ModelProcessor.SOURCE);
        if (pomSource != null) {
            projectModel.setPomFile(new File(pomSource.getLocation()));
        } else {
            logger.debug("skip model - no project model pom file");
            return projectModel;
        }

        if (!initialized) {
            init(projectModel);
            initialized = true;
        }

        if (disabled) {
            return projectModel;
        }

        GAV projectGAV = GAV.of(projectModel);
        if (projectGAV.getVersion() == null) {
            logger.debug("skip model - can not determine project version - {}", projectModel.getPomFile());
            return projectModel;
        }

        if (!isRelatedProject(projectGAV)) {
            if (logger.isTraceEnabled()) {
                logger.trace("skip model - unrelated project - {}", projectModel.getPomFile());
            }
            return projectModel;
        }

        File canonicalProjectPomFile = projectModel.getPomFile().getCanonicalFile();

        // return cached calculated project model if present
        Model cachedProjectModel = sessionModelCache.get(canonicalProjectPomFile);
        if (cachedProjectModel != null) {
            return cachedProjectModel;
        }

        // add current project model to session project models
        sessionModelCache.put(canonicalProjectPomFile, projectModel);

        if (logger.isInfoEnabled()) {
            // log project header
            logger.info(projectLogHeader(projectGAV));
        }

        updateModel(projectModel, gitVersionDetails.getPatchDescription());

        File gitVersionedPomFile = writePomFile(projectModel);
        if (updatePom) {
            logger.debug("updating original POM file");
            Files.copy(
                    gitVersionedPomFile.toPath(),
                    // In case another ModelProcessor is used (like for example polyglot extension),
                    // we need to work on the POM possibly translated into XML
                    locatePom(projectModel.getProjectDirectory()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // git versioned pom can't be set as model pom right away,
        // because it will break plugins, that trying to update original pom file
        //   e.g. mvn versions:set -DnewVersion=1.0.0
        // That's why we need to add a build plugin that sets project pom file to git versioned pom file
        addBuildPlugin(projectModel);

        logger.info("");
        return projectModel;
    }

    private void updateModel(Model projectModel, RefPatchDescription patchDescription) {
        final GAV originalProjectGAV = GAV.of(projectModel);

        final String versionFormat = patchDescription.version;
        if (versionFormat != null) {
            updateParentVersion(projectModel, versionFormat);
            updateVersion(projectModel, versionFormat);
            updateDependencyVersions(projectModel, versionFormat);
            updatePluginVersions(projectModel, versionFormat);
        }

        final Map<String, String> propertyFormats = patchDescription.properties;
        if (propertyFormats != null) {
            updatePropertyValues(projectModel, propertyFormats, originalProjectGAV);
        }

        addProjectProperties(projectModel);

        // profile section
        updateProfiles(projectModel, patchDescription, originalProjectGAV);
    }


    private void updateProfiles(Model model, RefPatchDescription patchDescription, GAV originalProjectGAV) {
        List<Profile> profiles = model.getProfiles();

        // profile section
        if (!profiles.isEmpty()) {
            for (Profile profile : profiles) {
                String version = patchDescription.version;
                if (version != null) {
                    updateDependencyVersions(profile, version);
                    updatePluginVersions(profile, version);
                }

                Map<String, String> propertyFormats = patchDescription.properties;
                if (propertyFormats != null && !propertyFormats.isEmpty()) {
                    updatePropertyValues(profile, propertyFormats, originalProjectGAV);
                }
            }
        }
    }

    private void updateParentVersion(Model projectModel, String versionFormat) {
        Parent parent = projectModel.getParent();
        if (parent != null) {
            GAV parentGAV = GAV.of(parent);
            if (isRelatedProject(parentGAV)) {
                String gitVersion = getGitVersion(versionFormat, parentGAV.getVersion());
                logger.debug("set parent version to {} ({})", gitVersion, parentGAV);
                parent.setVersion(gitVersion);
            }
        }
    }

    private void updateVersion(Model projectModel, String versionFormat) {
        if (projectModel.getVersion() != null) {
            GAV projectGAV = GAV.of(projectModel);
            String gitVersion = getGitVersion(versionFormat, projectGAV.getVersion());
            logger.info("set version to {}", gitVersion);
            projectModel.setVersion(gitVersion);
        }
    }

    private void updatePropertyValues(ModelBase model, Map<String, String> propertyFormats, GAV originalProjectGAV) {
        if (propertyFormats.isEmpty()) {
            return;
        }
        // properties section
        model.getProperties().forEach((modelPropertyName, modelPropertyValue) -> {
            String propertyFormat = propertyFormats.get((String) modelPropertyName);
            if (propertyFormat != null) {
                String gitPropertyValue = getGitPropertyValue(propertyFormat, (String) modelPropertyValue, originalProjectGAV.getVersion());
                if (!gitPropertyValue.equals(modelPropertyValue)) {
                    logger.info("set property {} to {}", modelPropertyName, gitPropertyValue);
                    model.addProperty((String) modelPropertyName, gitPropertyValue);
                }
            }
        });
    }

    private void updatePluginVersions(ModelBase model, String versionFormat) {
        BuildBase build = getBuild(model);
        if (build == null) {
            return;
        }
        // plugins section
        {
            List<Plugin> relatedPlugins = filterRelatedPlugins(build.getPlugins());
            if (!relatedPlugins.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sectionLogHeader("plugins", model));
                }
                for (Plugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
            
            updatePluginDependencyVersions(model, versionFormat, build.getPlugins());
        }

        // plugin management section
        PluginManagement pluginManagement = build.getPluginManagement();
        if (pluginManagement != null) {
            List<Plugin> relatedPlugins = filterRelatedPlugins(pluginManagement.getPlugins());
            if (!relatedPlugins.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sectionLogHeader("plugin management", model));
                }
                for (Plugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
            
            updatePluginDependencyVersions(model, versionFormat, pluginManagement.getPlugins());
        }

        // reporting section
        Reporting reporting = model.getReporting();
        if (reporting != null) {
            List<ReportPlugin> relatedPlugins = filterRelatedReportPlugins(reporting.getPlugins());
            if (!relatedPlugins.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sectionLogHeader("reporting plugins", model));
                }
                for (ReportPlugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
        }
    }


    private void updatePluginDependencyVersions(ModelBase model, String versionFormat, List<Plugin> plugins) {
        List<Dependency> pluginDepsList = filterRelatedDependencies(plugins.stream().map(Plugin::getDependencies).flatMap(List::stream).collect(toList()));

        if (!pluginDepsList.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug(sectionLogHeader("plugins dependencies", model));
            }
            for (Dependency dep : pluginDepsList) {
                updateVersion(dep, versionFormat);
            }
        }
    }

    private void updateVersion(Plugin plugin, String versionFormat) {
        if (plugin.getVersion() != null) {
            GAV pluginGAV = GAV.of(plugin);
            String gitVersion = getGitVersion(versionFormat, pluginGAV.getVersion());
            if (logger.isDebugEnabled()) {
                logger.debug("{}: set version to {}", pluginGAV.getProjectId(), gitVersion);
            }
            plugin.setVersion(gitVersion);
        }
    }

    private void updateVersion(ReportPlugin plugin, String versionFormat) {
        if (plugin.getVersion() != null) {
            GAV pluginGAV = GAV.of(plugin);
            String gitVersion = getGitVersion(versionFormat, pluginGAV.getVersion());
            if (logger.isDebugEnabled()) {
                logger.debug("{}: set version to {}", pluginGAV.getProjectId(), gitVersion);
            }
            plugin.setVersion(gitVersion);
        }
    }

    private List<Plugin> filterRelatedPlugins(List<Plugin> plugins) {
        return plugins.stream()
                .filter(it -> isRelatedProject(GAV.of(it)))
                .collect(toList());
    }

    private List<ReportPlugin> filterRelatedReportPlugins(List<ReportPlugin> plugins) {
        return plugins.stream()
                .filter(it -> isRelatedProject(GAV.of(it)))
                .collect(toList());
    }

    private void updateDependencyVersions(ModelBase model, String versionFormat) {
        // dependencies section
        {
            List<Dependency> relatedDependencies = filterRelatedDependencies(model.getDependencies());
            if (!relatedDependencies.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sectionLogHeader("dependencies", model));
                }
                for (Dependency dependency : relatedDependencies) {
                    updateVersion(dependency, versionFormat);
                }
            }
        }
        // dependency management section
        DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement != null) {
            List<Dependency> relatedDependencies = filterRelatedDependencies(dependencyManagement.getDependencies());
            if (!relatedDependencies.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sectionLogHeader("dependency management", model));
                }
                for (Dependency dependency : relatedDependencies) {
                    updateVersion(dependency, versionFormat);
                }
            }
        }
    }

    private void updateVersion(Dependency dependency, String versionFormat) {
        if (dependency.getVersion() != null) {
            GAV dependencyGAV = GAV.of(dependency);
            String gitVersion = getGitVersion(versionFormat, dependencyGAV.getVersion());
            if (logger.isDebugEnabled()) {
                logger.debug("{}: set version to {}", dependencyGAV.getProjectId(), gitVersion);
            }
            dependency.setVersion(gitVersion);
        }
    }

    public List<Dependency> filterRelatedDependencies(List<Dependency> dependencies) {
        return dependencies.stream()
                .filter(it -> isRelatedProject(GAV.of(it)))
                .collect(toList());
    }

    private void addProjectProperties(Model projectModel) {
        Properties projectProperties = projectModel.getProperties();

        if (!projectProperties.contains("git.worktree"))
            projectModel.addProperty("git.worktree", gitSituation.getRootDirectory().getAbsolutePath());

        if (!projectProperties.contains("git.commit"))
            projectModel.addProperty("git.commit", gitVersionDetails.getCommit());
        if (!projectProperties.contains("git.commit.short"))
            projectModel.addProperty("git.commit.short", gitVersionDetails.getCommit().substring(0, 7));

        final ZonedDateTime headCommitDateTime = gitSituation.getTimestamp();
        if (!projectProperties.contains("git.commit.timestamp"))
            projectModel.addProperty("git.commit.timestamp", String.valueOf(headCommitDateTime.toEpochSecond()));
        if (!projectProperties.contains("git.commit.timestamp.datetime"))
            projectModel.addProperty("git.commit.timestamp.datetime", headCommitDateTime.toEpochSecond() > 0
                    ? headCommitDateTime.format(ISO_INSTANT) : "0000-00-00T00:00:00Z");

        final String refName = gitVersionDetails.getRefName();
        if (!projectProperties.contains("git.ref"))
            projectModel.addProperty("git.ref", refName);
        if (!projectProperties.contains("git.ref.slug"))
            projectModel.addProperty("git.ref.slug", slugify(refName));
    }

    private void addBuildPlugin(Model projectModel) {

        logger.debug("add version build plugin");

        Plugin gitVersioningPlugin = asPlugin();
        {
            PluginExecution execution = new PluginExecution();
            execution.setId(GitVersioningMojo.GOAL);
            execution.getGoals().add(GitVersioningMojo.GOAL);
            gitVersioningPlugin.getExecutions().add(execution);
        }

        if (projectModel.getBuild() == null) {
            projectModel.setBuild(new Build());
        }

        // add at index 0 to be executed before any other project plugin,
        // to prevent malfunctions with other plugins
        projectModel.getBuild().getPlugins().add(0, gitVersioningPlugin);

        // exclude this plugin from maven enforce plugin
        {
            LinkedList<Plugin> plugins = new LinkedList<>(projectModel.getBuild().getPlugins());
            if (projectModel.getBuild().getPluginManagement() != null) {
                plugins.addAll(projectModel.getBuild().getPluginManagement().getPlugins());
            }
            plugins.stream()
                    .filter(it -> it.getKey().equals("org.apache.maven.plugins:maven-enforcer-plugin"))
                    .forEach(plugin -> plugin.getExecutions().forEach(execution -> {
                        Xpp3Dom configuration = (Xpp3Dom) execution.getConfiguration();
                        if (configuration != null) {
                            Xpp3Dom rules = configuration.getChild("rules");
                            if (rules != null) {
                                Xpp3Dom requirePluginVersions = rules.getChild("requirePluginVersions");
                                if (requirePluginVersions != null) {
                                    Xpp3Dom unCheckedPluginList = requirePluginVersions.getChild("unCheckedPluginList");
                                    if (unCheckedPluginList == null) {
                                        unCheckedPluginList = new Xpp3Dom("unCheckedPluginList");
                                        requirePluginVersions.addChild(unCheckedPluginList);
                                    }
                                    // prepend git versioning plugin to unCheckedPluginList
                                    unCheckedPluginList.setValue(gitVersioningPlugin.getKey() + ","
                                            + Objects.requireNonNullElse(unCheckedPluginList.getValue(), "")
                                    );
                                }
                            }
                        }
                    }));
        }
    }


    // ---- versioning -------------------------------------------------------------------------------------------------

    private GitSituation getGitSituation(File pomFile) throws IOException {
        final File baseDirectory = pomFile.getParentFile();
        final FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(baseDirectory);
        if (repositoryBuilder.getGitDir() == null) {
            return null;
        }

        final Repository repository = repositoryBuilder.build();
        return new GitSituation(repository) {
            {
                handleEnvironment(repository);
            }

            private void handleEnvironment(Repository repository) throws IOException {
                // --- commandline arguments and environment variables
                {
                    {
                        String overrideBranch = getCommandOption(OPTION_NAME_GIT_BRANCH);
                        String overrideTag = getCommandOption(OPTION_NAME_GIT_TAG);
                        if (overrideBranch != null || overrideTag != null) {
                            overrideBranch = overrideBranch == null || overrideBranch.trim().isEmpty() ? null : overrideBranch.trim();
                            setBranch(overrideBranch);

                            overrideTag = overrideTag == null || overrideTag.trim().isEmpty() ? null : overrideTag.trim();
                            setTags(overrideTag == null ? emptyList() : singletonList(overrideTag));
                            return;
                        }
                    }

                    {
                        final String providedRef = getCommandOption(OPTION_NAME_GIT_REF);
                        if (providedRef != null) {
                            if (!providedRef.startsWith("refs/")) {
                                throw new IllegalArgumentException("invalid provided ref " + providedRef + " -  needs to start with refs/");
                            }

                            if (providedRef.startsWith("refs/tags/")) {
                                setBranch(null);
                                setTags(singletonList(providedRef));
                            } else {
                                setBranch(providedRef);
                                setTags(emptyList());
                            }
                            return;
                        }
                    }
                }

                // --- try getting branch and tag situation from environment ---
                // skip if we are on a branch
                if (repository.getBranch() == null) {
                    return;
                }

                // GitHub Actions support
                if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
                    if (!this.getRev().equals(System.getenv("GITHUB_SHA"))) {
                        return;
                    }

                    logger.info("gather git situation from GitHub Actions environment variable: GITHUB_REF");
                    String githubRef = System.getenv("GITHUB_REF");
                    logger.debug("  GITHUB_REF: {}", githubRef);

                    if (githubRef.startsWith("refs/tags/")) {
                        addTag(githubRef);
                    } else {
                        setBranch(githubRef);
                    }
                    return;
                }

                // GitLab CI support
                if ("true".equalsIgnoreCase(System.getenv("GITLAB_CI"))) {
                    if (!this.getRev().equals(System.getenv("CI_COMMIT_SHA"))) {
                        return;
                    }

                    logger.info("gather git situation from GitLab CI environment variables: CI_COMMIT_BRANCH, CI_MERGE_REQUEST_SOURCE_BRANCH_NAME and CI_COMMIT_TAG");
                    String commitBranch = System.getenv("CI_COMMIT_BRANCH");
                    String commitTag = System.getenv("CI_COMMIT_TAG");
                    String mrSourceBranch = System.getenv("CI_MERGE_REQUEST_SOURCE_BRANCH_NAME");
                    logger.debug("  CI_COMMIT_BRANCH: {}", commitBranch);
                    logger.debug("  CI_COMMIT_TAG: {}", commitTag);
                    logger.debug("  CI_MERGE_REQUEST_SOURCE_BRANCH_NAME: {}", mrSourceBranch);

                    if (commitBranch != null) {
                        setBranch(commitBranch);
                    } else if (mrSourceBranch != null) {
                        setBranch(mrSourceBranch);
                    } else if (commitTag != null) {
                        addTag(commitTag);
                    }
                    return;
                }

                // Circle CI support
                if ("true".equalsIgnoreCase(System.getenv("CIRCLECI"))) {
                    if (!this.getRev().equals(System.getenv("CIRCLE_SHA1"))) {
                        return;
                    }

                    logger.info("gather git situation from Circle CI environment variables: CIRCLE_BRANCH and CIRCLE_TAG");
                    String commitBranch = System.getenv("CIRCLE_BRANCH");
                    String commitTag = System.getenv("CIRCLE_TAG");
                    logger.debug("  CIRCLE_BRANCH: {}", commitBranch);
                    logger.debug("  CIRCLE_TAG: {}", commitTag);

                    if (commitBranch != null) {
                        setBranch(commitBranch);
                    } else if (commitTag != null) {
                        addTag(commitTag);
                    }
                    return;
                }

                // Jenkins support
                if (System.getenv("JENKINS_HOME") != null && !System.getenv("JENKINS_HOME").trim().isEmpty()) {
                    if (!this.getRev().equals(System.getenv("GIT_COMMIT"))) {
                        return;
                    }
                    logger.info("gather git situation from jenkins environment variables: BRANCH_NAME and TAG_NAME");
                    String commitBranch = System.getenv("BRANCH_NAME");
                    String commitTag = System.getenv("TAG_NAME");
                    logger.debug("  BRANCH_NAME: {}", commitBranch);
                    logger.debug("  TAG_NAME: {}", commitTag);

                    if (commitBranch != null) {
                        if (commitBranch.equals(commitTag)) {
                            addTag(commitBranch);
                        } else {
                            setBranch(commitBranch);
                        }
                    } else if (commitTag != null) {
                        addTag(commitTag);
                    }
                    return;
                }
            }


            protected void setBranch(String branch) {
                logger.debug("override git branch with {}", branch);
                super.setBranch(branch);
            }

            protected void setTags(List<String> tags) {
                logger.debug("override git tags with single tag {}", tags);
                super.setTags(tags);
            }

            protected void addTag(String tag) {
                logger.debug("add git tag {}", tag);
                super.addTag(tag);
            }
        };
    }

    private static GitVersionDetails getGitVersionDetails(GitSituation gitSituation, Configuration config) {
        final Lazy<List<String>> sortedTags = Lazy.by(gitSituation::getTags);
        for (RefPatchDescription refConfig : config.refs.list) {
            switch (refConfig.type) {
                case TAG: {
                    if (gitSituation.isDetached() || config.refs.considerTagsOnBranches) {
                        for (String tag : sortedTags.get()) {
                            if (refConfig.pattern == null || refConfig.pattern().matcher(tag).matches()) {
                                return new GitVersionDetails(gitSituation.getRev(), TAG, tag, refConfig);
                            }
                        }
                    }
                }
                break;
                case BRANCH: {
                    if (!gitSituation.isDetached()) {
                        String branch = gitSituation.getBranch();
                        if (refConfig.pattern == null || refConfig.pattern().matcher(branch).matches()) {
                            return new GitVersionDetails(gitSituation.getRev(), BRANCH, branch, refConfig);
                        }
                    }
                }
                break;
                default:
                    throw new IllegalArgumentException("Unexpected ref type: " + refConfig.type);
            }
        }

        if (config.rev != null) {
            return new GitVersionDetails(gitSituation.getRev(), COMMIT, gitSituation.getRev(),
                    new RefPatchDescription(COMMIT, null, config.rev));
        }


        return null;
    }

    private String getGitVersion(String versionFormat, String projectVersion) {
        final Map<String, Supplier<String>> placeholderMap = generateFormatPlaceholderMap(projectVersion);

        return slugify(substituteText(versionFormat, placeholderMap));
    }

    private String getGitPropertyValue(String propertyFormat, String originalValue, String projectVersion) {
        final Map<String, Supplier<String>> placeholderMap = generateFormatPlaceholderMap(projectVersion);
        placeholderMap.put("value", () -> originalValue);
        return substituteText(propertyFormat, placeholderMap);
    }

    private Map<String, Supplier<String>> generateFormatPlaceholderMap(String projectVersion) {
        final Map<String, Supplier<String>> placeholderMap = new HashMap<>(globalFormatPlaceholderMap);

        placeholderMap.put("version", Lazy.of(projectVersion));

        final Lazy<Matcher> versionComponents = Lazy.by(() -> matchVersion(projectVersion));

        placeholderMap.put("version.core", Lazy.by(() -> requireNonNullElse(versionComponents.get().group("core"), "0.0.0")));

        placeholderMap.put("version.major", Lazy.by(() -> requireNonNullElse(versionComponents.get().group("major"), "0")));
        placeholderMap.put("version.major.next", Lazy.by(() -> increase(placeholderMap.get("version.major").get(), 1)));

        placeholderMap.put("version.minor", Lazy.by(() -> requireNonNullElse(versionComponents.get().group("minor"), "0")));
        placeholderMap.put("version.minor.next", Lazy.by(() -> increase(placeholderMap.get("version.minor").get(), 1)));

        placeholderMap.put("version.patch", Lazy.by(() -> requireNonNullElse(versionComponents.get().group("patch"), "0")));
        placeholderMap.put("version.patch.next", Lazy.by(() -> increase(placeholderMap.get("version.patch").get(), 1)));

        placeholderMap.put("version.label", Lazy.by(() -> requireNonNullElse(versionComponents.get().group("label"), "")));
        placeholderMap.put("version.label.prefixed", Lazy.by(() -> {
            String label = placeholderMap.get("version.label").get();
            return !label.isEmpty() ? "-" + label : "";
        }));

        // deprecated
        placeholderMap.put("version.release", Lazy.by(() -> projectVersion.replaceFirst("-.*$", "")));

        final Pattern projectVersionPattern = config.projectVersionPattern();
        if (projectVersionPattern != null) {
            // ref pattern groups
            for (Entry<String, String> patternGroup : patternGroupValues(projectVersionPattern, projectVersion).entrySet()) {
                final String groupName = patternGroup.getKey();
                final String value = patternGroup.getValue() != null ? patternGroup.getValue() : "";

                final var placeholderKey = "version." + groupName;
                // ensure no placeholder overwrites
                if (placeholderMap.containsKey(placeholderKey)) {
                    throw new IllegalArgumentException("project version pattern capture group can not be named '" + groupName + "', because this would overwrite extension placeholder ${" + placeholderKey + "}");
                }
                placeholderMap.put(placeholderKey, () -> value);
            }
        }


        return placeholderMap;
    }

    private Map<String, Supplier<String>> generateGlobalFormatPlaceholderMap(GitSituation gitSituation, GitVersionDetails gitVersionDetails, MavenSession mavenSession) {

        final Map<String, Supplier<String>> placeholderMap = new HashMap<>();

        final Lazy<String> hash = Lazy.by(gitSituation::getRev);
        placeholderMap.put("commit", hash);
        placeholderMap.put("commit.short", Lazy.by(() -> hash.get().substring(0, 7)));

        final Lazy<ZonedDateTime> headCommitDateTime = Lazy.by(gitSituation::getTimestamp);
        placeholderMap.put("commit.timestamp", Lazy.by(() -> String.valueOf(headCommitDateTime.get().toEpochSecond())));
        placeholderMap.put("commit.timestamp.year", Lazy.by(() -> String.valueOf(headCommitDateTime.get().getYear())));
        placeholderMap.put("commit.timestamp.year.2digit", Lazy.by(() -> String.valueOf(headCommitDateTime.get().getYear() % 100)));
        placeholderMap.put("commit.timestamp.month", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getMonthValue()), 2, "0")));
        placeholderMap.put("commit.timestamp.day", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getDayOfMonth()), 2, "0")));
        placeholderMap.put("commit.timestamp.hour", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getHour()), 2, "0")));
        placeholderMap.put("commit.timestamp.minute", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getMinute()), 2, "0")));
        placeholderMap.put("commit.timestamp.second", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getSecond()), 2, "0")));
        placeholderMap.put("commit.timestamp.datetime", Lazy.by(() -> headCommitDateTime.get().toEpochSecond() > 0
                ? headCommitDateTime.get().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")) : "00000101.000000"));
        placeholderMap.put("commit.timestamp.iso", Lazy.by(() -> headCommitDateTime.get().toEpochSecond() > 0
                ? headCommitDateTime.get().format(DateTimeFormatter.ISO_INSTANT) : "0000-01-01T00:00:00Z"));

        final Lazy<ZonedDateTime> buildCommitDateTime = Lazy.by(() -> mavenSession.getStartTime().toInstant().atZone(ZoneId.systemDefault()));
        placeholderMap.put("build.timestamp", Lazy.by(() -> String.valueOf(buildCommitDateTime.get().toEpochSecond())));
        placeholderMap.put("build.timestamp.year", Lazy.by(() -> String.valueOf(buildCommitDateTime.get().getYear())));
        placeholderMap.put("build.timestamp.year.2digit", Lazy.by(() -> String.valueOf(buildCommitDateTime.get().getYear() % 100)));
        placeholderMap.put("build.timestamp.month", Lazy.by(() -> leftPad(String.valueOf(buildCommitDateTime.get().getMonthValue()), 2, "0")));
        placeholderMap.put("build.timestamp.day", Lazy.by(() -> leftPad(String.valueOf(buildCommitDateTime.get().getDayOfMonth()), 2, "0")));
        placeholderMap.put("build.timestamp.hour", Lazy.by(() -> leftPad(String.valueOf(buildCommitDateTime.get().getHour()), 2, "0")));
        placeholderMap.put("build.timestamp.minute", Lazy.by(() -> leftPad(String.valueOf(buildCommitDateTime.get().getMinute()), 2, "0")));
        placeholderMap.put("build.timestamp.second", Lazy.by(() -> leftPad(String.valueOf(buildCommitDateTime.get().getSecond()), 2, "0")));
        placeholderMap.put("build.timestamp.datetime", Lazy.by(() -> buildCommitDateTime.get().toEpochSecond() > 0
                ? buildCommitDateTime.get().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")) : "00000000.000000"));
        placeholderMap.put("build.timestamp.iso", Lazy.by(() -> buildCommitDateTime.get().toEpochSecond() > 0
                ? headCommitDateTime.get().format(DateTimeFormatter.ISO_INSTANT) : "0000-01-01T00:00:00Z"));

        final String refName = gitVersionDetails.getRefName();
        final Lazy<String> refNameSlug = Lazy.by(() -> slugify(refName));
        placeholderMap.put("ref", () -> refName);
        placeholderMap.put("ref" + ".slug", refNameSlug);

        final Pattern refPattern = gitVersionDetails.getPatchDescription().pattern();
        if (refPattern != null) {
            // ref pattern groups
            for (Entry<String, String> patternGroup : patternGroupValues(refPattern, refName).entrySet()) {
                final String groupName = patternGroup.getKey();
                final String value = patternGroup.getValue() != null ? patternGroup.getValue() : "";

                final var placeholderKey = "ref." + groupName;
                // ensure no placeholder overwrites
                if (placeholderMap.containsKey(placeholderKey)) {
                    throw new IllegalArgumentException("ref pattern capture group can not be named '" + groupName + "', because this would overwrite extension placeholder ${" + placeholderKey + "}");
                }
                placeholderMap.put(placeholderKey, () -> value);
                placeholderMap.put(placeholderKey + ".slug", Lazy.by(() -> slugify(value)));
            }
        }

        // dirty
        final Lazy<Boolean> dirty = Lazy.by(() -> !gitSituation.isClean());
        placeholderMap.put("dirty", Lazy.by(() -> dirty.get() ? "-DIRTY" : ""));
        placeholderMap.put("dirty.snapshot", Lazy.by(() -> dirty.get() ? "-SNAPSHOT" : ""));

        // describe
        final Lazy<GitDescription> description = Lazy.by(gitSituation::getDescription);
        placeholderMap.put("describe", Lazy.by(() -> description.get().toString()));
        final Lazy<String> descriptionTag = Lazy.by(() -> description.get().getTag());
        placeholderMap.put("describe.tag", descriptionTag);

        final Lazy<Matcher> descriptionTagVersionMatcher = Lazy.by(() -> matchVersion(descriptionTag.get()));

        placeholderMap.put("describe.tag.version", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("version"), "0.0.0")));

        placeholderMap.put("describe.tag.version.core", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("core"), "0")));

        placeholderMap.put("describe.tag.version.major", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("major"), "0")));
        placeholderMap.put("describe.tag.version.major.next", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.major").get(), 1)));

        placeholderMap.put("describe.tag.version.minor", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("minor"), "0")));
        placeholderMap.put("describe.tag.version.minor.next", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.minor").get(), 1)));

        placeholderMap.put("describe.tag.version.patch", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("patch"), "0")));
        placeholderMap.put("describe.tag.version.patch.next", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.patch").get(), 1)));

        placeholderMap.put("describe.tag.version.label", Lazy.by(() -> requireNonNullElse(descriptionTagVersionMatcher.get().group("label"), "")));
        placeholderMap.put("describe.tag.version.label.next", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.label").get(), 1)));

        final Lazy<Integer> descriptionDistance = Lazy.by(() -> description.get().getDistance());
        placeholderMap.put("describe.distance", Lazy.by(() -> String.valueOf(descriptionDistance.get())));
        placeholderMap.put("describe.distance.snapshot", Lazy.by(() -> (descriptionDistance.get() == 0 ? "" : "-SNAPSHOT")));

        placeholderMap.put("describe.tag.version.patch.plus.describe.distance", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.patch").get(), descriptionDistance.get())));
        placeholderMap.put("describe.tag.version.patch.next.plus.describe.distance", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.patch.next").get(), descriptionDistance.get())));

        placeholderMap.put("describe.tag.version.label.plus.describe.distance", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.label").get(), descriptionDistance.get())));
        placeholderMap.put("describe.tag.version.label.next.plus.describe.distance", Lazy.by(() -> increase(placeholderMap.get("describe.tag.version.label.next").get(), descriptionDistance.get())));

        // describe tag pattern groups
        final Lazy<Map<String, String>> describeTagPatternValues = Lazy.by(
                () -> patternGroupValues(gitSituation.getDescribeTagPattern(), descriptionTag.get()));
        for (String groupName : patternGroups(gitSituation.getDescribeTagPattern())) {
            final var placeholderKey = "describe.tag." + groupName;
            // ensure no placeholder overwrites
            if (placeholderMap.containsKey(placeholderKey)) {
                throw new IllegalArgumentException("describe tag pattern capture group can not be named '" + groupName + "', because this would overwrite extension placeholder ${" + placeholderKey + "}");
            }
            Lazy<String> groupValue = Lazy.by(() -> describeTagPatternValues.get().get(groupName));
            placeholderMap.put(placeholderKey, groupValue);
            placeholderMap.put(placeholderKey + ".slug", Lazy.by(() -> slugify(groupValue.get())));
        }

        // command parameters e.g. mvn -Dfoo=123 will be available as ${property.foo}
        for (Entry<Object, Object> property : mavenSession.getUserProperties().entrySet()) {
            if (property.getValue() != null) {
                placeholderMap.put("property." + property.getKey(), () -> property.getValue().toString());
            }
        }

        // environment variables e.g. BUILD_NUMBER=123 will be available as ${env.BUILD_NUMBER}
        System.getenv().forEach((key, value) -> placeholderMap.put("env." + key, () -> value));

        return placeholderMap;
    }

    private Matcher matchVersion(String input) {
        Matcher matcher = VERSION_PATTERN.matcher(input);
        //noinspection ResultOfMethodCallIgnored
        matcher.find();

        return matcher;
    }

    // ---- configuration -------------------------------------------------------------------------------------------------

    private static File findMvnDirectory(File pomFile) throws IOException {
        File searchDirectory = pomFile.getParentFile();
        while (searchDirectory != null) {
            File mvnDir = new File(searchDirectory, ".mvn");
            if (mvnDir.exists()) {
                return mvnDir;
            }
            searchDirectory = searchDirectory.getParentFile();
        }

        throw new FileNotFoundException("Can not find .mvn directory in hierarchy of " + pomFile);
    }

    private static Configuration readConfig(File configFile) throws IOException {
        final XmlMapper xmlMapper = XmlMapper.builder().enable(ACCEPT_CASE_INSENSITIVE_ENUMS).build();

        final Configuration config = xmlMapper.readValue(configFile, Configuration.class);

        // consider global config
        List<PatchDescription> patchDescriptions = new ArrayList<>(config.refs.list);
        if (config.rev != null) {
            patchDescriptions.add(config.rev);
        }
        for (PatchDescription patchDescription : patchDescriptions) {
            if (patchDescription.describeTagPattern == null) {
                patchDescription.describeTagPattern = config.describeTagPattern;
            }
            if (patchDescription.describeTagFirstParent == null) {
                patchDescription.describeTagFirstParent = config.describeTagFirstParent;
            }
            if (patchDescription.updatePom == null) {
                patchDescription.updatePom = config.updatePom;
            }
        }

        return config;
    }

    private String getCommandOption(final String name) {
        String value = mavenSession.getUserProperties().getProperty(name);
        if (value == null) {
            String plainName = name.replaceFirst("^versioning\\.", "");
            String environmentVariableName = "VERSIONING_"
                    + String.join("_", plainName.split("(?=\\p{Lu})"))
                    .replaceAll("\\.", "_")
                    .toUpperCase();
            value = System.getenv(environmentVariableName);
        }
        if (value == null) {
            value = System.getProperty(name);
        }
        return value;
    }

    private boolean getUpdatePomOption(final PatchDescription gitRefConfig) {
        final String updatePomCommandOption = getCommandOption(OPTION_UPDATE_POM);
        if (updatePomCommandOption != null) {
            return parseBoolean(updatePomCommandOption);
        }

        //noinspection ReplaceNullCheck
        if (gitRefConfig.updatePom != null) {
            return gitRefConfig.updatePom;
        }

        return false;
    }

    // ---- determine related projects ---------------------------------------------------------------------------------

    private Set<GAV> determineRelatedProjects(Model projectModel) throws IOException {
        final HashSet<GAV> relatedProjects = new HashSet<>();
        determineRelatedProjects(projectModel, relatedProjects);
        config.relatedProjects.stream()
                .map(it -> new GAV(it.groupId, it.artifactId, "*"))
                .forEach(relatedProjects::add);
        return relatedProjects;
    }

    private void determineRelatedProjects(Model projectModel, Set<GAV> relatedProjects) throws IOException {
        final GAV projectGAV = GAV.of(projectModel);
        if (relatedProjects.contains(projectGAV)) {
            return;
        }

        // add self
        relatedProjects.add(projectGAV);

        // check for related parent project by parent tag
        if (projectModel.getParent() != null) {
            final GAV parentGAV = GAV.of(projectModel.getParent());
            final File parentProjectPomFile = getParentProjectPomFile(projectModel);
            if (isRelatedPom(parentProjectPomFile)) {
                final Model parentProjectModel = readModel(parentProjectPomFile);
                final GAV parentProjectGAV = GAV.of(parentProjectModel);
                if (parentProjectGAV.equals(parentGAV)) {
                    determineRelatedProjects(parentProjectModel, relatedProjects);
                }
            }
        }

        // check for related parent project within parent directory
        final Model parentProjectModel = searchParentProjectInParentDirectory(projectModel);
        if (parentProjectModel != null) {
            determineRelatedProjects(parentProjectModel, relatedProjects);
        }

        //  process modules
        for (File modulePomFile : getProjectModules(projectModel)) {
            Model moduleProjectModel = readModel(modulePomFile);
            determineRelatedProjects(moduleProjectModel, relatedProjects);
        }
    }

    private boolean isRelatedProject(GAV project) {
        return relatedProjects.contains(project)
                || relatedProjects.contains(new GAV(project.getGroupId(), project.getArtifactId(), "*"));
    }


    /**
     * checks if <code>pomFile</code> is part of current maven and git context
     *
     * @param pomFile the pom file
     * @return true if <code>pomFile</code> is part of current maven and git context
     */
    private boolean isRelatedPom(File pomFile) throws IOException {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repositories ends in .pom
                && pomFile.getName().endsWith(".xml")
                && pomFile.getCanonicalPath().startsWith(mvnDirectory.getParentFile().getCanonicalPath() + File.separator)
                // only pom files within git directory are treated as project pom files
                && pomFile.getCanonicalPath().startsWith(gitSituation.getRootDirectory().getCanonicalPath() + File.separator);
    }

    private Model searchParentProjectInParentDirectory(Model projectModel) throws IOException {
        // search for parent project by directory hierarchy
        File parentDirectoryPomFile = pomFile(projectModel.getProjectDirectory().getParentFile(), "pom.xml");
        if (parentDirectoryPomFile.exists() && isRelatedPom(parentDirectoryPomFile)) {
            // check if parent has module that points to current project directory
            Model parentDirectoryProjectModel = readModel(parentDirectoryPomFile);
            for (File modulePomFile : getProjectModules(parentDirectoryProjectModel)) {
                if (modulePomFile.getCanonicalFile().equals(projectModel.getPomFile().getCanonicalFile())) {
                    return parentDirectoryProjectModel;
                }
            }
        }
        return null;
    }

    private static File getParentProjectPomFile(Model projectModel) {
        if (projectModel.getParent() == null) {
            return null;
        }

        File parentProjectPomFile = pomFile(projectModel.getProjectDirectory(), projectModel.getParent().getRelativePath());
        if (parentProjectPomFile.exists()) {
            return parentProjectPomFile;
        }

        return null;
    }

    private static Set<File> getProjectModules(Model projectModel) {
        final Set<File> modules = new HashSet<>();

        // modules section
        for (String module : projectModel.getModules()) {
            modules.add(pomFile(projectModel.getProjectDirectory(), module));
        }

        // profiles section
        for (Profile profile : projectModel.getProfiles()) {

            // modules section
            for (String module : profile.getModules()) {
                modules.add(pomFile(projectModel.getProjectDirectory(), module));
            }
        }

        return modules.stream().filter(File::exists).collect(toSet());
    }


    // ---- generate git versioned pom file ----------------------------------------------------------------------------

    private File writePomFile(Model projectModel) throws IOException {
        File gitVersionedPomFile = new File(projectModel.getProjectDirectory(), GIT_VERSIONING_POM_NAME);
        logger.debug("generate {}", gitVersionedPomFile);

        // In case another ModelProcessor is used (like for example polyglot extension),
        // we need to work on the POM possibly translated into XML
        Document gitVersionedPomDocument = readXml(this.locatePom(projectModel.getProjectDirectory()));
        Element projectElement = gitVersionedPomDocument.getChild("project");

        // update project
        updateParentVersion(projectElement, projectModel.getParent());
        updateVersion(projectElement, projectModel);
        updatePropertyValues(projectElement, projectModel);
        updateDependencyVersions(projectElement, projectModel);
        updatePluginVersions(projectElement, projectModel.getBuild(), projectModel.getReporting());

        updateProfiles(projectElement, projectModel.getProfiles());

        writeXml(gitVersionedPomFile, gitVersionedPomDocument);

        return gitVersionedPomFile;
    }

    private static void updateParentVersion(Element projectElement, Parent parent) {
        Element parentElement = projectElement.getChild("parent");
        if (parentElement != null) {
            Element parentVersionElement = parentElement.getChild("version");
            parentVersionElement.setText(parent.getVersion());
        }
    }

    private static void updateVersion(Element projectElement, Model projectModel) {
        Element versionElement = projectElement.getChild("version");
        if (versionElement != null) {
            versionElement.setText(projectModel.getVersion());
        }
    }

    private void updatePropertyValues(Element element, ModelBase model) {
        // properties section
        Element propertiesElement = element.getChild("properties");
        if (propertiesElement != null) {
            propertiesElement.getChildren().forEach(propertyElement -> {
                String modelPropertyValue = model.getProperties().getProperty(propertyElement.getName());
                if (modelPropertyValue != null) {
                    if (!Objects.equals(propertyElement.getText(), modelPropertyValue)) {
                        propertyElement.setText(modelPropertyValue);
                    }
                }
            });
        }
    }

    private static void updateDependencyVersions(Element element, ModelBase model) {
        // dependencies section
        {
            Element dependenciesElement = element.getChild("dependencies");
            if (dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, model.getDependencies());
            }
        }
        // dependencyManagement section
        Element dependencyManagementElement = element.getChild("dependencyManagement");
        if (dependencyManagementElement != null) {
            Element dependenciesElement = dependencyManagementElement.getChild("dependencies");
            if (dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, model.getDependencyManagement().getDependencies());
            }
        }
    }

    private static void updateDependencyVersions(Element dependenciesElement, List<Dependency> dependencies) {
        forEachPair(dependenciesElement.getChildren(), dependencies, (dependencyElement, dependency) -> {
            // sanity check
            if (!Objects.equals(dependency.getManagementKey(), getDependencyManagementKey(dependencyElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model dependencies order");
            }

            Element dependencyVersionElement = dependencyElement.getChild("version");
            if (dependencyVersionElement != null) {
                dependencyVersionElement.setText(dependency.getVersion());
            }
        });
    }

    private static String getDependencyManagementKey(Element element) {
        Element groupId = element.getChild("groupId");
        Element artifactId = element.getChild("artifactId");
        Element type = element.getChild("type");
        Element classifier = element.getChild("classifier");
        return (groupId != null ? groupId.getText().trim() : "")
                + ":" + (artifactId != null ? artifactId.getText().trim() : "")
                + ":" + (type != null ? type.getText().trim() : "jar")
                + (classifier != null ? ":" + classifier.getText().trim() : "");
    }

    private static void updatePluginVersions(Element projectElement, BuildBase build, Reporting reporting) {
        // build section
        Element buildElement = projectElement.getChild("build");
        if (buildElement != null) {
            // plugins section
            {
                Element pluginsElement = buildElement.getChild("plugins");
                if (pluginsElement != null) {
                    updatePluginVersions(pluginsElement, build.getPlugins());
                }
            }
            // pluginManagement section
            Element pluginsManagementElement = buildElement.getChild("pluginsManagement");
            if (pluginsManagementElement != null) {
                Element pluginsElement = pluginsManagementElement.getChild("plugins");
                if (pluginsElement != null) {
                    updatePluginVersions(pluginsElement, build.getPluginManagement().getPlugins());
                }
            }
        }

        Element reportingElement = projectElement.getChild("reporting");
        if (reportingElement != null) {
            // plugins section
            {
                Element pluginsElement = reportingElement.getChild("plugins");
                if (pluginsElement != null) {
                    updateReportPluginVersions(pluginsElement, reporting.getPlugins());
                }
            }
        }
    }

    private static void updatePluginVersions(Element pluginsElement, List<Plugin> plugins) {
        forEachPair(pluginsElement.getChildren(), plugins, (pluginElement, plugin) -> {
            // sanity check
            if (!Objects.equals(plugin.getKey(), getPluginKey(pluginElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model plugin order");
            }

            Element pluginVersionElement = pluginElement.getChild("version");
            if (pluginVersionElement != null) {
                pluginVersionElement.setText(plugin.getVersion());
            }
            
            Element dependenciesElement = pluginElement.getChild("dependencies");
            List<Dependency> dependencies = plugin.getDependencies();
            if (!dependencies.isEmpty() && dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, dependencies);
            }
        });
    }

    private static void updateReportPluginVersions(Element pluginsElement, List<ReportPlugin> plugins) {
        forEachPair(pluginsElement.getChildren(), plugins, (pluginElement, plugin) -> {
            // sanity check
            if (!Objects.equals(plugin.getKey(), getPluginKey(pluginElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model report plugin order");
            }

            Element pluginVersionElement = pluginElement.getChild("version");
            if (pluginVersionElement != null) {
                pluginVersionElement.setText(plugin.getVersion());
            }
        });
    }

    private static String getPluginKey(Element element) {
        Element groupId = element.getChild("groupId");
        Element artifactId = element.getChild("artifactId");
        return (groupId != null ? groupId.getText().trim() : "org.apache.maven.plugins")
                + ":" + (artifactId != null ? artifactId.getText().trim() : "");
    }

    private void updateProfiles(Element projectElement, List<Profile> profiles) {
        Element profilesElement = projectElement.getChild("profiles");
        if (profilesElement != null) {
            Map<String, Profile> profileMap = profiles.stream()
                    .collect(toMap(Profile::getId, it -> it));
            for (Element profileElement : profilesElement.getChildren("profile")) {
                String profileId = profileElement.getChild("id").getText().trim();
                Profile profile = profileMap.get(profileId);
                updatePropertyValues(profileElement, profile);
                updateDependencyVersions(profileElement, profile);
                updatePluginVersions(profileElement, profile.getBuild(), profile.getReporting());
            }
        }
    }


    // ---- misc -------------------------------------------------------------------------------------------------------

    private static String extensionLogHeader(GAV extensionGAV) {
        String extension = extensionGAV.toString();
        String metaInfo = "[core extension]";

        String plainLog = extension + " " + metaInfo;
        String formattedLog = buffer()
                .a(" ").mojo(extension).a(" ").strong(metaInfo).a(" ")
                .toString();

        return padLogHeaderPadding(plainLog, formattedLog);
    }

    private static String padLogHeaderPadding(String plainLog, String formattedLog) {
        String pad = "-";
        int padding = max(6, 72 - 2 - plainLog.length());
        int paddingLeft = (int) floor(padding / 2.0);
        int paddingRight = (int) ceil(padding / 2.0);
        return buffer()
                .strong(repeat(pad, paddingLeft))
                .a(formattedLog)
                .strong(repeat(pad, paddingRight))
                .toString();
    }

    private static String projectLogHeader(GAV projectGAV) {
        String project = projectGAV.getProjectId();
        return buffer().project(project).toString();
    }

    private static String sectionLogHeader(String title, ModelBase model) {
        String header = title + ":";
        if (model instanceof Profile) {
            header = buffer().strong("profile " + ((Profile) model).getId() + " ") + header;
        }
        return header;
    }

    // ---- utils ------------------------------------------------------------------------------------------------------

    public static <T1, T2> void forEachPair(Collection<T1> collection1, Collection<T2> collection2, BiConsumer<T1, T2> consumer) {
        if (collection1.size() != collection2.size()) {
            throw new IllegalArgumentException("Collections sizes are not equals");
        }

        Iterator<T1> iter1 = collection1.iterator();
        Iterator<T2> iter2 = collection2.iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            consumer.accept(iter1.next(), iter2.next());
        }
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("/", "-");
    }


    private static String increase(String number, long increment) {
        String sanitized = number.isEmpty() ? "0" : number;
        return String.format("%0" + sanitized.length() + "d", Long.parseLong(number.isEmpty() ? "0" : number) + increment);
    }
}
