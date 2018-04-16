package com.qoomon.maven.extension.gitversioning;

import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.GAV;
import com.qoomon.maven.ModelUtil;
import com.qoomon.maven.extension.gitversioning.config.VersioningConfiguration;
import com.qoomon.maven.extension.gitversioning.config.VersioningConfigurationProvider;
import com.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.qoomon.maven.extension.gitversioning.SessionScopeUtil.*;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class VersioningModelProcessor extends DefaultModelProcessor {

    private Logger logger;

    private SessionScope sessionScope;

    private VersioningConfigurationProvider configurationProvider;

    private static final String GIT_VERSIONING_PROPERTY_KEY = "gitVersioning";

    private static final String PROJECT_BRANCH_PROPERTY_KEY = "project.branch";
    private static final String PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_BRANCH";

    private static final String PROJECT_TAG_PROPERTY_KEY = "project.tag";
    private static final String PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_TAG";

    // can not be injected cause it is not always available
    private MavenSession mavenSession;

    private VersioningConfiguration configuration;

    private boolean initialized = false;

    private boolean disabled = false;


    @Inject
    public VersioningModelProcessor(Logger logger, SessionScope sessionScope, VersioningConfigurationProvider configurationProvider) {
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

            // deduce version
            ProjectVersion projectVersion = deduceProjectVersion(projectGav, pomFile.getParentFile());

            // add properties
            if (projectVersion.getBranch() != null) {
                model.addProperty("project.branch", projectVersion.getBranch());
            }
            if (projectVersion.getTag() != null) {
                model.addProperty("project.tag", projectVersion.getTag());
            }
            model.addProperty("project.commit", projectVersion.getCommit());

            // update parent version
            if (model.getParent() != null) {
                File parentPomFile = new File(pomFile.getParentFile(), model.getParent().getRelativePath());
                GAV parentGav = GAV.of(model.getParent());
                if (parentPomFile.exists() && isProjectPom(parentPomFile)) {
                    // check if parent pom file match project parent
                    Model parentModel = ModelUtil.readModel(parentPomFile);
                    GAV parentProjectGav = GAV.of(parentModel);
                    if (parentProjectGav.equals(parentGav)) {
                        ProjectVersion parentProjectVersion = deduceProjectVersion(parentGav, parentPomFile.getParentFile());
                        logger.debug(projectGav + " adjust project parent version to " + parentProjectVersion);
                        model.getParent().setVersion(parentProjectVersion.getVersion());
                    }
                }
            }

            // update project version
            if (model.getParent() == null
                    || !model.getParent().getVersion().equals(projectVersion.getVersion())) {
                logger.debug(projectGav + " adjust project version to " + projectVersion);
                model.setVersion(projectVersion.getVersion());
            }

            // add plugin
            addBuildPlugin(model); // has to be removed from model by plugin itself

            return model;
        } catch (Exception e) {
            throw new IOException("Branch Versioning Model Processor", e);
        }
    }

    private void initialize() {
        logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

        Optional<MavenSession> mavenSessionOptional = get(sessionScope, MavenSession.class);
        if (!mavenSessionOptional.isPresent()) {
            logger.warn("Skip provisioning. No MavenSession present.");
            disabled = true;
        } else {
            mavenSession = mavenSessionOptional.get();

            //  check if extension is disabled
            String gitVersioning = mavenSession.getUserProperties().getProperty(GIT_VERSIONING_PROPERTY_KEY);
            if ("false".equals(gitVersioning)) {
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


    private void addBuildPlugin(Model model) {
        GAV projectGav = GAV.of(model);
        logger.debug(projectGav + " temporary add build plugin");
        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }

        Plugin projectPlugin = VersioningPomReplacementMojo.asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(VersioningPomReplacementMojo.GOAL);
        execution.getGoals().add(VersioningPomReplacementMojo.GOAL);
        projectPlugin.getExecutions().add(execution);

        model.getBuild().getPlugins().add(projectPlugin);
    }


    private ProjectVersion deduceProjectVersion(GAV gav, File gitDir) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

        try (Repository repository = repositoryBuilder.build()) {

            final Status status = getStatus(repository);
            final String headCommit = getHeadCommit(repository);
            String projectBranchName = null;
            String projectTagName = null;
            VersionFormatDescription projectVersionFormatDescription = null;
            Map<String, String> projectVersionDataMap = buildCommonVersionDataMap(headCommit, gav);

            if (status.isClean() && !configuration.getTagVersionDescriptions().isEmpty()) {
                final List<String> headTags = getHeadTags(repository);
                if (!headTags.isEmpty()) {
                    for (VersionFormatDescription versionFormatDescription : configuration.getTagVersionDescriptions()) {
                        String tagName = headTags.stream().sequential()
                                .filter(tag -> tag.matches(versionFormatDescription.pattern))
                                .sorted((versionLeft, versionRight) -> {
                                    DefaultArtifactVersion tagVersionLeft = new DefaultArtifactVersion(removePrefix(versionLeft, versionFormatDescription.prefix));
                                    DefaultArtifactVersion tagVersionRight = new DefaultArtifactVersion(removePrefix(versionRight, versionFormatDescription.prefix));
                                    return tagVersionLeft.compareTo(tagVersionRight) * -1; // -1 revert sorting, latest version first
                                })
                                .findFirst().orElse(null);

                        if (tagName != null) {
                            projectTagName = tagName;
                            projectVersionFormatDescription = versionFormatDescription;
                            projectVersionDataMap.put("tag", removePrefix(projectTagName, projectVersionFormatDescription.prefix));
                            projectVersionDataMap.putAll(getRegexGroupValueMap(projectVersionFormatDescription.pattern, projectTagName));
                            break;
                        }
                    }
                }
            }


            if (projectTagName == null) {
                final String branchName = getHeadBranch(repository)
                        .orElseThrow(() -> new ModelParseException(gitDir + ": No Branch Name provided in Detached HEAD state. See documentation.", 0, 0));

                projectBranchName = branchName;
                projectVersionFormatDescription = configuration.getBranchVersionDescriptions().stream()
                        .filter(versionFormatDescription -> branchName.matches(versionFormatDescription.pattern))
                        .findFirst()
                        .orElseThrow(() -> new ModelParseException(gitDir + ": No version format for branch '" + branchName + "' found.", 0, 0));

                projectVersionDataMap.put("branch", removePrefix(projectBranchName, projectVersionFormatDescription.prefix));
                projectVersionDataMap.putAll(getRegexGroupValueMap(projectVersionFormatDescription.pattern, projectBranchName));
            }

            String version = StrSubstitutor.replace(projectVersionFormatDescription.versionFormat, projectVersionDataMap);
            ProjectVersion projectVersion = new ProjectVersion(escapeVersion(version), headCommit, projectBranchName, projectTagName);

            logger.info(gav.getArtifactId()
                    + ":" + gav.getVersion()
                    + (projectVersion.getTag() != null ? " - tag: " + projectVersion.getTag() : "")
                    + (projectVersion.getTag() != null ? " - branch: " + projectVersion.getBranch() : "")
                    + " -> version: " + projectVersion.getVersion());

            return projectVersion;
        }
    }

    private static Map<String, String> buildCommonVersionDataMap(String commit, GAV gav) {
        Map<String, String> versionDataMap = new HashMap<>();
        versionDataMap.put("commit", commit);
        versionDataMap.put("commit.short", commit
                .substring(0, 7));
        versionDataMap.put("version", gav.getVersion());
        versionDataMap.put("version.release", gav.getVersion()
                .replaceFirst("-SNAPSHOT$", ""));
        return versionDataMap;
    }

    private Status getStatus(Repository repository) {
        try {
            return Git.wrap(repository).status().call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<String> getHeadBranch(Repository repository) throws IOException {

        Optional<String> branchOverwrite = Stream.of(
                mavenSession.getUserProperties().getProperty(PROJECT_BRANCH_PROPERTY_KEY),
                System.getenv(PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME))
                .sequential()
                .filter(Objects::nonNull).findFirst();
        if (branchOverwrite.isPresent()) {
            return branchOverwrite;
        }

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return Optional.of("master");
        }

        boolean detachedHead = ObjectId.isId(repository.getFullBranch());
        if (detachedHead) {
            return Optional.empty();
        }

        return Optional.of(repository.getBranch());
    }


    private List<String> getHeadTags(Repository repository) throws IOException {

        Optional<String> tagOverwrite = Stream.of(
                mavenSession.getUserProperties().getProperty(PROJECT_TAG_PROPERTY_KEY),
                System.getenv(PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME))
                .sequential()
                .filter(Objects::nonNull).findFirst();
        if (tagOverwrite.isPresent()) {
            return Collections.singletonList(tagOverwrite.get());
        }

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return Collections.emptyList();
        }

        return repository.getTags().values().stream()
                .map(repository::peel)
                .filter(ref -> {
                    ObjectId objectId;
                    if (ref.getPeeledObjectId() != null) {
                        objectId = ref.getPeeledObjectId();
                    } else {
                        objectId = ref.getObjectId();
                    }
                    return objectId.equals(head);
                })
                .map(ref -> ref.getName().replaceFirst("^refs/tags/", ""))
                .collect(Collectors.toList());
    }

    private String getHeadCommit(Repository repository) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return "0000000000000000000000000000000000000000";
        }
        return head.getName();
    }

    /**
     * @return a map of group-index and group-name to matching value
     */
    private Map<String, String> getRegexGroupValueMap(String regex, String text) {
        Map<String, String> result = new HashMap<>();
        Pattern groupPattern = Pattern.compile(regex);
        Matcher groupMatcher = groupPattern.matcher(text);
        if (groupMatcher.find()) {
            // add group index to value entries
            for (int i = 0; i <= groupMatcher.groupCount(); i++) {
                result.put(String.valueOf(i), groupMatcher.group(i));
            }

            // determine group ames
            Pattern groupNamePattern = Pattern.compile("\\(\\?<(?<name>[a-zA-Z][a-zA-Z0-9]*)>");
            Matcher groupNameMatcher = groupNamePattern.matcher(groupPattern.toString());

            // add group name to value Entries
            while (groupNameMatcher.find()) {
                String groupName = groupNameMatcher.group("name");
                result.put(groupName, groupMatcher.group(groupName));
            }
        }
        return result;
    }

    private static String removePrefix(String string, String prefix) {
        return string.replaceFirst(Pattern.quote(prefix), "");
    }

    private static String escapeVersion(String version) {
        return version.replace("/", "-");
    }

    class ProjectVersion {

        final String version;
        final String commit;
        final String branch;
        final String tag;

        ProjectVersion(String version, String commit, String branch, String tag) {
            this.version = version;
            this.commit = commit;
            this.branch = branch;
            this.tag = tag;
        }

        String getVersion() {
            return version;
        }

        String getCommit() {
            return commit;
        }

        String getBranch() {
            return branch;
        }

        String getTag() {
            return tag;
        }

        @Override
        public String toString() {
            return version;
        }
    }
}
