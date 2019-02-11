package me.qoomon.maven.gitversioning;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.apache.maven.model.Model;
import org.assertj.core.data.MapEntry;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;
import static me.qoomon.maven.gitversioning.ModelUtil.readModel;
import static me.qoomon.maven.gitversioning.ModelUtil.writeModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.eclipse.jgit.lib.Constants.MASTER;

public class GitVersioningExtensionIT {

    @TempDir
    Path projectDir;

    @Test
    public void commitVersioning() throws Exception {
        // Given
        Git.init().setDirectory(projectDir.toFile()).call();

        Model pomModel = new Model();
        pomModel.setModelVersion("4.0.0");
        pomModel.setGroupId("test");
        pomModel.setArtifactId("test-artifact");
        pomModel.setVersion("0.0.0");
        writeModel(pomModel, projectDir.resolve("pom.xml").toFile());
        writeExtensionsFile(projectDir);
        GitVersioningExtensionConfiguration config = new GitVersioningExtensionConfiguration();
        writeExtensionConfigFile(projectDir, config);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.addCliOption("-Dgit.branch=");
        verifier.addCliOption("-Dgit.tag=");
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Building test-artifact " + NO_COMMIT);
        File gitVersionedPomFile = new File(projectDir.toFile(), GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
        Model gitVersionedPomModel = readModel(gitVersionedPomFile);
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(NO_COMMIT);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", NO_COMMIT),
                    MapEntry.entry("git.ref", NO_COMMIT),
                    MapEntry.entry("git.ref.0", NO_COMMIT)
            );
        }));
    }

    @Test
    void branchVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedBranch = "feature/test";
        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dgit.branch=" + providedBranch);
        verifier.addCliOption("-Dgit.tag=");
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + "test-SNAPSHOT");
            File actualGitVersionedPomFile = new File(baseDir, GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-branch" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }

    @Test
    void tagVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedTag = "version/test";
        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dgit.branch=");
        verifier.addCliOption("-Dgit.tag=" + providedTag);
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + "test");
            File actualGitVersionedPomFile = new File(baseDir, GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-tag" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }

    @Test
    void commitVersioning_multiModuleProject() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/multiModuleProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dgit.branch=");
        verifier.addCliOption("-Dgit.tag=");
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir, GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building api " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/api", GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/api", "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building logic " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/logic", GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/logic", "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }


    @Test
    void withEnforcePlugin_multiModuleProject() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/multiModuleProject_withEnforcePlugin");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dgit.branch=");
        verifier.addCliOption("-Dgit.tag=");
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir, GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building api " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/api", GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/api", "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building logic " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/logic", GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/logic", "expected" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }


    private Path writeExtensionsFile(Path projectDir) throws IOException {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        return Files.write(mvnDotDir.resolve("extensions.xml"), ("" +
                "<extensions>\n" +
                "  <extension>\n" +
                "    <groupId>me.qoomon</groupId>\n" +
                "    <artifactId>maven-git-versioning-extension</artifactId>\n" +
                "    <version>LATEST</version>\n" +
                "  </extension>\n" +
                "</extensions>").getBytes());
    }

    private Path writeExtensionConfigFile(Path projectDir, GitVersioningExtensionConfiguration config) throws Exception {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        Path configFile = mvnDotDir.resolve("maven-git-versioning-extension.xml");
        unchecked(() -> new Persister().write(config, configFile.toFile()));
        return configFile;
    }
}
