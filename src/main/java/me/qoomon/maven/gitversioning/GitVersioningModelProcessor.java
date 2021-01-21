package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import me.qoomon.gitversioning.*;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static me.qoomon.maven.gitversioning.BuildProperties.projectArtifactId;
import static me.qoomon.maven.gitversioning.GitVersioningMojo.*;
import static me.qoomon.maven.gitversioning.MavenUtil.pomFile;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static org.apache.maven.shared.utils.StringUtils.repeat;
import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;

/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Named("core-default")
@Singleton
@Typed(ModelProcessor.class)
@SuppressWarnings("CdiInjectionPointsInspection")
public class GitVersioningModelProcessor extends DefaultModelProcessor {

    private static final String OPTION_NAME_GIT_TAG = "git.tag";
    private static final String OPTION_NAME_GIT_BRANCH = "git.branch";
    private static final String OPTION_NAME_DISABLE = "versioning.disable";
    private static final String OPTION_UPDATE_POM = "versioning.updatePom";
    private static final String OPTION_PREFER_TAGS = "versioning.preferTags";

    @Inject
    private Logger logger;

    @Inject
    private SessionScope sessionScope;

    private boolean initialized = false;

    // can't be injected, cause it's not available before model read
    private MavenSession mavenSession;
    private File mvnDirectory;

    private File gitDirectory;
    private GitVersionDetails gitVersionDetails;

    private boolean disabled = false;
    private boolean updatePomOption = false;
    private Set<GAV> relatedProjects;

    private final Set<File> projectModules = new HashSet<>();
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
        String extensionId = BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion();
        logger.info(extensionLogFormat(extensionId));

        // check if session is available
        try {
            mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
        } catch (OutOfScopeException ex) {
            logger.warn("skip - no maven session present");
            disabled = true;
            return;
        }

        // check if extension is disabled
        String propertyOptionDisableValue = projectModel.getProperties().getProperty(OPTION_NAME_DISABLE);
        if (propertyOptionDisableValue != null) {
            disabled = parseBoolean(propertyOptionDisableValue);
        }
        String commandOptionDisable = getCommandOption(OPTION_NAME_DISABLE);
        if (commandOptionDisable != null) {
            disabled = parseBoolean(commandOptionDisable);
        }
        if (disabled) {
            logger.info("skip - versioning is disabled");
            return;
        }

        File executionRootDirectory = new File(mavenSession.getRequest().getBaseDirectory());
        logger.debug("execution root directory: " + executionRootDirectory);

        mvnDirectory = findMvnDirectory(executionRootDirectory);
        logger.debug(".mvn directory: " + mvnDirectory);

        File configFile = new File(mvnDirectory, projectArtifactId() + ".xml");
        logger.debug("read config from " + configFile);
        Configuration config = readConfig(configFile);

        gitDirectory = findGitDir(executionRootDirectory);
        if (gitDirectory == null || !gitDirectory.exists()) {
            logger.warn("skip - project is not part of a git repository");
            disabled = true;
            return;
        }
        logger.debug("git directory: " + gitDirectory.toString());

        gitVersionDetails = getGitVersionDetails(config, executionRootDirectory);
        logger.info("git ref: " + gitVersionDetails.getCommitRefType() + " " + buffer().strong(gitVersionDetails.getCommitRefName()));

        updatePomOption = getUpdatePomOption(config, gitVersionDetails);

        // determine related projects
        relatedProjects = determineRelatedProjects(projectModel);
        logger.debug("related projects:");
        relatedProjects.forEach(gav -> logger.debug("  " + gav));

        // add session root project as initial module
        projectModules.add(projectModel.getPomFile());

        logger.info("");
    }

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

        if (projectModel.getPomFile().getName().equals(GIT_VERSIONING_POM_NAME)) {
            logger.debug("skip model - git-versioned pom - " + projectModel.getPomFile());
            return projectModel;
        }

        if (GAV.of(projectModel).getVersion() == null) {
            logger.debug("skip model - can not determine project version - " + projectModel.getPomFile());
            return projectModel;
        }

        File canonicalProjectPomFile = projectModel.getPomFile().getCanonicalFile();

        if (!projectModules.contains(canonicalProjectPomFile)) {
            logger.debug("skip model - non project module - " + projectModel.getPomFile());
            return projectModel;
        }

        // return cached calculated project model if present
        Model cachedProjectModel = sessionModelCache.get(canonicalProjectPomFile);
        if (cachedProjectModel != null) {
            return cachedProjectModel;
        }

        // add current project model to session project models
        sessionModelCache.put(canonicalProjectPomFile, projectModel);


        // --------------------------- update project model versions ---------------------------------------------------

        GAV originalProjectGAV = GAV.of(projectModel);

        // log project header
        logger.info(buffer().strong("--- ") + buffer().project(originalProjectGAV).toString() + buffer().strong(" ---"));

        updateParent(projectModel);

        updateVersion(projectModel);

        updateProperties(projectModel, originalProjectGAV.getVersion());

        addGitProperties(projectModel);

        updateDependencies(projectModel);

        updatePlugins(projectModel);

        // TODO generate right away only do profile replacement within plugin
        // add build plugin to model to generate git-versioned pom.xml
        addBuildPlugin(projectModel);

        // add potential project modules
        for (File modulePomFile : getProjectModules(projectModel)) {
            projectModules.add(modulePomFile.getCanonicalFile());
        }

        logger.info("");
        return projectModel;
    }

    private GitVersionDetails getGitVersionDetails(Configuration config, File repositoryDirectory) {
        GitRepoSituation repoSituation = GitUtil.situation(repositoryDirectory);
        String providedTag = getCommandOption(OPTION_NAME_GIT_TAG);
        if (providedTag != null) {
            repoSituation.setHeadBranch(null);
            repoSituation.setHeadTags(providedTag.isEmpty() ? emptyList() : singletonList(providedTag));
        }
        String providedBranch = getCommandOption(OPTION_NAME_GIT_BRANCH);
        if (providedBranch != null) {
            repoSituation.setHeadBranch(providedBranch.isEmpty() ? null : providedBranch);
        }

        final boolean preferTagsOption = getPreferTagsOption(config);
        return GitVersioning.determineVersion(repoSituation,
                ofNullable(config.commit)
                        .map(it -> new VersionDescription(null, it.versionFormat, convertPropertyDescription(it.property)))
                        .orElse(new VersionDescription()),
                config.branch.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat, convertPropertyDescription(it.property)))
                        .collect(toList()),
                config.tag.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat, convertPropertyDescription(it.property)))
                        .collect(toList()),
                preferTagsOption);
    }

    private Set<GAV> determineRelatedProjects(Model projectModel) throws IOException {
        HashSet<GAV> relatedProjects = new HashSet<>();
        determineRelatedProjects(projectModel, relatedProjects);
        return relatedProjects;
    }

    private void determineRelatedProjects(Model projectModel, Set<GAV> relatedProjects) throws IOException {
        GAV projectGAV = GAV.of(projectModel);
        if (relatedProjects.contains(projectGAV)) {
            return;
        }

        // add self
        relatedProjects.add(projectGAV);

        // check for related parent project by parent tag
        if (projectModel.getParent() != null) {
            GAV parentGAV = GAV.of(projectModel.getParent());
            File parentProjectPomFile = getParentProjectPomFile(projectModel);
            if (isRelatedPom(parentProjectPomFile)) {
                Model parentProjectModel = readModel(parentProjectPomFile);
                GAV parentProjectGAV = GAV.of(parentProjectModel);
                if (parentProjectGAV.equals(parentGAV)) {
                    determineRelatedProjects(parentProjectModel, relatedProjects);
                }
            }
        }

        // check for related parent project within parent directory
        Model parentProjectModel = searchParentProjectInParentDirectory(projectModel);
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
                && pomFile.getCanonicalPath().startsWith(gitDirectory.getParentFile().getCanonicalPath() + File.separator);
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

    private String getGitVersion(GAV projectGAV) {
        String projectVersion = projectGAV.getVersion();
        if (isNullOrEmpty(projectVersion)) {
            return null;
        }
        return gitVersionDetails.getVersionTransformer().apply(projectVersion);
    }

    private void updateParent(Model projectModel) {
        final Parent parent = projectModel.getParent();
        if (parent != null) {
            GAV parentGAV = GAV.of(parent);
            if (relatedProjects.contains(parentGAV)) {
                String gitVersion = getGitVersion(parentGAV);
                logger.info("update parent version: " + parentGAV.getProjectId() + ":" + gitVersion);
                parent.setVersion(getGitVersion(parentGAV));
            }
        }
    }

    private void updateVersion(Model projectModel) {
        if (projectModel.getVersion() != null) {
            GAV projectGAV = GAV.of(projectModel);
            String gitVersion = getGitVersion(projectGAV);
            logger.info("update version: " + projectGAV + " -> " + gitVersion);
            projectModel.setVersion(gitVersion);
        }
    }

    private void updateProperties(Model projectModel, String originalVersion) {
        // properties section
        {
            final Map<String, String> gitProperties = gitVersionDetails.getPropertiesTransformer()
                    .apply(Maps.fromProperties(projectModel.getProperties()), originalVersion);
            for (Entry<String, String> property : gitProperties.entrySet()) {
                if (!property.getValue().equals(projectModel.getProperties().getProperty(property.getKey()))) {
                    logger.info("update property: " + property.getKey() + ": " + property.getValue());
                    projectModel.getProperties().setProperty(property.getKey(), property.getValue());
                }
            }
        }
        // profiles section
        for (Profile profile : projectModel.getProfiles()) {
            // properties section
            {
                final Map<String, String> gitProperties = gitVersionDetails.getPropertiesTransformer()
                        .apply(Maps.fromProperties(profile.getProperties()), originalVersion);
                for (Entry<String, String> property : gitProperties.entrySet()) {
                    if (!property.getValue().equals(projectModel.getProperties().getProperty(property.getKey()))) {
                        logger.info("update profile '" + profile.getId() + "' property: " + property.getKey() + ": " + property.getValue());
                        projectModel.getProperties().setProperty(property.getKey(), property.getValue());
                    }
                }
            }
        }


        // TODO update project properties update git versioning lib
//        PropertiesTransformer propertyTransformer = gitVersionDetails.getPropertyTransformer(projectGAV.getVersion());
//        // properties section
//        {
//            for (Entry<String, String> property : Maps.fromProperties(projectModel.getProperties()).entrySet()) {
//                String transformedPropertyValue = propertyTransformer.apply(property.getKey(), property.getValue());
//                projectModel.addProperty(property.getKey(), transformedPropertyValue);
//            }
//        }
//        // profile section
//        for (Profile profile : projectModel.getProfiles()) {
//            // properties section
//            {
//                for (Entry<String, String> property : Maps.fromProperties(projectModel.getProperties()).entrySet()) {
//                    String transformedPropertyValue = propertyTransformer.apply(property.getKey(), property.getValue());
//                    projectModel.addProperty(property.getKey(), transformedPropertyValue);
//                }
//            }
//        }
    }

    private void addGitProperties(Model projectModel) {
        projectModel.addProperty("git.commit", gitVersionDetails.getCommit());
        projectModel.addProperty("git.commit.timestamp", Long.toString(gitVersionDetails.getCommitTimestamp()));
        projectModel.addProperty("git.commit.timestamp.datetime", toTimestampDateTime(gitVersionDetails.getCommitTimestamp()));
        projectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
        projectModel.addProperty("git.ref.slug", gitVersionDetails.getCommitRefName().toLowerCase().replaceAll("/", "-"));
        projectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());
        projectModel.addProperty("git.dirty", Boolean.toString(!gitVersionDetails.isClean()));
    }

    private void updatePlugins(Model projectModel) {
        for (Plugin plugin : getProjectPlugins(projectModel)) {
            GAV pluginGAV = GAV.of(plugin);
            if (relatedProjects.contains(pluginGAV)) {
                String gitVersion = getGitVersion(pluginGAV);
                logger.info("update plugin version: " + pluginGAV.getProjectId() + ":" + gitVersion);
                plugin.setVersion(gitVersion);
            }
        }
    }

    private void updateDependencies(Model projectModel) {
        for (Dependency dependency : getProjectDependencies(projectModel)) {
            GAV dependencyGAV = GAV.of(dependency);
            if (relatedProjects.contains(dependencyGAV)) {
                String gitVersion = getGitVersion(dependencyGAV);
                logger.info("update dependency version: " + dependencyGAV.getProjectId() + ":" + gitVersion);
                dependency.setVersion(gitVersion);
            }
        }
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

        // add plugin properties
        projectModel.getProperties().setProperty(propertyKeyUpdatePom, Boolean.toString(updatePomOption));
    }

    private static List<Dependency> getProjectDependencies(Model projectModel) {
        final List<Dependency> dependencies = Lists.newArrayList();
        {
            // dependencies section
            dependencies.addAll(projectModel.getDependencies());

            // dependency management section
            DependencyManagement dependencyManagement = projectModel.getDependencyManagement();
            if (dependencyManagement != null) {
                dependencies.addAll(dependencyManagement.getDependencies());
            }
        }

        // profiles section
        for (Profile profile : projectModel.getProfiles()) {

            // dependencies section
            dependencies.addAll(profile.getDependencies());

            // dependency management section
            DependencyManagement profileDependencyManagement = profile.getDependencyManagement();
            if (profileDependencyManagement != null) {
                dependencies.addAll(profileDependencyManagement.getDependencies());
            }
        }

        return dependencies;
    }

    private static List<Plugin> getProjectPlugins(Model projectModel) {
        final List<Plugin> plugins = Lists.newArrayList();
        {
            // build section
            Build build = projectModel.getBuild();
            if (build != null) {

                // plugins section
                plugins.addAll(build.getPlugins());

                // plugin management section
                PluginManagement pluginManagement = build.getPluginManagement();
                if (pluginManagement != null) {
                    plugins.addAll(pluginManagement.getPlugins());
                }
            }
        }

        // profiles section
        for (Profile profile : projectModel.getProfiles()) {

            // build section
            BuildBase build = profile.getBuild();
            if (build != null) {

                // plugins section
                plugins.addAll(build.getPlugins());

                // plugin management section
                PluginManagement pluginManagement = build.getPluginManagement();
                if (pluginManagement != null) {
                    plugins.addAll(pluginManagement.getPlugins());
                }
            }
        }

        return plugins;
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

        return modules;
    }

    private static List<PropertyDescription> convertPropertyDescription(
            List<Configuration.PropertyDescription> confPropertyDescription) {
        return confPropertyDescription.stream()
                .map(prop -> new PropertyDescription(
                        prop.pattern, new PropertyValueDescription(prop.valuePattern, prop.valueFormat)))
                .collect(toList());
    }

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

    private static File findGitDir(File baseDirectory) {
        return new FileRepositoryBuilder().findGitDir(baseDirectory).getGitDir();
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

    private static Configuration readConfig(File configFile) throws IOException {
        return new XmlMapper().readValue(configFile, Configuration.class);
    }

    private boolean getPreferTagsOption(final Configuration config) {
        final boolean preferTagsOption;
        final String preferTagsCommandOption = getCommandOption(OPTION_PREFER_TAGS);
        if (preferTagsCommandOption != null) {
            preferTagsOption = parseBoolean(preferTagsCommandOption);
        } else if (config.preferTags != null) {
            preferTagsOption = config.preferTags;
        } else {
            preferTagsOption = false;
        }
        return preferTagsOption;
    }

    private boolean getUpdatePomOption(final Configuration config, final GitVersionDetails gitVersionDetails) {
        String updatePomCommandOption = getCommandOption(OPTION_UPDATE_POM);
        if (updatePomCommandOption != null) {
            return parseBoolean(updatePomCommandOption);
        }

        boolean updatePomOption = config.updatePom != null && config.updatePom;
        if (gitVersionDetails.getCommitRefType().equals("tag")) {
            updatePomOption = config.tag.stream()
                    .filter(it -> Pattern.matches(it.pattern, gitVersionDetails.getCommitRefName()))
                    .findFirst()
                    .map(it -> it.updatePom)
                    .orElse(updatePomOption);
        } else if (gitVersionDetails.getCommitRefType().equals("branch")) {
            updatePomOption = config.branch.stream()
                    .filter(it -> Pattern.matches(it.pattern, gitVersionDetails.getCommitRefName()))
                    .findFirst()
                    .map(it -> it.updatePom)
                    .orElse(updatePomOption);
        } else if (config.commit != null) {
            updatePomOption = Optional.ofNullable(config.commit.updatePom)
                    .orElse(updatePomOption);
        }

        return updatePomOption;
    }

    private static String extensionLogFormat(String extensionId) {
        int extensionIdPadding = 72 - 2 - extensionId.length();
        int extensionIdPaddingLeft = (int) ceil(extensionIdPadding / 2.0);
        int extensionIdPaddingRight = (int) floor(extensionIdPadding / 2.0);
        return buffer().strong(repeat("-", extensionIdPaddingLeft))
                + " " + buffer().mojo(extensionId) + " "
                + buffer().strong(repeat("-", extensionIdPaddingRight));
    }

    private static String toTimestampDateTime(long timestamp) {
        if (timestamp == 0) {
            return "0000-00-00T00:00:00Z";
        }

        return DateTimeFormatter.ISO_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(timestamp));
    }
}
