package com.qoomon.maven.extension.branchversioning;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.qoomon.maven.GAV;
import com.qoomon.maven.ModelUtil;
import com.qoomon.maven.extension.branchversioning.config.Configuration;
import com.qoomon.maven.extension.branchversioning.config.BranchVersionDescription;
import com.qoomon.maven.BuildProperties;
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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    private static final String DISABLE_BRANCH_VERSIONING_PROPERTY_KEY = "disableBranchVersioning";

    private static final String DEFAULT_BRANCH_VERSION_FORMAT = "${branchName}-SNAPSHOT";

    /**
     * Settings
     */
    private Boolean disableExtension = false;

    private LinkedHashMap<Pattern, String> branchVersionFormatMap = Maps.newLinkedHashMap();

    { // default
        branchVersionFormatMap.put(Pattern.compile("^master$"), "${pomReleaseVersion}");
    }

    @Requirement
    private Logger logger;

    @Requirement
    private SessionScope sessionScope;

    private boolean init = false;

    private Cache<GAV, String> branchVersionMap = CacheBuilder.newBuilder().build();

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

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            return model;
        }

        GAV projectGav = GAV.of(model);

        MavenSession session = getMavenSession();

        // init processor
        if (!init) {
            logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

            File rootProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
            File configFile = new File(rootProjectDirectory, ".mvn/" + BuildProperties.projectArtifactId() + ".xml");
            logger.debug("load config from " + configFile);
            if (configFile.exists()) {
                branchVersionFormatMap = loadBranchVersionFormatMap(configFile);
            }
            disableExtension = Boolean.valueOf(session.getUserProperties().getProperty(DISABLE_BRANCH_VERSIONING_PROPERTY_KEY, "false"));
        }
        init = true;

        if (disableExtension) {
            logger.info("Disabled.");
            return model;
        }

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
                        String parentBranchVersion = deduceBranchVersion(parentGav, parentPomFile.getParentFile());
                        logger.debug(projectGav + " adjust parent version to " + parentBranchVersion);
                        model.getParent().setVersion(parentBranchVersion);
                    }
                }
            }

            // always set version
            if (model.getVersion() != null) {
                String branchVersion = deduceBranchVersion(projectGav, pomFile.getParentFile());
                logger.debug(projectGav + " temporary override version with " + branchVersion);
                model.setVersion(branchVersion);
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

    private MavenSession getMavenSession() {
        return sessionScope.scope(Key.get(MavenSession.class), null).get();
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


    private String deduceBranchVersion(GAV gav, File gitDir) {
        try {
            return branchVersionMap.get(gav, () -> {
                FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
                logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

                try (Repository repository = repositoryBuilder.build()) {
                    final ObjectId head = repository.resolve(Constants.HEAD);
                    final String commitHash = head.getName();
                    final String branchName = repository.getBranch();
                    final boolean detachedHead = branchName.equals(commitHash);

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
                        String versionFormat = branchVersionFormatMap.entrySet().stream()
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
                    return branchVersion;
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public LinkedHashMap<Pattern, String> loadBranchVersionFormatMap(File configFile) {
        LinkedHashMap<Pattern, String> branchVersionFormatMap = Maps.newLinkedHashMap();

        try (FileInputStream configFileInputStream = new FileInputStream(configFile)) {
            Unmarshaller unmarshaller = JAXBContext.newInstance(Configuration.class).createUnmarshaller();
            Configuration configuration = (Configuration) unmarshaller.unmarshal(configFileInputStream);
            for (BranchVersionDescription branchVersionDescription : configuration.branches) {
                logger.debug("branchVersionFormat: " + branchVersionDescription.branchPattern + " -> " + branchVersionDescription.versionFormat);
                branchVersionFormatMap.put(
                        Pattern.compile(branchVersionDescription.branchPattern),
                        branchVersionDescription.versionFormat
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return branchVersionFormatMap;
    }
}
