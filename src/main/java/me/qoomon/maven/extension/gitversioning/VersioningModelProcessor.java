package me.qoomon.maven.extension.gitversioning;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import me.qoomon.maven.BuildProperties;
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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static me.qoomon.maven.extension.gitversioning.StringUtil.*;


/**
 * Replacement for {@link ModelProcessor} to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class VersioningModelProcessor extends DefaultModelProcessor {

    private final Logger logger;
    // for preventing unnecessary logging
    private final Set<String> loggingBouncer = new HashSet<>();

    private final Map<File, GitRepoData> gitRepoDataCache = new HashMap<>();
    private final Map<GAV, GAVGit> gitVersionCache = new HashMap<>();

    private final SessionScope sessionScope;
    private final VersioningConfigurationProvider configurationProvider;

    private MavenSession mavenSession;  // can not be injected cause it is not always available
    private VersioningConfiguration configuration;

    private boolean initialized = false;


    @Inject
    public VersioningModelProcessor(final Logger logger, final SessionScope sessionScope, final VersioningConfigurationProvider configurationProvider) {
        this.logger = logger;
        this.sessionScope = sessionScope;
        this.configurationProvider = configurationProvider;
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

            // ---------------- initialize ---------------------------------------

            if (!initialized) {
                logger.info("");
                logger.info("--- " + BuildProperties.projectArtifactId() + ":" + BuildProperties.projectVersion() + " ---");

                try {
                    mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
                    configuration = configurationProvider.get();
                } catch (OutOfScopeException ex) {
                    logger.warn("skip - no maven session present");
                }

                initialized = true;
            }

            if (mavenSession == null) {
                return projectModel;
            }

            if (!configuration.isEnabled()) {
                if (loggingBouncer.add("DISABLED")) {
                    logger.info("disabled");
                }
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

            if (projectPomFile.getName().equals("pom.virtual.xml")) {
                logger.debug("skip - virtual pom - " + projectPomFile);
                return projectModel;
            }


            // ---------------- process project model ----------------------------

            final Model virtualProjectModel = projectModel.clone();


            // ---------------- handle project git based version ----------------

            final GAV projectGav = GAV.of(projectModel);
            if (projectGav.getVersion() == null) {
                logger.warn("skip - invalid model - 'version' is missing - " + projectPomFile);
                return projectModel;
            }

            final GAVGit projectGitBasedVersion = determineGitBasedProjectVersion(projectGav, projectPomFile.getParentFile());

            // log only once per GAV
            if (loggingBouncer.add(projectGav.toString())) {
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

                    final GAVGit parentGitBasedVersion = determineGitBasedProjectVersion(parentGav, parentPomFile.getParentFile());

                    logger.debug("set parent version to " + parentGitBasedVersion + " in " + projectPomFile);
                    virtualProjectModel.getParent().setVersion(parentGitBasedVersion.getVersion());
                }
            }

            // ---------------- add plugin ---------------------------------------

            addBuildPlugin(virtualProjectModel); // has to be removed from model by plugin itself

            return virtualProjectModel;
        } catch (Exception e) {
            throw new IOException("Branch Versioning Model Processor", e);
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

        Plugin projectPlugin = VersioningPomReplacementMojo.asPlugin();

        PluginExecution execution = new PluginExecution();
        execution.setId(VersioningPomReplacementMojo.GOAL);
        execution.getGoals().add(VersioningPomReplacementMojo.GOAL);
        projectPlugin.getExecutions().add(execution);

        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }
        model.getBuild().getPlugins().add(projectPlugin);
    }

    private GAVGit determineGitBasedProjectVersion(GAV gav, File gitDir) throws IOException {
        GAVGit gitBasedProjectVersion = gitVersionCache.get(gav);
        if (gitBasedProjectVersion == null) {
            final GitRepoData gitRepoData = getGitRepoData(gitDir);

            // default versioning
            String projectCommitRefType = "commit";
            String projectCommitRefName = gitRepoData.getCommit();
            VersionFormatDescription projectVersionFormatDescription = configuration.getCommitVersionDescription();

            // branch versioning
            String gitRepoBranch = gitRepoData.getBranch();
            if (gitRepoBranch != null) {
                for (VersionFormatDescription versionFormatDescription : configuration.getBranchVersionDescriptions()) {
                    if (gitRepoBranch.matches(versionFormatDescription.pattern)) {
                        projectCommitRefType = "branch";
                        projectCommitRefName = gitRepoBranch;
                        projectVersionFormatDescription = versionFormatDescription;
                        break;
                    }
                }
            } else {
                // tag versioning
                List<String> gitRepoTags = gitRepoData.getTags();
                if (!gitRepoTags.isEmpty()) {
                    for (VersionFormatDescription versionFormatDescription : configuration.getTagVersionDescriptions()) {
                        String gitRepoVersionTag = gitRepoTags.stream().sequential()
                                .filter(tag -> tag.matches(versionFormatDescription.pattern))
                                .max((tagLeft, tagRight) -> {
                                    String versionLeft = removePrefix(tagLeft, versionFormatDescription.prefix);
                                    String versionRight = removePrefix(tagRight, versionFormatDescription.prefix);
                                    DefaultArtifactVersion tagVersionLeft = new DefaultArtifactVersion(versionLeft);
                                    DefaultArtifactVersion tagVersionRight = new DefaultArtifactVersion(versionRight);
                                    return tagVersionLeft.compareTo(tagVersionRight);
                                }).orElse(null);
                        if (gitRepoVersionTag != null) {
                            projectCommitRefType = "tag";
                            projectCommitRefName = gitRepoVersionTag;
                            projectVersionFormatDescription = versionFormatDescription;
                            break;
                        }
                    }
                }
            }

            Map<String, String> projectVersionDataMap = buildCommonVersionDataMap(gav);
            projectVersionDataMap.put("commit", gitRepoData.getCommit());
            projectVersionDataMap.put("commit.short", gitRepoData.getCommit().substring(0, 7));
            projectVersionDataMap.put(projectCommitRefType, removePrefix(projectCommitRefName, projectVersionFormatDescription.prefix));
            projectVersionDataMap.putAll(valueGroupMap(projectVersionFormatDescription.pattern, projectCommitRefName));

            String versionGit = escapeVersion(substituteText(projectVersionFormatDescription.versionFormat, projectVersionDataMap));

            gitBasedProjectVersion = new GAVGit(
                    gav.getGroupId(),
                    gav.getArtifactId(),
                    versionGit,
                    gitRepoData.getCommit(),
                    projectCommitRefType,
                    removePrefix(projectCommitRefName, projectVersionFormatDescription.prefix)
            );
            gitVersionCache.put(gav, gitBasedProjectVersion);
        }
        return gitBasedProjectVersion;

    }

    private GitRepoData getGitRepoData(File gitDir) throws IOException {
        GitRepoData gitRepoData;
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(gitDir);
        gitDir = repositoryBuilder.getGitDir();
        gitRepoData = gitRepoDataCache.get(gitDir);
        if (gitRepoData == null) {
            logger.debug("git directory " + repositoryBuilder.getGitDir());
            try (Repository repository = repositoryBuilder.build()) {

                final Status status = GitUtil.getStatus(repository);
                if (!status.isClean()) {
                    logger.warn("Git working tree is not clean " + repository.getDirectory());
                }

                final String headCommit = GitUtil.getHeadCommit(repository);

                String headBranch = GitUtil.getHeadBranch(repository);
                final String providedBranch = configuration.getProvidedBranch();
                if (providedBranch != null) {
                    headBranch = providedBranch.isEmpty() ? null : providedBranch;
                }

                List<String> headTags = GitUtil.getHeadTags(repository);
                final String providedTag = configuration.getProvidedTag();
                if (providedTag != null) {
                    headTags = providedTag.isEmpty() ? emptyList() : singletonList(providedTag);
                }

                gitRepoData = new GitRepoData(headCommit, headBranch, headTags);
                gitRepoDataCache.put(gitDir, gitRepoData);
            }
        }
        return gitRepoData;
    }

    private static Map<String, String> buildCommonVersionDataMap(GAV gav) {
        Map<String, String> versionDataMap = new HashMap<>();
        versionDataMap.put("version", gav.getVersion());
        versionDataMap.put("version.release", gav.getVersion().replaceFirst("-SNAPSHOT$", ""));
        return versionDataMap;
    }

    private static String escapeVersion(String version) {
        return version.replace("/", "-");
    }

    private static class GitRepoData {

        private final String commit;
        private final String branch;
        private final List<String> tags;

        GitRepoData(String commit, String branch, List<String> tags) {

            this.commit = commit;
            this.branch = branch;
            this.tags = tags;
        }

        public String getCommit() {
            return commit;
        }

        public String getBranch() {
            return branch;
        }

        public List<String> getTags() {
            return tags;
        }
    }
}
