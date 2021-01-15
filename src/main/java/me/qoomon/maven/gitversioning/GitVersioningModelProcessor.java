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

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
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
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Named( "core-default" )
@Singleton
@Typed( ModelProcessor.class )
public class GitVersioningModelProcessor extends DefaultModelProcessor {

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

    private File mvnDirectory;
    private File gitDirectory;
    private Configuration config;
    private GitVersionDetails gitVersionDetails;

    private final Set<String> sessionProjectDirectories = new HashSet<>();
    private final Map<String, Model> virtualProjectModelCache = new HashMap<>();

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

    public Model processModel(Model projectModel, Map<String, ?> options) throws IOException {
        if (this.disabled) {
            return projectModel;
        }

        final Source pomSource = (Source) options.get(ModelProcessor.SOURCE);
        if (pomSource != null) {
            projectModel.setPomFile(new File(pomSource.getLocation()));
        }

        try {
            if (!initialized) {
                logger.info("");
                String extensionId = BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion();
                logger.info(extensionLogFormat(extensionId));

                try {
                    mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
                } catch (OutOfScopeException ex) {
                    logger.warn("skip - no maven session present");
                    disabled = true;
                    return projectModel;
                }

                String commandOptionValueDisable = getCommandOption(OPTION_NAME_DISABLE);
                if(commandOptionValueDisable != null){
                    if (parseBoolean(commandOptionValueDisable)) {
                        logger.info("skip - versioning is disabled");
                        disabled = true;
                        return projectModel;
                    }
                } else {
                    String propertyOptionValueDisable = projectModel.getProperties().getProperty(OPTION_NAME_DISABLE);
                    if(propertyOptionValueDisable != null) {
                        if (parseBoolean(propertyOptionValueDisable)) {
                            disabled = true;
                            return projectModel;
                        }
                    }
                }

                File executionRootDirectory = new File(mavenSession.getRequest().getBaseDirectory());
                logger.debug("executionRootDirectory: " + executionRootDirectory.toString());

                mvnDirectory = findMvnDirectory(executionRootDirectory);
                logger.debug("mvnDirectory: " + mvnDirectory.toString());

                String configFileName = BuildProperties.projectArtifactId() + ".xml";
                File configFile = new File(mvnDirectory, configFileName);
                logger.debug("configFile: " + configFile.toString());
                config = loadConfig(configFile);

                gitDirectory = findGitDir(executionRootDirectory);
                if (gitDirectory == null || !gitDirectory.exists()) {
                    logger.warn("skip - project is not part of a git repository");
                    disabled = true;
                    return projectModel;
                }

                logger.debug("gitDirectory: " + gitDirectory.toString());

                gitVersionDetails = getGitVersionDetails(config, executionRootDirectory);

                logger.info("Adjusting project models...");
                logger.info("");
                initialized = true;
            }

            return processModel(projectModel);
        } catch (Exception e) {
            throw new IOException("Git Versioning Model Processor", e);
        }
    }

    private Model processModel(Model projectModel) throws IOException {
        if (!isRelatedPom(projectModel.getPomFile())) {
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

        if(sessionProjectDirectories.isEmpty()){
            sessionProjectDirectories.add(projectModel.getProjectDirectory().getCanonicalPath());
        }

        String projectId = projectGav.getProjectId();
        Model virtualProjectModel = this.virtualProjectModelCache.get(projectId);
        if (virtualProjectModel == null) {
            logger.info(buffer().strong("--- ") + buffer().project(projectId).toString() + " @ " + gitVersionDetails.getCommitRefType() + " " + buffer().strong(gitVersionDetails.getCommitRefName()) + buffer().strong(" ---"));

            virtualProjectModel = projectModel.clone();

            // ---------------- process parent -----------------------------------

            final Parent parent = projectModel.getParent();
            if (parent != null) {

                if (parent.getVersion() == null) {
                    logger.warn("skip - invalid model - parent 'version' is missing - " + projectModel.getPomFile());
                    return projectModel;
                }

                Model parentModel = getParentModel(projectModel);
                if (parentModel != null && isRelatedPom(parentModel.getPomFile())) {
                    if (virtualProjectModel.getVersion() != null) {
                        virtualProjectModel.setVersion(null);
                        logger.warn("Do not set version tag in a multi module project module: " + projectModel.getPomFile());
                        if (!projectModel.getVersion().equals(parent.getVersion())) {
                            throw new IllegalStateException("'version' has to be equal to parent 'version'");
                        }
                    }

                    final String parentVersion = virtualProjectModel.getParent().getVersion();
                    final String gitParentVersion = gitVersionDetails.getVersionTransformer().apply(parentVersion);
                    logger.info("parent version: " + gitParentVersion);
                    virtualProjectModel.getParent().setVersion(gitParentVersion);
                }
            }


            // ---------------- process project -----------------------------------

            if (virtualProjectModel.getVersion() != null) {
                final String projectVersion = virtualProjectModel.getVersion();
                final String gitProjectVersion = gitVersionDetails.getVersionTransformer().apply(projectVersion);
                logger.info("project version: " + buffer().strong(gitProjectVersion));
                virtualProjectModel.setVersion(gitProjectVersion);
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

            for (Model previousModel : virtualProjectModelCache.values()) {
                // dependency management section
                DependencyManagement dependencyManagement = previousModel.getDependencyManagement();
                if (dependencyManagement != null && dependencyManagement.getDependencies() != null ) {
                    updateDependencies(projectId, virtualProjectModel, dependencyManagement.getDependencies());
                }
                // dependency section
                if (previousModel.getDependencies() != null) {
                    updateDependencies(projectId, virtualProjectModel, previousModel.getDependencies());
                }
                // Plugin section
                List<Plugin> plugins = previousModel.getBuild().getPlugins();
                if (plugins != null) {
                    updatePlugins(projectId, virtualProjectModel, plugins);
                }
                // PluginManagement section
                PluginManagement pluginManagement = previousModel.getBuild().getPluginManagement();
                if (pluginManagement != null && pluginManagement.getPlugins() != null) {
                    updatePlugins(projectId, virtualProjectModel, pluginManagement.getPlugins());
                }

                // update profile's dependency and dependency management section too
                for (Profile previousModelProfile : previousModel.getProfiles()) {
                    dependencyManagement = previousModelProfile.getDependencyManagement();
                    if (dependencyManagement != null && dependencyManagement.getDependencies() != null ) {
                        updateDependencies(projectId, virtualProjectModel, dependencyManagement.getDependencies());
                    }
                    // dependency section
                    if (previousModelProfile.getDependencies() != null) {
                        updateDependencies(projectId, virtualProjectModel, previousModelProfile.getDependencies());
                    }
                    // Plugin section
                    plugins = previousModelProfile.getBuild().getPlugins();
                    if (plugins != null) {
                        updatePlugins(projectId, virtualProjectModel, plugins);
                    }
                    // PluginManagement section
                    pluginManagement = previousModelProfile.getBuild().getPluginManagement();
                    if (pluginManagement != null && pluginManagement.getPlugins() != null) {
                        updatePlugins(projectId, virtualProjectModel, pluginManagement.getPlugins());
                    }
                }
            }

            logger.info("");

            virtualProjectModel.addProperty("git.commit", gitVersionDetails.getCommit());
            virtualProjectModel.addProperty("git.commit.timestamp", Long.toString(gitVersionDetails.getCommitTimestamp()));
            virtualProjectModel.addProperty("git.commit.timestamp.datetime", toTimestampDateTime(gitVersionDetails.getCommitTimestamp()));
            virtualProjectModel.addProperty("git.ref", gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git.ref.slug", gitVersionDetails.getCommitRefName().toLowerCase().replaceAll("/","-"));
            virtualProjectModel.addProperty("git." + gitVersionDetails.getCommitRefType(), gitVersionDetails.getCommitRefName());
            virtualProjectModel.addProperty("git.dirty", Boolean.toString(!gitVersionDetails.isClean()));

            // ---------------- add plugin ---------------------------------------

            boolean isProjectPom = sessionProjectDirectories.contains(virtualProjectModel.getProjectDirectory().getCanonicalPath());
            if (isProjectPom) {
                boolean updatePomOption = getUpdatePomOption(config, gitVersionDetails);
                addBuildPlugin(virtualProjectModel, updatePomOption);

                // ---------------- add all sub projects to session  -----------------

                for (String module : projectModel.getModules()) {
                    sessionProjectDirectories.add(new File(projectModel.getProjectDirectory(), module).getCanonicalPath());
                }
                for (Profile profile : projectModel.getProfiles()) {
                    for (String module : profile.getModules()) {
                        sessionProjectDirectories.add(new File(projectModel.getProjectDirectory(), module).getCanonicalPath());
                    }
                }

            }

            this.virtualProjectModelCache.put(projectId, virtualProjectModel);

            for (Model model : virtualProjectModelCache.values()) {
                if(projectModel != model){
                    MavenXpp3Writer writer = new MavenXpp3Writer();
                    File file = new File(model.getPomFile().getParent(), GIT_VERSIONING_POM_NAME);
                    writer.write(new FileOutputStream(file), model);
                }
            }
        }
        return virtualProjectModel;
    }

    private void updateDependencies(String projectId, Model virtualProjectModel, List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (dependency.getVersion() != null) {
                GAV dep = new GAV(dependency);
                if (dep.getProjectId().equals(projectId)) {
                    if (virtualProjectModel.getVersion() != null) {
                        dependency.setVersion(virtualProjectModel.getVersion());
                    } else {
                        dependency.setVersion(virtualProjectModel.getParent().getVersion());
                    }
                }
            }
        }
    }

    private void updatePlugins(String projectId, Model virtualProjectModel, List<Plugin> dependencies) {
        for (Plugin plugin : dependencies) {
            if (plugin.getVersion() != null) {
                GAV dep = new GAV(plugin);
                if (dep.getProjectId().equals(projectId)) {
                    if (virtualProjectModel.getVersion() != null) {
                        plugin.setVersion(virtualProjectModel.getVersion());
                    } else {
                        plugin.setVersion(virtualProjectModel.getParent().getVersion());
                    }
                }
            }
        }
    }

    private GitVersionDetails getGitVersionDetails(Configuration config, File repositoryDirectory) {
        GitRepoSituation repoSituation = GitUtil.situation(repositoryDirectory);
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

    private static File findMvnDirectory(File baseDirectory) throws IOException {
        File searchDirectory = baseDirectory;
        while (searchDirectory != null) {
            File mvnDir = new File(searchDirectory, ".mvn");
            if (mvnDir.exists()) {
                return mvnDir;
            }
            searchDirectory = searchDirectory.getParentFile();
        }

        throw new FileNotFoundException("Can not find .mvn directory in hierarchy of " + baseDirectory);
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
    private boolean isRelatedPom(File pomFile) throws IOException {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repositories ends in .pom
                && pomFile.getName().endsWith(".xml")
                && pomFile.getCanonicalPath().startsWith(mvnDirectory.getParentFile().getCanonicalPath() + File.separator)
                // only pom files within git directory are treated as project pom files
                && pomFile.getCanonicalPath().startsWith(gitDirectory.getParentFile().getCanonicalPath() + File.separator);
    }

    private static File findGitDir(File baseDirectory) {
        return new FileRepositoryBuilder().findGitDir(baseDirectory).getGitDir();
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

    private Configuration loadConfig(File configFile) throws IOException {
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
