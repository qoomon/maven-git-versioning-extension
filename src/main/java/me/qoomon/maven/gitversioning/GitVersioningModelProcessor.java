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
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.*;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.*;
import static me.qoomon.gitversioning.commons.GitRefType.COMMIT;
import static me.qoomon.gitversioning.commons.StringUtil.*;
import static me.qoomon.maven.gitversioning.BuildProperties.projectArtifactId;
import static me.qoomon.maven.gitversioning.GitVersioningMojo.GOAL;
import static me.qoomon.maven.gitversioning.GitVersioningMojo.asPlugin;
import static me.qoomon.maven.gitversioning.MavenUtil.*;
import static org.apache.maven.shared.utils.StringUtils.leftPad;
import static org.apache.maven.shared.utils.StringUtils.repeat;
import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Named("core-default")
@Singleton
@Typed(ModelProcessor.class)
@SuppressWarnings("CdiInjectionPointsInspection")
public class GitVersioningModelProcessor extends DefaultModelProcessor {

    private static final String OPTION_NAME_GIT_REF = "git.ref";
    private static final String OPTION_NAME_GIT_TAG = "git.tag";
    private static final String OPTION_NAME_GIT_BRANCH = "git.branch";
    private static final String OPTION_NAME_DISABLE = "versioning.disable";
    private static final String OPTION_UPDATE_POM = "versioning.updatePom";

    static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";

    final private Logger logger = getLogger(GitVersioningModelProcessor.class);

    @Inject
    private SessionScope sessionScope;

    private boolean initialized = false;
    // --- following fields will be initialized by init() method -------------------------------------------------------

    private MavenSession mavenSession; // can't be injected, cause it's not available before model read
    private File mvnDirectory;
    private GitSituation gitSituation;

    private boolean disabled = false;
    private GitVersionDetails gitVersionDetails;
    boolean updatePom = false;

    private Map<String, Supplier<String>> globalFormatPlaceholderMap;
    private Map<String, String> gitProjectProperties;
    private Set<GAV> relatedProjects;


    // ---- other fields -----------------------------------------------------------------------------------------------

    private final Map<File, Model> sessionModelCache = new HashMap<>();


    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        final Model projectModel = super.read(input, options);
        return processModel(projectModel, options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        final Model projectModel = super.read(input, options);
        return processModel(projectModel, options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        final Model projectModel = super.read(input, options);
        return processModel(projectModel, options);
    }


    private void init(Model projectModel) throws IOException {
        logger.info("");
        logger.info(extensionLogHeader(BuildProperties.projectGAV()));

        // check if session is available
        try {
            mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
        } catch (OutOfScopeException ex) {
            logger.warn("skip - no maven session present");
            disabled = true;
            return;
        }

        File executionRootDirectory = new File(mavenSession.getRequest().getBaseDirectory());
        logger.debug("execution root directory: " + executionRootDirectory);

        mvnDirectory = findMvnDirectory(executionRootDirectory);
        logger.debug(".mvn directory: " + mvnDirectory);

        File configFile = new File(mvnDirectory, projectArtifactId() + ".xml");
        logger.debug("read config from " + configFile);
        Configuration config = readConfig(configFile);

        // check if extension is disabled by command option
        String commandOptionDisable = getCommandOption(OPTION_NAME_DISABLE);
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
        gitSituation = getGitSituation(executionRootDirectory);
        if (gitSituation == null) {
            logger.warn("skip - project is not part of a git repository");
            disabled = true;
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("git situation:");
            logger.debug("  root directory: " + gitSituation.getRootDirectory());
            logger.debug("  head commit: " + gitSituation.getRev());
            logger.debug("  head commit timestamp: " + gitSituation.getTimestamp());
            logger.debug("  head branch: " + gitSituation.getBranch());
            logger.debug("  head tags: " + gitSituation.getTags());
            logger.debug("  head description: " + gitSituation.getDescription());
        }

        if (config.refs.considerTagsOnlyIfHeadIsDetached) {
            logger.debug(buffer().strong("option:").toString() + " consider tags only if head is detached");
        }

        // determine git version details
        gitVersionDetails = getGitVersionDetails(gitSituation, config);

        logger.info("git " + gitVersionDetails.getRefType().name() + " " + buffer().strong(gitVersionDetails.getRefName()));

        Pattern describeTagPattern = gitVersionDetails.getPatchDescription().describeTagPattern;
        if (describeTagPattern != null) {
            logger.debug(buffer().strong("option:").toString() + " describe tag pattern: " + describeTagPattern);
            gitSituation.setDescribeTagPattern(describeTagPattern);
        }

        updatePom = getUpdatePomOption(gitVersionDetails.getPatchDescription());
        if (updatePom) {
            logger.debug(buffer().strong("option:").toString() + " update pom");
        }

        // determine related projects
        relatedProjects = determineRelatedProjects(projectModel);
        logger.debug(buffer().strong("related projects:").toString());
        relatedProjects.forEach(gav -> logger.debug("  " + gav));

        globalFormatPlaceholderMap = generateGlobalFormatPlaceholderMap(gitSituation, gitVersionDetails, mavenSession);
        gitProjectProperties = generateGitProjectProperties(gitSituation, gitVersionDetails);

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
            logger.debug("skip model - can not determine project version - " + projectModel.getPomFile());
            return projectModel;
        }

        if (!relatedProjects.contains(projectGAV)) {
            if (logger.isTraceEnabled()) {
                logger.trace("skip model - unrelated project - " + projectModel.getPomFile());
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

        // log project header
        logger.info(projectLogHeader(projectGAV));

        updateModel(projectModel, gitVersionDetails.getPatchDescription());

        addGitProperties(projectModel);

        File gitVersionedPomFile = writePomFile(projectModel);
        if (updatePom) {
            logger.debug("updating original POM file");
            Files.copy(
                    gitVersionedPomFile.toPath(),
                    projectModel.getPomFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // git versioned pom can't be set as model pom right away, file
        // cause it will break plugins, that trying to update original pom file
        //   e.g. mvn versions:set -DnewVersion=1.0.0
        // That's why we need to add a build plugin that sets project pom file to git versioned pom file
        addBuildPlugin(projectModel);

        logger.info("");
        return projectModel;
    }

    private void updateModel(Model projectModel, RefPatchDescription patchDescription) {

        final String versionFormat = patchDescription.version;
        if (versionFormat != null) {
            updateParentVersion(projectModel, versionFormat);
            updateVersion(projectModel, versionFormat);
            logger.info("project version: " + GAV.of(projectModel).getVersion());

            updateDependencyVersions(projectModel, versionFormat);
            updatePluginVersions(projectModel, versionFormat);
        }

        final Map<String, String> properties = patchDescription.properties;
        if (properties != null && !properties.isEmpty()) {
            updatePropertyValues(projectModel, properties, GAV.of(projectModel));
        }

        // profile section
        updateProfiles(projectModel, patchDescription);
    }


    private void updateProfiles(Model model, RefPatchDescription patchDescription) {
        GAV originalProjectGAV = GAV.of(model);
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
            if (relatedProjects.contains(parentGAV)) {
                String gitVersion = getGitVersion(versionFormat, parentGAV);
                logger.debug("set parent version to " + gitVersion + " (" + parentGAV + ")");
                parent.setVersion(gitVersion);
            }
        }
    }

    private void updateVersion(Model projectModel, String versionFormat) {
        if (projectModel.getVersion() != null) {
            GAV projectGAV = GAV.of(projectModel);
            String gitVersion = getGitVersion(versionFormat, projectGAV);
            logger.debug("set version to " + gitVersion);
            projectModel.setVersion(gitVersion);
        }
    }

    private void updatePropertyValues(ModelBase model, Map<String, String> propertyFormats, GAV originalProjectGAV) {

        boolean logHeader = true;
        // properties section
        for (Map.Entry<Object, Object> modelProperty : model.getProperties().entrySet()) {
            String modelPropertyName = (String) modelProperty.getKey();
            String modelPropertyValue = (String) modelProperty.getValue();

            String propertyFormat = propertyFormats.get(modelPropertyName);
            if (propertyFormat != null) {
                String gitPropertyValue = getGitPropertyValue(propertyFormat, modelPropertyValue, originalProjectGAV);
                if (!gitPropertyValue.equals(modelPropertyValue)) {
                    if (logHeader) {
                        logger.info(sectionLogHeader("properties", model));
                        logHeader = false;
                    }
                    logger.info(modelPropertyName + ": " + gitPropertyValue);
                    model.addProperty(modelPropertyName, gitPropertyValue);
                }
            }
        }
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
                logger.debug(sectionLogHeader("plugins", model));
                for (Plugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
        }

        // plugin management section
        PluginManagement pluginManagement = build.getPluginManagement();
        if (pluginManagement != null) {
            List<Plugin> relatedPlugins = filterRelatedPlugins(pluginManagement.getPlugins());
            if (!relatedPlugins.isEmpty()) {
                logger.debug(buffer().strong("plugin management:").toString());
                for (Plugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
        }

        // reporting section
        Reporting reporting = model.getReporting();
        if (reporting != null) {
            List<ReportPlugin> relatedPlugins = filterRelatedReportPlugins(reporting.getPlugins());
            if (!relatedPlugins.isEmpty()) {
                logger.debug(buffer().strong("reporting plugins:").toString());
                for (ReportPlugin plugin : relatedPlugins) {
                    updateVersion(plugin, versionFormat);
                }
            }
        }
    }

    private void updateVersion(Plugin plugin, String versionFormat) {
        if (plugin.getVersion() != null) {
            GAV pluginGAV = GAV.of(plugin);
            String gitVersion = getGitVersion(versionFormat, pluginGAV);
            logger.debug(pluginGAV.getProjectId() + ": set version to " + gitVersion);
            plugin.setVersion(gitVersion);
        }
    }

    private void updateVersion(ReportPlugin plugin, String versionFormat) {
        if (plugin.getVersion() != null) {
            GAV pluginGAV = GAV.of(plugin);
            String gitVersion = getGitVersion(versionFormat, pluginGAV);
            logger.debug(pluginGAV.getProjectId() + ": set version to " + gitVersion);
            plugin.setVersion(gitVersion);
        }
    }

    private List<Plugin> filterRelatedPlugins(List<Plugin> plugins) {
        return plugins.stream()
                .filter(it -> relatedProjects.contains(GAV.of(it)))
                .collect(toList());
    }

    private List<ReportPlugin> filterRelatedReportPlugins(List<ReportPlugin> plugins) {
        return plugins.stream()
                .filter(it -> relatedProjects.contains(GAV.of(it)))
                .collect(toList());
    }

    private void updateDependencyVersions(ModelBase model, String versionFormat) {
        // dependencies section
        {
            List<Dependency> relatedDependencies = filterRelatedDependencies(model.getDependencies());
            if (!relatedDependencies.isEmpty()) {
                logger.debug(sectionLogHeader("dependencies", model));
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
                logger.debug(buffer().strong("dependency management:").toString());
                for (Dependency dependency : relatedDependencies) {
                    updateVersion(dependency, versionFormat);
                }
            }
        }
    }

    private void updateVersion(Dependency dependency, String versionFormat) {
        if (dependency.getVersion() != null) {
            GAV dependencyGAV = GAV.of(dependency);
            String gitVersion = getGitVersion(versionFormat, dependencyGAV);
            logger.debug(dependencyGAV.getProjectId() + ": set version to " + gitVersion);
            dependency.setVersion(gitVersion);
        }
    }

    public List<Dependency> filterRelatedDependencies(List<Dependency> dependencies) {
        return dependencies.stream()
                .filter(it -> relatedProjects.contains(GAV.of(it)))
                .collect(toList());
    }

    private void addGitProperties(Model projectModel) {
        gitProjectProperties.forEach(projectModel::addProperty);
    }

    private void addBuildPlugin(Model projectModel) {
        logger.debug("add version build plugin");

        Plugin plugin = asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(GOAL);
        execution.getGoals().add(GOAL);

        plugin.getExecutions().add(execution);

        if (projectModel.getBuild() == null) {
            projectModel.setBuild(new Build());
        }
        projectModel.getBuild().getPlugins().add(plugin);
    }


    // ---- versioning -------------------------------------------------------------------------------------------------

    private GitSituation getGitSituation(File executionRootDirectory) throws IOException {
        final FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(executionRootDirectory);
        if (repositoryBuilder.getGitDir() == null) {
            return null;
        }

        final Repository repository = repositoryBuilder.build();
        return new GitSituation(repository) {
            {
                String providedRef = getCommandOption(OPTION_NAME_GIT_REF);
                if (providedRef != null) {
                    if (!providedRef.startsWith("refs/")) {
                        throw new IllegalArgumentException("unexpected ref format" + providedRef + " -  needs to start with refs/");
                    }

                    if (providedRef.startsWith("refs/tags/")) {
                        overrideTags(providedRef);
                        overrideBranch(null);
                    } else {
                        overrideBranch(providedRef);
                        overrideTags(null);
                    }
                }

                final String providedTag = getCommandOption(OPTION_NAME_GIT_TAG);
                if (providedTag != null) {
                    overrideTags(providedTag);
                    overrideBranch(null);
                }

                final String providedBranch = getCommandOption(OPTION_NAME_GIT_BRANCH);
                if (providedBranch != null) {
                    overrideBranch(providedBranch);
                    overrideTags(null);
                }
            }

            void overrideBranch(String branch) {
                if (branch != null && branch.isEmpty()) {
                    branch = null;
                }
                logger.debug("override git branch with: " + branch);
                if (branch != null) {
                    branch = branch.replaceFirst("^refs/heads/", "");
                    // GitHub Pull Requests
                    branch = branch.replaceFirst("^refs/", "");
                }

                setBranch(branch);
            }

            void overrideTags(String tag) {
                if (tag != null && tag.isEmpty()) {
                    tag = null;
                }
                logger.debug("override git tags with: " + tag);
                if (tag != null) {
                    tag = tag.replaceFirst("^refs/tags/", "");
                }
                setTags(tag == null ? emptyList() : singletonList(tag));
            }
        };
    }

    private static GitVersionDetails getGitVersionDetails(GitSituation gitSituation, Configuration config) {
        final Lazy<List<String>> sortedTags = Lazy.by(() -> gitSituation.getTags().stream()
                .sorted(comparing(DefaultArtifactVersion::new)).collect(toList()));
        for (RefPatchDescription refConfig : config.refs.list) {
            switch (refConfig.type) {
                case TAG: {
                    if (!config.refs.considerTagsOnlyIfHeadIsDetached || gitSituation.isDetached()) {
                        for (String tag : sortedTags.get()) {
                            if (refConfig.pattern == null || refConfig.pattern.matcher(tag).matches()) {
                                return new GitVersionDetails(gitSituation.getRev(), refConfig.type,
                                        tag, refConfig);
                            }
                        }
                    }
                }
                break;
                case BRANCH: {
                    if (!gitSituation.isDetached()) {
                        String branch = gitSituation.getBranch();
                        if (refConfig.pattern == null || refConfig.pattern.matcher(branch).matches()) {
                            return new GitVersionDetails(gitSituation.getRev(), refConfig.type,
                                    branch, refConfig);
                        }
                    }
                }
                break;
                default:
                    throw new IllegalArgumentException("Unexpected ref type: " + refConfig.type);
            }
        }

        if (config.rev != null) {
            return new GitVersionDetails(gitSituation.getRev(), COMMIT,
                    gitSituation.getRev(), RefPatchDescription.of(config.rev));
        }

        throw new RuntimeException("No matching ref config nor rev config found.");
    }

    private String getGitVersion(String versionFormat, GAV originalProjectGAV) {
        final Map<String, Supplier<String>> placeholderMap = generateFormatPlaceholderMap(originalProjectGAV);

        return slugify(substituteText(versionFormat, placeholderMap));
    }

    private String getGitPropertyValue(String propertyFormat, String originalValue, GAV originalProjectGAV) {
        final Map<String, Supplier<String>> placeholderMap = generateFormatPlaceholderMap(originalProjectGAV);
        placeholderMap.put("value", () -> originalValue);
        return substituteText(propertyFormat, placeholderMap);
    }

    private Map<String, Supplier<String>> generateFormatPlaceholderMap(GAV originalProjectGAV) {
        final Map<String, Supplier<String>> placeholderMap = new HashMap<>(globalFormatPlaceholderMap);
        final Supplier<String> originalProjectVersion = originalProjectGAV::getVersion;
        placeholderMap.put("version", originalProjectVersion);
        placeholderMap.put("version.release", Lazy.by(
                () -> originalProjectVersion.get().replaceFirst("-SNAPSHOT$", "")));
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
        placeholderMap.put("commit.timestamp.month", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getMonthValue()), 2, "0")));
        placeholderMap.put("commit.timestamp.day", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getDayOfMonth()), 2, "0")));
        placeholderMap.put("commit.timestamp.hour", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getHour()), 2, "0")));
        placeholderMap.put("commit.timestamp.minute", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getMinute()), 2, "0")));
        placeholderMap.put("commit.timestamp.second", Lazy.by(() -> leftPad(String.valueOf(headCommitDateTime.get().getSecond()), 2, "0")));
        placeholderMap.put("commit.timestamp.datetime", Lazy.by(() -> headCommitDateTime.get().toEpochSecond() > 0
                ? headCommitDateTime.get().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")) : "00000000.000000"));

        final String refName = gitVersionDetails.getRefName();
        final Lazy<String> refNameSlug = Lazy.by(() -> slugify(refName));
        placeholderMap.put("ref", () -> refName);
        placeholderMap.put("ref" + ".slug", refNameSlug);

        final Pattern refPattern = gitVersionDetails.getPatchDescription().pattern;
        if (refPattern != null) {
            // ref pattern groups
            for (Map.Entry<String, String> patternGroup : patternGroupValues(refPattern, refName).entrySet()) {
                final String groupName = patternGroup.getKey();
                final String value = patternGroup.getValue() != null ? patternGroup.getValue() : "";
                placeholderMap.put("ref." + groupName, () -> value);
                placeholderMap.put("ref." + groupName + ".slug", Lazy.by(() -> slugify(value)));
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
        placeholderMap.put("describe.distance", Lazy.by(() -> String.valueOf(description.get().getDistance())));

        // describe tag pattern groups
        final Lazy<Map<String, String>> describeTagPatternValues = Lazy.by(
                () -> patternGroupValues(gitSituation.getDescribeTagPattern(), descriptionTag.get()));
        for (String groupName : patternGroups(gitSituation.getDescribeTagPattern())) {
            Lazy<String> value = Lazy.by(() -> describeTagPatternValues.get().get(groupName));
            placeholderMap.put("describe.tag." + groupName, value);
            placeholderMap.put("describe.tag." + groupName + ".slug", Lazy.by(() -> slugify(value.get())));
        }

        // command parameters e.g. mvn -Dfoo=123 will be available as ${foo}
        mavenSession.getUserProperties().forEach((key, value) -> placeholderMap.put((String) key, () -> (String) value));

        // environment variables e.g. BUILD_NUMBER=123 will be available as ${env.BUILD_NUMBER}
        System.getenv().forEach((key, value) -> placeholderMap.put("env." + key, () -> value));

        return placeholderMap;
    }

    private static Map<String, String> generateGitProjectProperties(GitSituation gitSituation, GitVersionDetails gitVersionDetails) {
        final Map<String, String> properties = new HashMap<>();

        properties.put("git.commit", gitVersionDetails.getCommit());
        properties.put("git.commit.short", gitVersionDetails.getCommit().substring(0, 7));

        final ZonedDateTime headCommitDateTime = gitSituation.getTimestamp();
        properties.put("git.commit.timestamp", String.valueOf(headCommitDateTime.toEpochSecond()));
        properties.put("git.commit.timestamp.datetime", headCommitDateTime.toEpochSecond() > 0
                ? headCommitDateTime.format(ISO_INSTANT) : "0000-00-00T00:00:00Z");

        final String refName = gitVersionDetails.getRefName();
        final String refNameSlug = slugify(refName);
        properties.put("git.ref", refName);
        properties.put("git.ref" + ".slug", refNameSlug);

        return properties;
    }


    // ---- configuration -------------------------------------------------------------------------------------------------

    private static File findMvnDirectory(File baseDirectory) throws IOException {
        File searchDirectory = baseDirectory;
        while (searchDirectory != null) {
            File mvnDir = new File(searchDirectory, ".mvn");
            if (mvnDir.exists()) {
                return mvnDir;
            }
            searchDirectory = searchDirectory.getParentFile();
        }

        throw new FileNotFoundException("Can not find .mvn directory in hierarchy of " + baseDirectory);
    }

    private static Configuration readConfig(File configFile) throws IOException {
        final XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.enable(ACCEPT_CASE_INSENSITIVE_ENUMS);

        final Configuration config = xmlMapper.readValue(configFile, Configuration.class);

        List<PatchDescription> patchDescriptions = new ArrayList<>(config.refs.list);
        if (config.rev != null) {
            patchDescriptions.add(config.rev);
        }

        for (PatchDescription patchDescription : patchDescriptions) {
            if (patchDescription.describeTagPattern == null && config.describeTagPattern != null) {
                patchDescription.describeTagPattern = config.describeTagPattern;
            }
            if (patchDescription.updatePom == null && config.updatePom != null) {
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

        if (gitRefConfig.updatePom != null) {
            return gitRefConfig.updatePom;
        }

        return false;
    }

    // ---- determine related projects ---------------------------------------------------------------------------------

    private Set<GAV> determineRelatedProjects(Model projectModel) throws IOException {
        final HashSet<GAV> relatedProjects = new HashSet<>();
        determineRelatedProjects(projectModel, relatedProjects);
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
        logger.debug("generate " + gitVersionedPomFile);

        // read original pom file
        Document gitVersionedPomDocument = readXml(projectModel.getPomFile());
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
            Properties modelProperties = model.getProperties();
            gitVersionDetails.getPatchDescription().properties.keySet().forEach(propertyName -> {
                Element propertyElement = propertiesElement.getChild(propertyName);
                if (propertyElement != null) {
                    String pomPropertyValue = propertyElement.getText();
                    String modelPropertyValue = (String) modelProperties.get(propertyName);
                    if (!Objects.equals(modelPropertyValue, pomPropertyValue)) {
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
        });
    }

    private static void updateReportPluginVersions(Element pluginsElement, List<ReportPlugin> plugins) {
        forEachPair(pluginsElement.getChildren(), plugins, (pluginElement, plugin) -> {
            // sanity check
            if (!Objects.equals(plugin.getKey(), getPluginKey(pluginElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model plugin order");
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
                Profile profile = profileMap.get(profileElement.getChild("id").getText());
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

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("/", "-");
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
}
