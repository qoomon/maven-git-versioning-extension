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
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.stream.Stream;

import static com.qoomon.maven.extension.branchversioning.SessionScopeUtil.*;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    private Logger logger;

    private SessionScope sessionScope;

    private BranchVersioningConfigurationProvider configurationProvider;


    private static final String PROJECT_BRANCH_PROPERTY_KEY = "project.branch";

    private static final String PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_BRANCH";

    // can not be injected cause it is not always available
    private MavenSession mavenSession;

    private BranchVersioningConfiguration configuration;

    private boolean initialized = false;

    private boolean disabled = false;


    @Inject
    public BranchVersioningModelProcessor(Logger logger, SessionScope sessionScope, BranchVersioningConfigurationProvider configurationProvider) {
        this.logger = logger;
        this.sessionScope = sessionScope;
        this.configurationProvider = configurationProvider;
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

        try {

            // ---------------- initialize ----------------

            if (!initialized) {
                initialize();
                initialized = true;
            }

            if (disabled) {
                return model;
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

            // deduce getVersion
            ProjectVersion projectVersion = deduceBranchVersion(projectGav, pomFile.getParentFile());

            // add properties
            model.addProperty("project.branch", projectVersion.getBranch());
            model.addProperty("project.commit", projectVersion.getCommit());

            // update project getVersion
            if (model.getVersion() != null) {
                logger.debug(projectGav + " temporary override getVersion with " + projectVersion);
                model.setVersion(projectVersion.getVersion());
            }

            // update parent getVersion
            if (model.getParent() != null) {
                File parentPomFile = new File(pomFile.getParentFile(), model.getParent().getRelativePath());
                GAV parentGav = GAV.of(model.getParent());
                if (parentPomFile.exists() && isProjectPom(parentPomFile)) {
                    // check if parent pom file match project parent
                    Model parentModel = ModelUtil.readModel(parentPomFile);
                    GAV parentProjectGav = GAV.of(parentModel);
                    if (parentProjectGav.equals(parentGav)) {
                        ProjectVersion parentProjectVersion = deduceBranchVersion(parentGav, parentPomFile.getParentFile());
                        logger.debug(projectGav + " adjust parent getVersion to " + parentProjectVersion);
                        model.getParent().setVersion(parentProjectVersion.getVersion());
                    }
                }
            }

            // add plugin
            addBranchVersioningBuildPlugin(model); // has to be removed from model by plugin itself

            return model;
        } catch (Exception e) {
            throw new IOException("Branch Versioning Model Processor", e);
        }
    }

    private void initialize() throws Exception {
        logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

        Optional<MavenSession> mavenSessionOptional = get(sessionScope, MavenSession.class);
        if (!mavenSessionOptional.isPresent()) {
            logger.warn("Skip provisioning. No MavenSession present.");
            disabled = true;
        } else {
            mavenSession = mavenSessionOptional.get();

            //  check if extension is disabled
            String versionBranch = mavenSession.getUserProperties().getProperty(PROJECT_BRANCH_PROPERTY_KEY);
            if ("disable".equals(versionBranch)) {
                logger.info("Disabled.");
                disabled = true;
            }

            if (!disabled) {
                this.configuration = configurationProvider.get();
            }
        }
    }

    private boolean isProjectPom(File pomFile) {
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


    private ProjectVersion deduceBranchVersion(GAV gav, File gitDir) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

        try (Repository repository = repositoryBuilder.build()) {
            boolean noHead = repository.resolve(Constants.HEAD) == null;
            boolean detachedHead = ObjectId.isId(repository.getFullBranch());

            final String commit;
            final String branch;

            if (noHead) {
                commit = "0000000000000000000000000000000000000000";
                branch = "master";
            } else if (detachedHead) {
                commit = repository.resolve(Constants.HEAD).getName();
                branch = Stream.of(
                        mavenSession.getUserProperties().getProperty(PROJECT_BRANCH_PROPERTY_KEY),
                        System.getenv(PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME))
                        .filter(Objects::nonNull).findFirst()
                        .orElseThrow(() ->
                                new ModelParseException(gitDir + ": No Branch Name provided in Detached HEAD state. See documentation.", 0, 0));
            } else {
                commit = repository.resolve(Constants.HEAD).getName();
                branch = repository.getBranch();
            }

            Map<String, String> branchVersioningDataMap = new HashMap<>();
            branchVersioningDataMap.put("commit", commit);
            branchVersioningDataMap.put("commit.short", commit.substring(0, 7));
            branchVersioningDataMap.put("branch", branch.replace("/", "-"));
            branchVersioningDataMap.put("version", gav.getVersion());
            branchVersioningDataMap.put("version.release", gav.getVersion().replaceFirst("-SNAPSHOT$", ""));

            // find getVersion format for branch
            String versionFormat = configuration.getBranchVersionFormatMap().entrySet().stream()
                    .filter(entry -> entry.getKey().matcher(branch).find())
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElseThrow(() -> new ModelParseException(gitDir + ": No version format for branch '" + branch + "' found.", 0, 0));
            String branchVersion = StrSubstitutor.replace(versionFormat, branchVersioningDataMap);


            logger.info(gav.getArtifactId()
                    + ":" + gav.getVersion()
                    + " - branch: " + branch
                    + " - branch-version: " + branchVersion);

            return new ProjectVersion(branchVersion, commit, branch);
        }
    }

    public class ProjectVersion {
        final String value;
        final String commit;
        final String branch;

        public ProjectVersion(String version, String commit, String branch) {
            this.value = version;
            this.commit = commit;
            this.branch = branch;
        }

        public String getVersion() {
            return value;
        }

        public String getCommit() {
            return commit;
        }

        public String getBranch() {
            return branch;
        }
    }


}
