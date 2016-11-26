package com.qoomon.maven.extension.branchversioning;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.qoomon.maven.extension.branchversioning.config.Configuration;
import com.qoomon.maven.extension.branchversioning.config.VersionFormat;
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
import java.util.Set;
import java.util.regex.Pattern;


/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
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
        branchVersionFormatMap.put(Pattern.compile("^support/"), "${branchName}-${pomReleaseVersion}");
        branchVersionFormatMap.put(Pattern.compile("^support-"), "${branchName}-${pomReleaseVersion}");
    }

    @Requirement
    private Logger logger;

    @Requirement
    private SessionScope sessionScope;

    private boolean init = false;

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

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File rootProjectDirectory = getMavenSession().getRequest().getMultiModuleProjectDirectory();
        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            return model;
        }
        GAV projectGav = GAV.of(model);

        // init processor
        if (!init) {
            File configFile = new File(rootProjectDirectory, ".mvn/maven-branch-versioning-extension.configuration.xml");
            if (configFile.exists()) {
                branchVersionFormatMap = loadBranchVersionFormatMap(configFile);
            }
            disableExtension = Boolean.valueOf(getMavenSession().getUserProperties().getProperty(DISABLE_BRANCH_VERSIONING_PROPERTY_KEY, "false"));
        }
        init = true;

        if (disableExtension) {
            logger.info("Disabled.");
            return model;
        }

        // check if belongs to project
        if (pomFile.getParentFile().getCanonicalPath().startsWith(rootProjectDirectory.getCanonicalPath())) {
            logger.error("ExecutionRootDirectory: " + getMavenSession().getExecutionRootDirectory());
            logger.error("MultiModuleProjectDirectory: " + getMavenSession().getRequest().getMultiModuleProjectDirectory());
            logger.error("getBaseDirectory: " + getMavenSession().getRequest().getBaseDirectory());
            logger.error("pom: " + getMavenSession().getRequest().getPom());
            logger.error("pomFile: " + pomFile);

            // check for top level project
            if (pomFile.getParentFile().getCanonicalPath().equals(rootProjectDirectory.getCanonicalPath())) {
                addBranchVersioningBuildPlugin(model); // has to be removed from model by plugin itself
            }

            String branchVersion = getBranchVersion(projectGav, pomFile.getParentFile());

            // update project version to branch version for current maven session
            if (model.getParent() != null) {
                GAV parentProjectGav = GAV.of(model.getParent());
                if (hasBranchVersion(parentProjectGav)) {
                    String parentBranchVersion = getBranchVersion(parentProjectGav);
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
            final ObjectId head = repository.resolve(Constants.HEAD);
            final String commitHash = head.getName();
            final String branchName = repository.getBranch();
            final boolean detachedHead = branchName.equals(commitHash);

            String branchVersion;
            // Detached HEAD
            if (detachedHead) {
                branchVersion = commitHash;
            } else {
                Map<String, String> brnachVersioningDataMap = new HashMap<>();
                brnachVersioningDataMap.put("commitHash", commitHash);
                brnachVersioningDataMap.put("branchName", branchName);
                brnachVersioningDataMap.put("pomVersion", gav.getVersion());
                brnachVersioningDataMap.put("pomReleaseVersion", gav.getVersion().replaceFirst("-SNAPSHOT$", ""));

                // find version format for branch
                String versionFormat = branchVersionFormatMap.entrySet().stream()
                        .filter(entry -> entry.getKey().matcher(branchName).find())
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .orElse(DEFAULT_BRANCH_VERSION_FORMAT);
                branchVersion = StrSubstitutor.replace(versionFormat, brnachVersioningDataMap);
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


    public LinkedHashMap<Pattern, String> loadBranchVersionFormatMap(File configFile) {
        LinkedHashMap<Pattern, String> branchVersionFormatMap = Maps.newLinkedHashMap();

        try (FileInputStream configFileInputStream = new FileInputStream(configFile)) {
            Unmarshaller unmarshaller = JAXBContext.newInstance(Configuration.class).createUnmarshaller();
            Configuration configuration = (Configuration) unmarshaller.unmarshal(configFileInputStream);
            for (VersionFormat versionFormat : configuration.versionFormats) {
                logger.debug("branchVersionFormat: " + versionFormat.branchPattern + " -> " + versionFormat.versionFormat);
                branchVersionFormatMap.put(
                        Pattern.compile(versionFormat.branchPattern),
                        versionFormat.versionFormat
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return branchVersionFormatMap;
    }
}
