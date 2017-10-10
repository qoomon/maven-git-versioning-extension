package com.qoomon.maven.extension.branchversioning;

import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.GAV;
import com.qoomon.maven.ModelUtil;
import com.qoomon.maven.extension.branchversioning.config.BranchVersioningConfiguration;
import com.qoomon.maven.extension.branchversioning.config.BranchVersioningConfigurationProvider;
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
import java.util.*;

import static com.qoomon.maven.extension.branchversioning.SessionScopeUtil.*;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    private static final String DISABLE_VERSIONING_PROPERTY_KEY = "versioning.disable";
    private static final String BRANCH_VERSIONING_PROPERTY_KEY = "versioning.branch";

    @Requirement
    private Logger logger;

    @Requirement
    private SessionScope sessionScope;

    @Requirement
    private BranchVersioningConfigurationProvider configurationProvider;

    private boolean initialized = false;

    private boolean disabled = false;

    private BranchVersioningConfiguration configuration = null;


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

        if (disabled) {
            return model;
        }

        Optional<MavenSession> mavenSession = get(sessionScope, MavenSession.class);

        // disabled extension if no maven session is present - sometimes there is no maven session available e.g. intelliJ project import
        if (!mavenSession.isPresent()) {
            logger.warn("Skip provisioning. No MavenSession present.");
            disabled = true;
            return model;
        }

        // ---------------- initialize ----------------

        if (!initialized) {
            logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

            Properties userProperties = mavenSession.get().getUserProperties();

            String disablePropertyValue = userProperties.getProperty(DISABLE_VERSIONING_PROPERTY_KEY);
            if (disablePropertyValue != null) {
                disabled = Boolean.valueOf(disablePropertyValue);
                if (disabled) {
                    logger.info("Disabled.");
                    return model;
                }
            }

            configuration = configurationProvider.get();

            initialized = true;
        }

        // ---------------- provisioning ----------------

        Source pomSource = (Source) options.get(ModelProcessor.SOURCE);
        File pomFile = new File(pomSource != null ? pomSource.getLocation() : "");
        if (!isProjectPom(pomFile)) {
            // skip unrelated models
            logger.debug("skip unrelated model - source" + pomFile);
            return model;
        }

        GAV projectGav = GAV.of(model);

        // deduce version
        BranchVersion branchVersion = deduceBranchVersion(projectGav, pomFile.getParentFile());

        // add properties
        model.addProperty("git.branchName", branchVersion.getBranchName());
        model.addProperty("git.commitHash", branchVersion.getCommitHash());

        // update project version
        if (model.getVersion() != null) {
            logger.debug(projectGav + " temporary override version with " + branchVersion);
            model.setVersion(branchVersion.get());
        }

        // update parent version
        if (model.getParent() != null) {
            File parentPomFile = new File(pomFile.getParentFile(), model.getParent().getRelativePath());
            GAV parentGav = GAV.of(model.getParent());
            if (parentPomFile.exists() && isProjectPom(parentPomFile)) {
                // check if parent pom file match project parent
                Model parentModel = ModelUtil.readModel(parentPomFile);
                GAV parentProjectGav = GAV.of(parentModel);
                if (parentProjectGav.equals(parentGav)) {
                    BranchVersion parentBranchVersion = deduceBranchVersion(parentGav, parentPomFile.getParentFile());
                    logger.debug(projectGav + " adjust parent version to " + parentBranchVersion);
                    model.getParent().setVersion(parentBranchVersion.get());
                }
            }
        }

        // add plugin
        addBranchVersioningBuildPlugin(model); // has to be removed from model by plugin itself

        return model;
    }

    private boolean isProjectPom(File pomFile) throws IOException {
        // only project pom files ends in .xml, pom files from dependencies from repository ends in .pom
        return pomFile.isFile() && pomFile.getName().endsWith(".xml");
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


        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

        try (Repository repository = repositoryBuilder.build()) {
            final ObjectId head = repository.resolve(Constants.HEAD);
            final String commitHash = head.getName();
            final boolean detachedHead = repository.getBranch().equals(commitHash);
            final String branchName = Optional.ofNullable(System.getenv("GIT_BRANCH_NAME"))
                    .orElse(repository.getBranch());


            String branchVersion;
            if (branchName.equals(commitHash)) {
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
                        .orElseThrow(() -> new NoSuchElementException("No Version Format for '" + branchName + "' branch"));
                branchVersion = StrSubstitutor.replace(versionFormat, branchVersioningDataMap);
            }

            logger.info(gav.getArtifactId()
                    + ":" + gav.getVersion()
                    + " - branch: " + branchName
                    + " - version: " + branchVersion);

            return new BranchVersion(branchVersion.replace("/", "-"),
                    commitHash, branchName);
        } catch (IOException e) {
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
