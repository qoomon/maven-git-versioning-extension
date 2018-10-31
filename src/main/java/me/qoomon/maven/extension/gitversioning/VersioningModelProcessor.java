package me.qoomon.maven.extension.gitversioning;

import me.qoomon.maven.BuildProperties;
import me.qoomon.maven.GAV;
import me.qoomon.maven.extension.gitversioning.config.VersioningConfiguration;
import me.qoomon.maven.extension.gitversioning.config.VersioningConfigurationProvider;
import me.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
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


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class VersioningModelProcessor extends DefaultModelProcessor {

    private static final String GIT_VERSIONING_PROPERTY_KEY = "gitVersioning";

    private static final String PROJECT_BRANCH_PROPERTY_KEY = "project.branch";
    private static final String PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_BRANCH";

    private static final String PROJECT_TAG_PROPERTY_KEY = "project.tag";
    private static final String PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_TAG";

    private final Logger logger;
    private final SessionScope sessionScope;
    private final VersioningConfigurationProvider configurationProvider;

    private boolean initialized = false;
    // can not be injected cause it is not always available
    private MavenSession mavenSession;
    private boolean disabled = false;

    private String providedTag;
    private String providedBranch;

    private VersioningConfiguration configuration;

    // for preventing unnecessary logging
    private final Set<File> loggerProjectRepositoryDirectorySet = new HashSet<>();
    private final Set<GAV> loggerProjectModuleSet = new HashSet<>();


    @Inject
    public VersioningModelProcessor(final Logger logger, final SessionScope sessionScope, final VersioningConfigurationProvider configurationProvider) {
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

    private Model provisionModel(Model projectModel, Map<String, ?> options) throws IOException {
        try {
            // ---------------- initialize ----------------

            if (!initialized) {
                initialize();
                initialized = true;
            }

            if (disabled) {
                logger.debug("skip - extension disabled");
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


            final Model virtualProjectModel = projectModel.clone();


            // ---------------- handle project git based version ----------------

            final GAV projectGav = GAV.of(projectModel);
            if (projectGav.getVersion() == null) {
                logger.warn("skip - invalid model - 'version' is missing - " + projectPomFile);
                return projectModel;
            }

            final GitBasedProjectVersion projectGitBasedVersion = determineGitBasedProjectVersion(projectGav, projectPomFile.getParentFile());

            // log only once per git repository
            if (loggerProjectRepositoryDirectorySet.add(projectGitBasedVersion.getRepositoryPath())) {
                if (projectGitBasedVersion.isRepositoryDirty()) {
                    logger.warn("Git working tree is not clean " + projectGitBasedVersion.getRepositoryPath());
                }
            }

            // log only once per GAV
            if (loggerProjectModuleSet.add(projectGav)) {
                logger.info(projectGav.getArtifactId() + ":" + projectGav.getVersion()
                        + " - " + projectGitBasedVersion.getCommitRefType() + ": " + projectGitBasedVersion.getCommitRefName()
                        + " -> version: " + projectGitBasedVersion.getVersion());
            }

            if (projectModel.getVersion() != null) {
                logger.debug("set project version to " + projectGitBasedVersion + " in " + projectPomFile);
                virtualProjectModel.setVersion(projectGitBasedVersion.getVersion());
            }

            logger.debug("add project properties");
            virtualProjectModel.addProperty("project.commit", projectGitBasedVersion.getCommit());
            virtualProjectModel.addProperty("project.tag", projectGitBasedVersion.getCommitRefType().equals("tag") ? projectGitBasedVersion.getCommitRefName() : "");
            virtualProjectModel.addProperty("project.branch", projectGitBasedVersion.getCommitRefType().equals("branch") ? projectGitBasedVersion.getCommitRefName() : "");


            // ---------------- handle parent git based version ----------------

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

                    final GitBasedProjectVersion parentGitBasedVersion = determineGitBasedProjectVersion(parentGav, parentPomFile.getParentFile());

                    logger.debug("set parent version to " + parentGitBasedVersion + " in " + projectPomFile);
                    virtualProjectModel.getParent().setVersion(parentGitBasedVersion.getVersion());
                }
            }

            // ---------------- add plugin ----------------

            addBuildPlugin(virtualProjectModel); // has to be removed from model by plugin itself

            return virtualProjectModel;
        } catch (Exception e) {
            throw new IOException("Branch Versioning Model Processor", e);
        }
    }


    private void initialize() {
        logger.info("");
        logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

        Optional<MavenSession> mavenSessionOptional = SessionScopeUtil.get(sessionScope, MavenSession.class);
        if (!mavenSessionOptional.isPresent()) {
            logger.warn("Skip provisioning. No MavenSession present.");
            disabled = true;
        } else {
            mavenSession = mavenSessionOptional.get();
            //  check if extension is disabled
            String gitVersioningExtensionEnabled = mavenSession.getUserProperties().getProperty(GIT_VERSIONING_PROPERTY_KEY);
            if ("false".equals(gitVersioningExtensionEnabled)) {
                logger.info("Disabled.");
                disabled = true;
            } else {
                providedTag = mavenSession.getUserProperties().getProperty(PROJECT_TAG_PROPERTY_KEY);
                if (providedTag == null) {
                    providedTag = System.getenv(PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME);
                }

                providedBranch = mavenSession.getUserProperties().getProperty(PROJECT_BRANCH_PROPERTY_KEY);
                if (providedBranch == null) {
                    providedBranch = System.getenv(PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME);
                }

                if (providedTag != null && providedBranch != null) {
                    logger.warn("provided branch [" + providedBranch + "] is ignored " +
                            "due to provided tag [" + providedTag + "] !");
                    providedBranch = null;
                }

                this.configuration = configurationProvider.get();
            }
        }
    }

    /**
     * checks if <code>pomFile</code> is part of a project
     *
     * @param pomFile the pom file
     * @return true if <code>pomFile</code> is part of a project
     */
    private boolean isProjectPom(File pomFile) {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repository ends in .pom
                && pomFile.getName().endsWith(".xml");
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


    private GitBasedProjectVersion determineGitBasedProjectVersion(GAV gav, File gitDir) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        logger.debug(gav + "git directory " + repositoryBuilder.getGitDir());

        try (Repository repository = repositoryBuilder.build()) {

            final String headCommit = GitUtil.getHeadCommit(repository);

            final Status status = GitUtil.getStatus(repository);

            Optional<String> headBranch = GitUtil.getHeadBranch(repository);
            if (providedBranch != null) {
                if (!providedBranch.isEmpty()) {
                    headBranch = Optional.of(providedBranch);
                } else {
                    headBranch = Optional.empty();
                }
            }

            List<String> headTags = GitUtil.getHeadTags(repository);
            if (providedTag != null) {
                if (!providedTag.isEmpty()) {
                    headTags = Collections.singletonList(providedTag);
                } else {
                    headTags = Collections.emptyList();
                }
            }

            // default versioning
            VersionFormatDescription projectVersionFormatDescription = configuration.getCommitVersionDescription();
            String projectCommitRefType = "commit";
            String projectCommitRefName = headCommit;

            // branch versioning
            if (headBranch.isPresent() && providedTag == null) {
                for (VersionFormatDescription versionFormatDescription : configuration.getBranchVersionDescriptions()) {
                    if (headBranch.get().matches(versionFormatDescription.pattern)) {
                        projectVersionFormatDescription = versionFormatDescription;
                        projectCommitRefType = "branch";
                        projectCommitRefName = headBranch.get();
                        break;
                    }
                }
            } else
                // tag versioning
                if (!headTags.isEmpty()) {
                    for (VersionFormatDescription versionFormatDescription : configuration.getTagVersionDescriptions()) {
                        // -1 revert sorting, latest version first
                        Optional<String> headVersionTag = headTags.stream().sequential()
                                .filter(tag -> tag.matches(versionFormatDescription.pattern))
                                .max((versionLeft, versionRight) -> {
                                    DefaultArtifactVersion tagVersionLeft = new DefaultArtifactVersion(removePrefix(versionLeft, versionFormatDescription.prefix));
                                    DefaultArtifactVersion tagVersionRight = new DefaultArtifactVersion(removePrefix(versionRight, versionFormatDescription.prefix));
                                    return tagVersionLeft.compareTo(tagVersionRight);
                                });
                        if (headVersionTag.isPresent()) {
                            projectVersionFormatDescription = versionFormatDescription;
                            projectCommitRefType = "tag";
                            projectCommitRefName = headVersionTag.get();
                            break;
                        }
                    }
                }

            Map<String, String> projectVersionDataMap = buildCommonVersionDataMap(gav);
            projectVersionDataMap.put("commit", headCommit);
            projectVersionDataMap.put("commit.short", headCommit.substring(0, 7));
            projectVersionDataMap.put(projectCommitRefType, removePrefix(projectCommitRefName, projectVersionFormatDescription.prefix));
            projectVersionDataMap.putAll(getRegexGroupValueMap(projectVersionFormatDescription.pattern, projectCommitRefName));
            String version = subsituteText(projectVersionFormatDescription.versionFormat, projectVersionDataMap);
            return new GitBasedProjectVersion(escapeVersion(version),
                    headCommit, projectCommitRefName, projectCommitRefType,
                    repository.getDirectory().getParentFile(), !status.isClean());
        }
    }

    private static Map<String, String> buildCommonVersionDataMap(GAV gav) {
        Map<String, String> versionDataMap = new HashMap<>();
        versionDataMap.put("version", gav.getVersion());
        versionDataMap.put("version.release", gav.getVersion().replaceFirst("-SNAPSHOT$", ""));
        return versionDataMap;
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

            // determine group names
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

    private static String subsituteText(String text, Map<String, String> substitutionMap) {
        String result = text;

        final Pattern placeholderPattern = Pattern.compile("\\$\\{(.+?)}");
        final Matcher placeholderMatcher = placeholderPattern.matcher(text);
        while (placeholderMatcher.find()) {
            String substitutionKey = placeholderMatcher.group(1);
            String substitutionValue = substitutionMap.get(substitutionKey);
            result = result.replaceAll("\\$\\{" + substitutionKey + "}", substitutionValue);
        }

        return result;
    }

    class GitBasedProjectVersion {

        private final String version;
        private final String commit;
        private final String commitRefName;
        private final String commitRefType;
        private final File repositoryPath;
        private final boolean repositoryDirty;

        GitBasedProjectVersion(String version,
                               String commit, String commitRefName, String commitRefType,
                               File repositoryPath, boolean repositoryDirty) {
            this.version = version;

            this.commit = commit;
            this.commitRefName = commitRefName;
            this.commitRefType = commitRefType;

            this.repositoryPath = repositoryPath;
            this.repositoryDirty = repositoryDirty;
        }

        String getVersion() {
            return version;
        }

        String getCommit() {
            return commit;
        }

        String getCommitRefName() {
            return commitRefName;
        }

        String getCommitRefType() {
            return commitRefType;
        }

        File getRepositoryPath() {
            return repositoryPath;
        }

        boolean isRepositoryDirty() {
            return repositoryDirty;
        }

        @Override
        public String toString() {
            return version;
        }
    }
}
