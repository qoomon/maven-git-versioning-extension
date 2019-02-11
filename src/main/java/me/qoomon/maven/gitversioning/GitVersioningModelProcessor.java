package me.qoomon.maven.gitversioning;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import me.qoomon.gitversioning.*;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class GitVersioningModelProcessor extends DefaultModelProcessor {

    private final Logger logger;
    // for preventing unnecessary logging
    private final Set<String> loggingBouncer = new HashSet<>();

    private final SessionScope sessionScope;

    private MavenSession mavenSession;  // can not be injected cause it is not always available

    private GitVersionDetails gitVersionDetails;

    private boolean initialized = false;


    @Inject
    public GitVersioningModelProcessor(final Logger logger, final SessionScope sessionScope) {
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
                    // ignore
                }
                initialized = true;
            }

            if (mavenSession == null) {
                logger.warn("skip - no maven session present");
                return projectModel;
            }

            final Source pomSource = (Source) options.get(ModelProcessor.SOURCE);
            if (pomSource == null) {
                logger.debug("skip - unknown pom source");
                return projectModel;
            }

            final File projectPomFile = new File(pomSource.getLocation());
            if (!isProjectPom(projectPomFile)) {
                logger.debug("skip - unrelated pom location - " + projectPomFile);
                return projectModel;
            }

            if (projectPomFile.getName().equals(GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME)) {
                logger.debug("skip - git versioned pom - " + projectPomFile);
                return projectModel;
            }

            // ---------------- process project model ----------------------------

            final GAV projectGav = GAV.of(projectModel);
            if (projectGav.getVersion() == null) {
                logger.warn("skip - invalid model - 'version' is missing - " + projectPomFile);
                return projectModel;
            }

            // TODO extract to method
            if (gitVersionDetails == null) {

                File configFile = new File(projectPomFile.getParentFile(), ".mvn/" + BuildProperties.projectArtifactId() + ".xml");
                GitVersioningExtensionConfiguration config = loadConfig(configFile);

                GitRepoSituation repoSituation = GitUtil.situation(projectPomFile.getParentFile());
                String providedBranch = getOption(mavenSession, "git.branch");
                if (providedBranch != null) {
                    repoSituation.setHeadBranch(providedBranch.isEmpty() ? null : providedBranch);
                }
                String providedTag = getOption(mavenSession, "git.tag");
                if (providedTag != null) {
                    repoSituation.setHeadTags(providedTag.isEmpty() ? emptyList() : singletonList(providedTag));
                }

                gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                        ofNullable(config.commit)
                                .map(it -> new VersionDescription(null, it.versionFormat))
                                .orElse(new VersionDescription()),
                        config.branches.stream()
                                .map(it -> new VersionDescription(it.pattern, it.versionFormat))
                                .collect(toList()),
                        config.tags.stream()
                                .map(it -> new VersionDescription(it.pattern, it.versionFormat))
                                .collect(toList()),
                        projectModel.getVersion());
            }

            if (loggingBouncer.add(projectGav.toString())) {
                logger.info(projectGav.getArtifactId() + ":" + projectGav.getVersion()
                        + " - " + gitVersionDetails.getCommitRefType() + ": " + gitVersionDetails.getCommitRefName()
                        + " -> version: " + gitVersionDetails.getVersion());
            }

            final Model virtualProjectModel = projectModel.clone();
            if (projectModel.getVersion() != null) {
                virtualProjectModel.setVersion(gitVersionDetails.getVersion());
            }

            virtualProjectModel.addProperty("git.commit", gitVersionDetails.getCommit());
            virtualProjectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());
            gitVersionDetails.getMetaData().forEach((key, value) -> virtualProjectModel.addProperty("git.ref." + key, value));


            // ---------------- process parent -----------------------------------

            final Parent parent = projectModel.getParent();
            if (parent != null) {

                GAV parentGav = GAV.of(parent);
                if (parentGav.getVersion() == null) {
                    logger.warn("skip - invalid model - parent 'version' is missing - " + projectPomFile);
                    return projectModel;
                }

                File parentPomFile = new File(projectPomFile.getParentFile(), parent.getRelativePath());
                if (isProjectPom(parentPomFile)) {
                    if (projectModel.getVersion() != null) {
                        logger.warn("Do not set version tag in a multi module project module: " + projectPomFile);
                        if (!projectModel.getVersion().equals(parent.getVersion())) {
                            throw new IllegalStateException("'version' has to be equal to parent 'version'");
                        }
                    }

                    virtualProjectModel.getParent().setVersion(gitVersionDetails.getVersion());
                }
            }

            // ---------------- add plugin ---------------------------------------

            addBuildPlugin(virtualProjectModel); // has to be removed from model by plugin itself

            return virtualProjectModel;
        } catch (Exception e) {
            throw new IOException("Git Versioning Model Processor", e);
        }
    }

    /**
     * checks if <code>pomFile</code> is part of a project
     *
     * @param pomFile the pom file
     * @return true if <code>pomFile</code> is part of a project
     */
    private static boolean isProjectPom(File pomFile) {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repositories ends in .pom
                && pomFile.getName().endsWith(".xml");
    }

    private void addBuildPlugin(Model model) {
        GAV projectGav = GAV.of(model);
        logger.debug(projectGav + " temporary add build plugin");

        Plugin projectPlugin = GitVersioningPomReplacementMojo.asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(GitVersioningPomReplacementMojo.GOAL);
        execution.getGoals().add(GitVersioningPomReplacementMojo.GOAL);
        projectPlugin.getExecutions().add(execution);

        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }
        model.getBuild().getPlugins().add(projectPlugin);
    }

    private String getOption(final MavenSession session, final String name) {
        String value = session.getUserProperties().getProperty(name);
        if (value == null) {
            value = System.getenv("VERSIONING_" + name.replaceAll("[A-Z]", "_$0").toUpperCase());
        }
        return value;
    }

    private GitVersioningExtensionConfiguration loadConfig(File configFile) {
        if (!configFile.exists()) {
            return new GitVersioningExtensionConfiguration();
        }
        logger.debug("load config from " + configFile);
        Serializer serializer = new Persister();
        return unchecked(() -> serializer.read(GitVersioningExtensionConfiguration.class, configFile));
    }
}
