package me.qoomon.maven.gitversioning;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.maven.gitversioning.MavenUtil.isProjectPom;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static me.qoomon.maven.gitversioning.VersioningMojo.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.VersioningMojo.GOAL;
import static me.qoomon.maven.gitversioning.VersioningMojo.asPlugin;
import static me.qoomon.maven.gitversioning.VersioningMojo.propertyKeyPrefix;
import static me.qoomon.maven.gitversioning.VersioningMojo.propertyKeyUpdatePom;
import static org.apache.maven.shared.utils.StringUtils.repeat;
import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
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
 * Initialize and use {@link ModelProcessor} from GitModelProcessor {@link org.apache.maven.model.building.ModelProcessor},
 * This is need because maven 3.6.2 has broken component replacement mechanism.
 */
public class ModelProcessor {
    private static final String OPTION_NAME_DISABLE = "versioning.disable";
    private static final String OPTION_NAME_GIT_TAG = "git.tag";
    private static final String OPTION_NAME_GIT_BRANCH = "git.branch";

    @Inject
    private Logger logger;

    @Inject
    private SessionScope sessionScope;

    private boolean initialized = false;

    private MavenSession mavenSession;  // can not be injected cause it is not always available

    private Configuration config;
    private GitVersionDetails gitVersionDetails;

    private final Map<String, Model> virtualProjectModelCache = new HashMap<>();

    public Model processModel(Model projectModel, Map<String, ?> options) throws IOException {
        try {
            if (!initialized) {
                logger.info("");
                String extensionId = BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion();
                logger.info(extensionLogFormat(extensionId));
                logger.info("Adjusting project models...");
                logger.info("");
                try {
                    mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
                } catch (OutOfScopeException ex) {
                    mavenSession = null;
                }

                initialized = true;
            }

            if (mavenSession == null) {
                logger.warn("skip - no maven session present");
                return projectModel;
            }

            if (parseBoolean(getOption(OPTION_NAME_DISABLE))) {
                logger.warn("skip - versioning is disabled");
                return projectModel;
            }

            final Source pomSource = (Source) options.get(org.apache.maven.model.building.ModelProcessor.SOURCE);
            if (pomSource != null) {
                projectModel.setPomFile(new File(pomSource.getLocation()));
            }

            return processModel(projectModel);
        } catch (Exception e) {
            throw new IOException("Git Versioning Model Processor", e);
        }
    }

    private Model processModel(Model projectModel) {
        if (!isProjectPom(projectModel.getPomFile())) {
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
            File mvnDir = findMvnDir(projectModel);
            File configFile = new File(mvnDir, BuildProperties.projectArtifactId() + ".xml");
            config = loadConfig(configFile);
        }

        if (gitVersionDetails == null) {
            gitVersionDetails = getGitVersionDetails(config, projectModel);
        }

        String projectId = projectGav.getGroupId() + ":" + projectGav.getArtifactId();
        Model virtualProjectModel = this.virtualProjectModelCache.get(projectId);
        if (virtualProjectModel == null) {
            // ---------------- process project -----------------------------------
            logger.info(buffer().strong("--- ") + buffer().project(projectId).toString() + " @ " + gitVersionDetails.getCommitRefType() + " " + buffer().strong(gitVersionDetails.getCommitRefName()) + buffer().strong(" ---"));

            virtualProjectModel = projectModel.clone();
            final String projectVersion = GAV.of(projectModel).getVersion();

            if (virtualProjectModel.getVersion() != null) {
                final String gitVersion = gitVersionDetails.getVersionTransformer().apply(projectVersion);
                logger.info("project version: " + buffer().strong(gitVersion));
                virtualProjectModel.setVersion(gitVersion);
            }

            final Map<String, String> gitProperties = gitVersionDetails.getPropertiesTransformer().apply(
                    Maps.fromProperties(virtualProjectModel.getProperties()), projectVersion);
            if (!gitProperties.isEmpty()) {
                logger.info("properties:");
                for (Entry<String, String> property : gitProperties.entrySet()) {
                    if (!property.getValue().equals(virtualProjectModel.getProperties().getProperty(property.getKey()))) {
                        logger.info("  " + property.getKey() + ": " + property.getValue());
                        virtualProjectModel.getProperties().setProperty(property.getKey(), property.getValue());
                    }
                }
            }
            logger.info("");

            virtualProjectModel.addProperty("git.commit", gitVersionDetails.getCommit());
            virtualProjectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());

            // ---------------- process parent -----------------------------------

            final Parent parent = projectModel.getParent();
            if (parent != null) {

                if (parent.getVersion() == null) {
                    logger.warn("skip - invalid model - parent 'version' is missing - " + projectModel.getPomFile());
                    return projectModel;
                }

                Model parentModel = getParentModel(projectModel);
                if (parentModel != null && isProjectPom(parentModel.getPomFile())) {
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

            // ---------------- add plugin ---------------------------------------

            boolean updatePomOption = getUpdatePomOption(config, gitVersionDetails);
            addBuildPlugin(virtualProjectModel, updatePomOption);

            this.virtualProjectModelCache.put(projectId, virtualProjectModel);
        }
        return virtualProjectModel;
    }

    private GitVersionDetails getGitVersionDetails(Configuration config, Model projectModel) {
        GitRepoSituation repoSituation = GitUtil.situation(projectModel.getPomFile());
        String providedTag = getOption(OPTION_NAME_GIT_TAG);
        if (providedTag != null) {
            repoSituation.setHeadBranch(null);
            repoSituation.setHeadTags(providedTag.isEmpty() ? emptyList() : singletonList(providedTag));
        }
        String providedBranch = getOption(OPTION_NAME_GIT_BRANCH);
        if (providedBranch != null) {
            repoSituation.setHeadBranch(providedBranch.isEmpty() ? null : providedBranch);
        }

        return GitVersioning.determineVersion(repoSituation,
                ofNullable(config.commit)
                        .map(it -> new VersionDescription(null, it.versionFormat, convertPropertyDescription(it.property)))
                        .orElse(new VersionDescription()),
                config.branch.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat, convertPropertyDescription(it.property)))
                        .collect(toList()),
                config.tag.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat, convertPropertyDescription(it.property)))
                        .collect(toList()));
    }

    private List<PropertyDescription> convertPropertyDescription(
            List<Configuration.PropertyDescription> confPropertyDescription) {
        return confPropertyDescription.stream()
                .map(prop -> new PropertyDescription(
                        prop.pattern, new PropertyValueDescription(prop.value.pattern, prop.value.format)))
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

    private File findMvnDir(Model projectModel) {
        {
            final File mvnDir = new File(projectModel.getProjectDirectory(), ".mvn");
            if (mvnDir.exists()) {
                logger.debug("Found .mvn directory in project directory - " + mvnDir.toString());
                return mvnDir;
            }
        }

        {
            // search in parent project directories
            Model parentModel = getParentModel(projectModel);
            while (parentModel != null && isProjectPom(parentModel.getPomFile())) {
                final File mvnDir = new File(parentModel.getProjectDirectory(), ".mvn");
                if (mvnDir.exists()) {
                    logger.debug("Found .mvn directory in parent project hierarchy - " + mvnDir.toString());
                    return mvnDir;
                }
                parentModel = getParentModel(parentModel);
            }
        }

        {
            // search in git directories
            File gitDir = new FileRepositoryBuilder().findGitDir(projectModel.getProjectDirectory()).getGitDir();
            File parentDir = projectModel.getProjectDirectory().getParentFile();
            while (!parentDir.equals(gitDir)) {
                File mvnDir = new File(parentDir, ".mvn");
                if (mvnDir.exists()) {
                    logger.debug("Found .mvn directory in git directory hierarchy - " + mvnDir.toString());
                    return mvnDir;
                }
                parentDir = parentDir.getParentFile();
            }
        }

        logger.warn("Could not find .mvn directory!");
        return null;
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

    private String getOption(final String name) {
        String value = mavenSession.getUserProperties().getProperty(name);
        if (value == null) {
            String environmentVariableName = "VERSIONING_" + name
                    .replaceFirst("^versioning\\.", "")
                    .replaceAll("\\.", "_")
                    .toUpperCase();
            value = System.getenv(environmentVariableName);
        }
        return value;
    }

    private Configuration loadConfig(File configFile) {
        if (!configFile.exists()) {
            return new Configuration();
        }
        logger.debug("load config from " + configFile);
        return unchecked(() -> new XmlMapper().readValue(configFile, Configuration.class));
    }

    private boolean getUpdatePomOption(final Configuration config, final GitVersionDetails gitVersionDetails) {
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
}
