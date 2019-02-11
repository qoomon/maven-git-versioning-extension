package me.qoomon.maven.gitversioning;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class GitVersioningExtensionIT {

    @Test
    public void commitVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dgit.branch=");
        verifier.addCliOption("-Dgit.tag=");
        verifier.addCliOption("-Dgit.commit=" + providedCommit);
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir, GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-commit" + GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
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
        verifier.addCliOption("-Dgit.commit=" + providedCommit);
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
        verifier.addCliOption("-Dgit.commit=" + providedCommit);
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
        verifier.addCliOption("-Dgit.commit=" + providedCommit);
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
        verifier.addCliOption("-Dgit.commit=" + providedCommit);
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
}