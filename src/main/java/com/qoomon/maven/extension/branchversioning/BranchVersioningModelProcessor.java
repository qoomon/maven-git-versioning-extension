// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package com.qoomon.maven.extension.branchversioning;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    public static final String VERSION_TAG_PREFIX = "v";
    /**
     * Settings
     */

    private String mainReleaseBranch = "master";

    private Set<String> releaseBranchPrefixSet = Sets.newHashSet("support-", "support/");

    public static final String RELEASE_PROFILE_NAME = "release";

    /**
     * Options
     */

    public static final String DISABLE_BRANCH_VERSIONING_PROPERTY_KEY = "disableBranchVersioning";

    @Requirement
    private Logger logger = null;

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
        // get current session from scope
        MavenSession mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();

        Boolean disableExtension = Boolean.valueOf(mavenSession.getUserProperties().getProperty(DISABLE_BRANCH_VERSIONING_PROPERTY_KEY, "false"));
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

        File requestPomFile = mavenSession.getRequest().getPom();

        GAV projectGav = gavOf(model);

        // check for top level project
        if (pomFile.equals(requestPomFile)) {

            // enable release profile on release branches
            String branchVersion = getBranchVersion(projectGav, pomFile);
            if (!branchVersion.endsWith("-SNAPSHOT")) {
                activateReleaseProfile(mavenSession);
            }

            addBranchVersioningBuildPlugin(model); // will be removed from model by plugin itself
        }

        if (pomFile.getParentFile().getCanonicalPath()
                .startsWith(requestPomFile.getParentFile().getCanonicalPath())) {
            String branchVersion = getBranchVersion(projectGav, pomFile.getParentFile());
            if (model.getParent() != null) {
                GAV parentProjectGav = gavOf(model.getParent());
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

    private void activateReleaseProfile(MavenSession mavenSession) {
        if (mavenSession.getSettings().getProfiles().contains(RELEASE_PROFILE_NAME)) {
            logger.info("Activate " + RELEASE_PROFILE_NAME + "profile.");
            mavenSession.getSettings().addActiveProfile(RELEASE_PROFILE_NAME);
        } else {
            logger.info("No " + RELEASE_PROFILE_NAME + "profile available.");
        }
    }

    private void addBranchVersioningBuildPlugin(Model model) {
        GAV projectGav = gavOf(model);
        logger.debug(projectGav + " temporary add build plugin");
        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }
        model.getBuild().getPlugins().add(BranchVersioningTempPomUpdateMojo.asPlugin());
    }


    public String getBranchVersion(GAV gav, File projectDirectory) {
        if (!branchVersionMap.containsKey(gav)) {
            ensureSemanticVersionFormat(gav); // TODO extract to extra extension
            ensureSnapshotVersion(gav); // TODO extract to extra extension
            String version = generateBranchVersion(gav, projectDirectory);
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

    public String generateBranchVersion(GAV gav, File projectDirectory) {
        logger.debug(gav + " git directory " + projectDirectory);
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(projectDirectory);

        // update project version to branch version for current maven session
        try (Repository repository = repositoryBuilder.build()) {

            ObjectId head = repository.resolve("HEAD");
            String commitHash = head.getName();
            String branchName = repository.getBranch();
            boolean detachedHead = branchName.equals(commitHash);
            if (detachedHead) {
                branchName = "(HEAD detached at " + commitHash + ")";
            }
//            Optional<String> tag = getTag(repository, VERSION_TAG_PREFIX);
//            logger.debug(gav + " commit: " + commitHash);
//            logger.debug(gav + " branch: " + branchName);
//            logger.debug(gav + " tag:    " + tag);

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

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<String> getTag(Repository repository, String prefix) throws IOException {
        Optional<String> tag = Optional.empty();

        ObjectId head = repository.resolve("HEAD");
        for (Map.Entry<String, Ref> refEntry : repository.getTags().entrySet()) {
            boolean isAnnotatedTag = false;

            String tagName = refEntry.getKey();
            Ref tagRef = refEntry.getValue();
            ObjectId targetCommit = repository.peel(tagRef).getPeeledObjectId();

            if (targetCommit != null) {  // annotated Tag
                isAnnotatedTag = true;
            } else { // lightweight Tag
                targetCommit = tagRef.getObjectId();
            }

            logger.debug("tag type: " + (isAnnotatedTag ? "annotated" : "lightweight")
                    + " name: " + tagName
                    + " ref:" + tagRef.getObjectId()
                    + " target commit: " + targetCommit);

            if (tagName.startsWith(prefix) && targetCommit.equals(head)) {
                tag = Optional.of(tagName);
                if (isAnnotatedTag) {
                    break;
                }
            }
        }
        return tag;
    }

    private void ensureSnapshotVersion(GAV gav) {
        logger.info(gav + " Ensure snapshot version");
        if (!gav.getVersion().endsWith("-SNAPSHOT")) {
            throw new IllegalArgumentException(gav + " version is not a snapshot version");
        }
    }

    private void ensureSemanticVersionFormat(GAV gav) {
        logger.info(gav + " Ensure semantic version format");
        if (!Semver.PATTERN.matcher(gav.getVersion()).matches()) {
            throw new IllegalArgumentException(gav + " Version does not match semantic versioning pattern " + Semver.PATTERN);
        }
    }

    public boolean hasBranchVersion(GAV gav) {
        return branchVersionMap.containsKey(gav);
    }

    public GAV gavOf(Model model) {

        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        String version = model.getVersion();

        if (model.getParent() != null) {
            if (groupId == null) {
                groupId = model.getParent().getGroupId();
            }
            if (version == null) {
                version = model.getParent().getVersion();
            }
        }

        return new GAV(groupId, artifactId, version);
    }

    public GAV gavOf(Parent parent) {
        return new GAV(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion()
        );
    }
}
