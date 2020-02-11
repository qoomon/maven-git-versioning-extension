package me.qoomon.maven.gitversioning;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static me.qoomon.maven.gitversioning.VersioningMojo.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.VersioningMojo.GOAL;
import static me.qoomon.maven.gitversioning.VersioningMojo.asPlugin;
import static me.qoomon.maven.gitversioning.VersioningMojo.propertyKeyPrefix;
import static me.qoomon.maven.gitversioning.VersioningMojo.propertyKeyUpdatePom;
import static org.apache.maven.shared.utils.StringUtils.repeat;
import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;

import me.qoomon.gitversioning.GitRepoSituation;
import me.qoomon.gitversioning.GitUtil;
import me.qoomon.gitversioning.GitVersionDetails;
import me.qoomon.gitversioning.GitVersioning;
import me.qoomon.gitversioning.PropertyDescription;
import me.qoomon.gitversioning.PropertyValueDescription;
import me.qoomon.gitversioning.VersionDescription;

/**
 * WORKAROUND
 * Initialize and use {@link GitVersioningModelProcessor} from GitModelProcessor {@link org.apache.maven.model.building.ModelProcessor},
 * This is need because maven 3.6.2 has broken component replacement mechanism.
 */
public class GitVersioningModelProcessor {
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
    private boolean disabled = false;

    private MavenSession mavenSession;  // can not be injected cause it is not always available

    private File gitRepositoryDirectory;
    private Configuration config;
    private GitVersionDetails gitVersionDetails;

    private final Set<String> allProjectDirectories = new HashSet<>();
    private final Map<String, Model> virtualProjectModelCache = new HashMap<>();

    public Model processModel(Model projectModel, Map<String, ?> options) throws IOException {
        if(this.disabled){
            return projectModel;
        }

        try {
            if (!initialized) {
                logger.info("");
                String extensionId = BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion();
                logger.info(extensionLogFormat(extensionId));

                try {
                    mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
                } catch (OutOfScopeException ex) {
                    logger.warn("versioning is disabled, because no maven session present");
                    disabled = true;
                    return projectModel;
                }

                if (parseBoolean(getCommandOption(OPTION_NAME_DISABLE))) {
                    logger.info("versioning is disabled");
                    disabled = true;
                    return projectModel;
                }

                gitRepositoryDirectory = getRepositoryRootDirectory(new File(mavenSession.getExecutionRootDirectory()));
                if (gitRepositoryDirectory == null) {
                    throw new IllegalArgumentException(
                            mavenSession.getExecutionRootDirectory() + " directory is not a git repository (or any of the parent directories)");
                }

                logger.info("Adjusting project models...");
                logger.info("");
                initialized = true;
            }

            final Source pomSource = (Source) options.get(ModelProcessor.SOURCE);
            if (pomSource != null) {
                projectModel.setPomFile(new File(pomSource.getLocation()));
            }

            return processModel(projectModel);
        } catch (Exception e) {
            throw new IOException("Git Versioning Model Processor", e);
        }
    }

    private Model processModel(Model projectModel) throws IOException {
        if (!isRelatedProject(projectModel.getPomFile())) {
            logger.debug("skip - unrelated pom location - " + projectModel.getPomFile());
            return projectModel;
        }

        if (projectModel.getPomFile().getName().equals(GIT_VERSIONING_POM_NAME)) {
            logger.debug("skip - git versioned pom - " + projectModel.getPomFile());
            return projectModel;
        }

        GAV projectGav = GAV.of(projectModel);
        if (projectGav.getVersion() == null) {
            logger.debug("skip - invalid model - 'version' is missing - " + projectModel.getPomFile());
            return projectModel;
        }

        if (config == null) {
            config = loadConfig(projectModel);
        }

        if (gitVersionDetails == null) {
            gitVersionDetails = getGitVersionDetails(config, projectModel);
        }

        String projectId = projectGav.getProjectId();
        Model virtualProjectModel = this.virtualProjectModelCache.get(projectId);
        if (virtualProjectModel == null) {
            logger.info(buffer().strong("--- ") + buffer().project(projectId).toString() + " @ " + gitVersionDetails.getCommitRefType() + " " + buffer().strong(gitVersionDetails.getCommitRefName()) + buffer().strong(" ---"));

            virtualProjectModel = projectModel.clone();


            // ---------------- gather all sub projects -----------------------------------

            allProjectDirectories.add(projectModel.getProjectDirectory().getCanonicalPath());
            for (String module : projectModel.getModules()) {
                allProjectDirectories.add(new File(projectModel.getProjectDirectory(), module).getCanonicalPath());
            }
            for (Profile profile : projectModel.getProfiles()){
                for (String module :  profile.getModules()) {
                    allProjectDirectories.add(new File(projectModel.getProjectDirectory(), module).getCanonicalPath());
                }
            }

            // ---------------- process parent -----------------------------------

            final Parent parent = projectModel.getParent();
            boolean parentIsProjectPom = false;
            if (parent != null) {

                if (parent.getVersion() == null) {
                    logger.warn("skip - invalid model - parent 'version' is missing - " + projectModel.getPomFile());
                    return projectModel;
                }

                Model parentModel = getParentModel(projectModel);
                if (parentModel != null && isRelatedProject(parentModel.getPomFile())) {
                    if (virtualProjectModel.getVersion() != null) {
                        virtualProjectModel.setVersion(null);
                        logger.warn("Do not set version tag in a multi module project module: " + projectModel.getPomFile());
                        if (!projectModel.getVersion().equals(parent.getVersion())) {
                            throw new IllegalStateException("'version' has to be equal to parent 'version'");
                        }
                    }

                    logger.debug(" replace parent version");
                    final String parentVersion = virtualProjectModel.getParent().getVersion();
                    final String gitParentVersion = gitVersionDetails.getVersionTransformer().apply(parentVersion);
                    virtualProjectModel.getParent().setVersion(gitParentVersion);
                }
            }


            // ---------------- process project -----------------------------------

            if (virtualProjectModel.getVersion() != null) {
                final String gitVersion = gitVersionDetails.getVersionTransformer().apply(projectGav.getVersion());
                logger.info("project version: " + buffer().strong(gitVersion));
                virtualProjectModel.setVersion(gitVersion);
            }

            final Map<String, String> gitProperties = gitVersionDetails.getPropertiesTransformer().apply(
                    Maps.fromProperties(virtualProjectModel.getProperties()), projectGav.getVersion());
            if (!gitProperties.isEmpty()) {
                logger.info("properties:");
                for (Entry<String, String> property : gitProperties.entrySet()) {
                    if (!property.getValue().equals(virtualProjectModel.getProperties().getProperty(property.getKey()))) {
                        logger.info("  " + property.getKey() + ": " + property.getValue());
                        virtualProjectModel.getProperties().setProperty(property.getKey(), property.getValue());
                    }
                }
            }

            // TODO
            // update version within dependencies, dependency management, plugins, plugin management

            logger.info("");

            virtualProjectModel.addProperty("git.commit", gitVersionDetails.getCommit());
            virtualProjectModel.addProperty("git.commit.timestamp", Long.toString(gitVersionDetails.getCommitTimestamp()));
            virtualProjectModel.addProperty("git.commit.timestamp.datetime", toTimestampDateTime(gitVersionDetails.getCommitTimestamp()));
            virtualProjectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git.dirty", Boolean.toString(!gitVersionDetails.isClean()));

            // ---------------- add plugin ---------------------------------------

            boolean isProjectPom = allProjectDirectories.contains(virtualProjectModel.getPomFile().getParentFile().getCanonicalPath());
            if(isProjectPom) {
                boolean updatePomOption = getUpdatePomOption(config, gitVersionDetails);
                addBuildPlugin(virtualProjectModel, updatePomOption);
            }

            this.virtualProjectModelCache.put(projectId, virtualProjectModel);
        }
        return virtualProjectModel;
    }

    private GitVersionDetails getGitVersionDetails(Configuration config, Model projectModel) {
        GitRepoSituation repoSituation = GitUtil.situation(projectModel.getPomFile());
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

    private List<PropertyDescription> convertPropertyDescription(
            List<Configuration.PropertyDescription> confPropertyDescription) {
        return confPropertyDescription.stream()
                .map(prop -> new PropertyDescription(
                        prop.pattern, new PropertyValueDescription(prop.valuePattern, prop.valueFormat)))
                .collect(toList());
    }

    private Model getParentModel(Model projectModel) {
        if (projectModel.getParent() == null) {
            return null;
        }

        File parentPomPath = new File(projectModel.getProjectDirectory(), projectModel.getParent().getRelativePath());
        final File parentPom;
        if (parentPomPath.isDirectory()) {
            parentPom = new File(parentPomPath, "pom.xml");
        } else {
            parentPom = parentPomPath;
        }

        if (!parentPom.exists()) {
            return null;
        }

        Model parentModel = unchecked(() -> readModel(parentPom));

        GAV parentModelGav = GAV.of(parentModel);
        GAV parentGav = GAV.of(projectModel.getParent());
        if (!parentModelGav.equals(parentGav)) {
            return null;
        }

        return parentModel;
    }

    private File findConfigFile(Model projectModel) throws IOException {
        String configFileName = BuildProperties.projectArtifactId() + ".xml";

        {
            final File mvnDir = new File(projectModel.getProjectDirectory(), ".mvn");
            if (mvnDir.exists()) {
                logger.debug("Found config in project directory - " + mvnDir.toString());
                return new File(mvnDir, configFileName);
            }
        }

        {
            // search in parent project directories
            Model parentModel = getParentModel(projectModel);
            while (parentModel != null) {
                final File mvnDir = new File(parentModel.getProjectDirectory(), ".mvn");
                if (mvnDir.exists()) {
                    logger.debug("Found config in parent project hierarchy - " + mvnDir.toString());
                    return new File(mvnDir, configFileName);
                }
                parentModel = getParentModel(parentModel);
            }
        }

        {
            // search in git directories
            File parentDir = projectModel.getProjectDirectory().getParentFile();
            while (parentDir.getCanonicalPath().startsWith(gitRepositoryDirectory.getCanonicalPath())) {
                File mvnDir = new File(parentDir, ".mvn");
                if (mvnDir.exists()) {
                    logger.debug("Found config in git directory hierarchy - " + mvnDir.toString());
                    return new File(mvnDir, configFileName);
                }
                parentDir = parentDir.getParentFile();
            }
        }

        throw new FileNotFoundException("Could not find config file");
    }

    private void addBuildPlugin(Model model, boolean updatePomOption) {
        logger.debug(model.getArtifactId() + " temporary add build plugin");

        Plugin plugin = asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(GOAL);
        execution.getGoals().add(GOAL);
        plugin.getExecutions().add(execution);

        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }
        model.getBuild().getPlugins().add(plugin);

        // set plugin properties
        model.getProperties().setProperty(propertyKeyPrefix + propertyKeyUpdatePom, Boolean.toString(updatePomOption));
    }

    /**
     * checks if <code>pomFile</code> is part of a project
     *
     * @param pomFile the pom file
     * @return true if <code>pomFile</code> is part of a project
     */
    private boolean isRelatedProject(File pomFile) throws IOException {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repositories ends in .pom
                && pomFile.getName().endsWith(".xml")
                // only pom files within git directory are treated as project pom files
                && pomFile.getCanonicalPath().startsWith(gitRepositoryDirectory.getCanonicalPath() + File.separator);
    }

    private static File getRepositoryRootDirectory(File directory) {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(directory);
        if (repositoryBuilder.getGitDir() == null) {
            return null;
        }
        return repositoryBuilder.getGitDir().getParentFile();
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

    private Configuration loadConfig(Model projectModel) throws IOException {
        File configFile = findConfigFile(projectModel);
        logger.debug("load config from " + configFile);
        return unchecked(() -> new XmlMapper().readValue(configFile, Configuration.class));
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

    private String extensionLogFormat(String extensionId) {
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
