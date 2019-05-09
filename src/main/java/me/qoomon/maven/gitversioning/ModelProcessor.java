package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import me.qoomon.gitversioning.*;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.maven.gitversioning.MavenUtil.isProjectPom;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static me.qoomon.maven.gitversioning.VersioningMojo.*;

/**
 * Replacement for {@link org.apache.maven.model.building.ModelProcessor} to adapt versions.
 */
@Component(role = org.apache.maven.model.building.ModelProcessor.class)
public class ModelProcessor extends DefaultModelProcessor {

    private final Logger logger;

    private final SessionScope sessionScope;

    private boolean initialized = false;

    private MavenSession mavenSession;  // can not be injected cause it is not always available

    private Configuration config;
    private GitVersionDetails gitVersionDetails;

    private final Map<String, Model> virtualProjectModelCache = new HashMap<>();

    @Inject
    public ModelProcessor(final Logger logger, final SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

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

    private Model processModel(Model projectModel, Map<String, ?> options) throws IOException {
        try {
            if (!initialized) {
                logger.info("");
                logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

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

        Model virtualProjectModel = this.virtualProjectModelCache.get(projectModel.getArtifactId());
        if (virtualProjectModel == null) {
            logger.info(projectGav.getArtifactId() + " - set project version to " + gitVersionDetails.getVersion()
                    + " (" + gitVersionDetails.getCommitRefType() + ":" + gitVersionDetails.getCommitRefName() + ")");

            virtualProjectModel = projectModel.clone();

            // ---------------- process project -----------------------------------

            if (projectModel.getVersion() != null) {
                logger.debug(" replace project version");
                virtualProjectModel.setVersion(gitVersionDetails.getVersion());
            }

            virtualProjectModel.addProperty("git.commit", gitVersionDetails.getCommit());
            virtualProjectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());
            for (Map.Entry<String, String> entry : gitVersionDetails.getMetaData().entrySet()) {
                virtualProjectModel.addProperty("git.ref." + entry.getKey(), entry.getValue());
            }

            // ---------------- process parent -----------------------------------

            final Parent parent = projectModel.getParent();
            if (parent != null) {

                if (parent.getVersion() == null) {
                    logger.warn("skip - invalid model - parent 'version' is missing - " + projectModel.getPomFile());
                    return projectModel;
                }

                File parentPomFile = getParentPom(projectModel);
                if (isProjectPom(parentPomFile)) {
                    if (projectModel.getVersion() != null) {
                        virtualProjectModel.setVersion(null);
                        logger.warn("Do not set version tag in a multi module project module: " + projectModel.getPomFile());
                        if (!projectModel.getVersion().equals(parent.getVersion())) {
                            throw new IllegalStateException("'version' has to be equal to parent 'version'");
                        }
                    }

                    logger.debug(" replace parent version");
                    virtualProjectModel.getParent().setVersion(gitVersionDetails.getVersion());
                }
            }

            // ---------------- add plugin ---------------------------------------

            boolean updatePomOption = getUpdatePomOption(config, gitVersionDetails);
            addBuildPlugin(virtualProjectModel, updatePomOption);

            this.virtualProjectModelCache.put(projectModel.getArtifactId(), virtualProjectModel);
        }
        return virtualProjectModel;
    }

    private GitVersionDetails getGitVersionDetails(Configuration config, Model projectModel) {
        GitRepoSituation repoSituation = GitUtil.situation(projectModel.getPomFile());
        String providedBranch = getOption("git.branch");
        if (providedBranch != null) {
            repoSituation.setHeadBranch(providedBranch.isEmpty() ? null : providedBranch);
        }
        String providedTag = getOption("git.tag");
        if (providedTag != null) {
            repoSituation.setHeadTags(providedTag.isEmpty() ? emptyList() : singletonList(providedTag));
        }

        return GitVersioning.determineVersion(repoSituation,
                ofNullable(config.commit)
                        .map(it -> new VersionDescription(null, it.versionFormat))
                        .orElse(new VersionDescription()),
                config.branch.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat))
                        .collect(toList()),
                config.tag.stream()
                        .map(it -> new VersionDescription(it.pattern, it.versionFormat))
                        .collect(toList()),
                GAV.of(projectModel).getVersion());
    }

    private Model getParentModel(Model projectModel) {
        return unchecked(() -> {
            File parentFile = getParentPom(projectModel);
            return parentFile != null ? readModel(parentFile) : null;
        });
    }

    private File getParentPom(Model projectModel) {
        if (projectModel.getParent() == null) {
            return null;
        }

        File parentPom = new File(projectModel.getProjectDirectory(), projectModel.getParent().getRelativePath());
        if (parentPom.isDirectory()) {
            parentPom = new File(parentPom, "pom.xml");
        }
        return parentPom;
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
            value = System.getenv("VERSIONING_" + name.replaceAll("\\.", "_").toUpperCase());
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
}
