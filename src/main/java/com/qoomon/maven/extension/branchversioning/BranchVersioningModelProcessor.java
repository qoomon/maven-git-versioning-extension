package com.qoomon.maven.extension.branchversioning;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.GAV;
import com.qoomon.maven.ModelUtil;
import com.qoomon.maven.extension.branchversioning.config.BranchVersioningConfiguration;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    private static final String DEFAULT_BRANCH_VERSION_FORMAT = "${branchName}-SNAPSHOT";

    @Requirement
    private Logger logger;

    @Requirement
    private BranchVersioningConfiguration configuration;

    @Requirement
    private SessionScope sessionScope;

    private boolean init = false;

    private Cache<GAV, BranchVersion> branchVersionMap = CacheBuilder.newBuilder().build();

    public BranchVersioningModelProcessor() {
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    private Model provisionModel(Model model, Map<String, ?> options) throws IOException {

        MavenSession session = SessionUtil.getMavenSession(sessionScope);
        if (session == null) {
            return model;
        }

        if (configuration.isDisabled()) {
            logger.info("Disabled.");
            return model;
        }

        // initial tasks
        if (!init) {
            logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");
            init = true;
        }

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            return model;
        }

        GAV projectGav = GAV.of(model);

        // check if belongs to project
        if (isProjectModule(session, pomFile)) {

            // check for top level project
            if (isExecutionRoot(session, pomFile)) {
                logger.debug("executionRoot Processor: " + pomFile);
                addBranchVersioningBuildPlugin(model); // has to be removed from model by plugin itself
            }

            // update project version to branch version for current maven session
            if (model.getParent() != null) {

                GAV parentGav = GAV.of(model.getParent());

                File parentPomFile = new File(pomFile.getParentFile(), model.getParent().getRelativePath());
                if (parentPomFile.exists()) {
                    Model parentModel = ModelUtil.readModel(parentPomFile);
                    GAV parentModelGav = GAV.of(parentModel);
                    if (parentModelGav.equals(parentGav)) {
                        BranchVersion parentBranchVersion = deduceBranchVersion(parentGav, parentPomFile.getParentFile());
                        logger.debug(projectGav + " adjust parent version to " + parentBranchVersion);
                        model.getParent().setVersion(parentBranchVersion.get());
                    }
                }
            }

            // always set version
            if (model.getVersion() != null) {
                BranchVersion branchVersion = deduceBranchVersion(projectGav, pomFile.getParentFile());
                logger.debug(projectGav + " temporary override version with " + branchVersion);
                model.setVersion(branchVersion.get());

                model.addProperty("git.branchName", branchVersion.getBranchName());
                model.addProperty("git.commitHash", branchVersion.getCommitHash());
            }

        } else {
            // skip unrelated models
            logger.debug(projectGav + " skipping unrelated model - source" + pomFile);
        }

        return model;
    }

    private boolean isProjectModule(MavenSession session, File pomFile) throws IOException {
        File rootProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
        return pomFile.getParentFile().getCanonicalPath()
                .startsWith(rootProjectDirectory.getCanonicalPath());
    }

    private boolean isExecutionRoot(MavenSession session, File pomFile) throws IOException {
        return pomFile.getCanonicalPath()
                .equals(session.getRequest().getPom().getCanonicalPath());
    }


    private void addBranchVersioningBuildPlugin(Model model) {
        GAV projectGav = GAV.of(model);
        logger.debug(projectGav + " temporary add build plugin");
        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }

        Plugin projectPlugin = BranchVersioningPomReplacementMojo.asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(BranchVersioningPomReplacementMojo.GOAL);
        execution.getGoals().add(BranchVersioningPomReplacementMojo.GOAL);
        projectPlugin.getExecutions().add(execution);

        model.getBuild().getPlugins().add(projectPlugin);
    }


    private BranchVersion deduceBranchVersion(GAV gav, File gitDir) {
        try {
            return branchVersionMap.get(gav, () -> {
                FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
                logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

                try (Repository repository = repositoryBuilder.build()) {
                    final ObjectId head = repository.resolve(Constants.HEAD);
                    final String commitHash = head.getName();
                    final boolean detachedHead = repository.getBranch().equals(commitHash);
                    final String branchName = !detachedHead ? repository.getBranch() : "(HEAD detached at " + commitHash.substring(0, 7) + ")";

                    String branchVersion;
                    // Detached HEAD
                    if (detachedHead) {
                        branchVersion = commitHash;
                    } else {
                        Map<String, String> branchVersioningDataMap = new HashMap<>();
                        branchVersioningDataMap.put("commitHash", commitHash);
                        branchVersioningDataMap.put("branchName", branchName);
                        branchVersioningDataMap.put("pomVersion", gav.getVersion());
                        branchVersioningDataMap.put("pomReleaseVersion", gav.getVersion().replaceFirst("-SNAPSHOT$", ""));

                        // find version format for branch
                        String versionFormat = configuration.getBranchVersionFormatMap().entrySet().stream()
                                .filter(entry -> entry.getKey().matcher(branchName).find())
                                .findFirst()
                                .map(Map.Entry::getValue)
                                .orElse(DEFAULT_BRANCH_VERSION_FORMAT);
                        branchVersion = StrSubstitutor.replace(versionFormat, branchVersioningDataMap);
                    }

                    logger.info(gav.getArtifactId()
                            + ":" + gav.getVersion()
                            + " - branch: " + branchName
                            + " - version: " + branchVersion
                    );
                    return new BranchVersion(branchVersion, commitHash, branchName);
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public class BranchVersion {
        final String value;
        final String commitHash;
        final String branchName;

        public BranchVersion(String value, String commitHash, String branchName) {
            this.value = value;
            this.commitHash = commitHash;
            this.branchName = branchName;
        }

        public String get() {
            return value;
        }

        public String getCommitHash() {
            return commitHash;
        }

        public String getBranchName() {
            return branchName;
        }
    }


}
