package me.qoomon.maven.extension.gitversioning;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class VersioningStandardProjectIT {

    @Test
    public void commitVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dproject.branch=");
        verifier.addCliOption("-Dproject.tag=");
        verifier.addCliOption("-Dproject.commit=" + providedCommit);
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir, VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-commit" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }

    @Test
    public void branchVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedBranch = "feature/test";
        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dproject.branch=" + providedBranch);
        verifier.addCliOption("-Dproject.tag=");
        verifier.addCliOption("-Dproject.commit=" + providedCommit);
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + "test-SNAPSHOT");
            File actualGitVersionedPomFile = new File(baseDir, VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-branch" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }

    @Test
    public void tagVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/standardProject");
        Verifier verifier = new Verifier(baseDir.getAbsolutePath());
        verifier.executeGoal("clean");
        verifier.resetStreams();

        String providedTag = "version/test";
        String providedCommit = "a02162d634f5f5721d0d66d87291c8e5e5d7446c";

        // When
        verifier.addCliOption("-Dproject.branch=");
        verifier.addCliOption("-Dproject.tag=" + providedTag);
        verifier.addCliOption("-Dproject.commit=" + providedCommit);
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        {
            verifier.verifyTextInLog("Building main " + "test");
            File actualGitVersionedPomFile = new File(baseDir, VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir, "expected-tag" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }
}