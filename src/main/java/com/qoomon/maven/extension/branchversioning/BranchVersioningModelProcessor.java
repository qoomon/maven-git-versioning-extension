package com.qoomon.maven.extension.branchversioning;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
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
import java.util.Map;
import java.util.Set;


/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    /**
     * Settings
     */

    private String mainReleaseBranch = "master";

    private Set<String> releaseBranchPrefixSet = Sets.newHashSet("support-", "support/");

    public static final String RELEASE_BRANCH_PROFILE_NAME = "release-branch";

    /**
     * Options
     */

    public static final String DISABLE_BRANCH_VERSIONING_PROPERTY_KEY = "disableBranchVersioning";

    @Requirement
    private Logger logger;

    @Requirement
    private SessionScope sessionScope;

    private Map<GAV, String> branchVersionMap = Maps.newHashMap();

    public BranchVersioningModelProcessor() {
        super();
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

        Boolean disableExtension = Boolean.valueOf(getMavenSession().getUserProperties().getProperty(DISABLE_BRANCH_VERSIONING_PROPERTY_KEY, "false"));
        if (disableExtension) {
            logger.info("Disabled.");
            return model;
        }

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            return model;
        }

        File requestPomFile = getMavenSession().getRequest().getPom();

        // check for top level project
        if (pomFile.equals(requestPomFile)) {
            addBranchVersioningBuildPlugin(model); // has to be removed from model by plugin itself
        }

        GAV projectGav = GAV.of(model);

        if (pomFile.getParentFile().getCanonicalPath()
                .startsWith(requestPomFile.getParentFile().getCanonicalPath())) {

            String branchVersion = getBranchVersion(projectGav, requestPomFile.getParentFile());

            // update project version to branch version for current maven session
            if (model.getParent() != null) {
                GAV parentProjectGav = GAV.of(model.getParent());
                if (hasBranchVersion(parentProjectGav)) {
                    String parentBranchVersion = this.getBranchVersion(parentProjectGav);
                    logger.debug(projectGav + " adjust parent version to " + parentBranchVersion);
                    model.getParent().setVersion(parentBranchVersion);
                }
            }

            if (model.getVersion() != null) {
                logger.debug(projectGav + " temporary override version with " + branchVersion);
                model.setVersion(branchVersion);
            }

        } else {
            // skip unrelated models
            logger.debug(projectGav + " skipping unrelated model - source" + pomFile);
        }

        return model;
    }

    private MavenSession getMavenSession() {
        return sessionScope.scope(Key.get(MavenSession.class), null).get();
    }

    private void addBranchVersioningBuildPlugin(Model model) {
        GAV projectGav = GAV.of(model);
        logger.debug(projectGav + " temporary add build plugin");
        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }

        Plugin projectPlugin = ExtensionUtil.projectPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(BranchVersioningTempPomUpdateMojo.GOAL);
        execution.getGoals().add(BranchVersioningTempPomUpdateMojo.GOAL);
        execution.setPhase("verify");
        projectPlugin.getExecutions().add(execution);

        model.getBuild().getPlugins().add(projectPlugin);
    }


    public String getBranchVersion(GAV gav, File gitDir) {
        if (!branchVersionMap.containsKey(gav)) {
            String version = deduceBranchVersion(gav, gitDir);
            branchVersionMap.put(gav, version);
        }
        return getBranchVersion(gav);
    }

    public String getBranchVersion(GAV gav) {
        if (!hasBranchVersion(gav)) {
            throw new IllegalStateException(gav + " no branch version available");
        }
        return branchVersionMap.get(gav);
    }

    public String deduceBranchVersion(GAV gav, File gitDir) {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        logger.debug("git directory " + repositoryBuilder.getGitDir());

         try (Repository repository = repositoryBuilder.build()) {
            ObjectId head = repository.resolve(Constants.HEAD);
            String commitHash = head.getName();
            String branchName = repository.getBranch();
            boolean detachedHead = branchName.equals(commitHash);
            if (detachedHead) {
                branchName = "(HEAD detached at " + commitHash + ")";
            }

            String branchVersion;
            // Detached HEAD
            if (detachedHead) {
                branchVersion = commitHash;
            }
            // Main Branch
            else if (mainReleaseBranch.equalsIgnoreCase(branchName)) {
                branchVersion = gav.getVersion().replaceFirst("-SNAPSHOT$", "");
            }
            // Release Branches
            else if (releaseBranchPrefixSet.stream().anyMatch(branchName::startsWith)) {
                branchVersion = branchName + "-" + gav.getVersion().replaceFirst("-SNAPSHOT$", "");
            }
            // Snapshot Branches
            else {
                branchVersion = branchName + "-SNAPSHOT";
            }

            logger.info(gav + " Branch: '" + branchName + "' -> Branch Version: '" + branchVersion + "'");

            return branchVersion;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasBranchVersion(GAV gav) {
        return branchVersionMap.containsKey(gav);
    }

}
