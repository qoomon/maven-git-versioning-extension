package me.qoomon.maven.extension.gitversioning;

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class VersioningMultiModuleProjectIT {

    @Test
    public void commitVersioning() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/multiModuleProject");
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
            File expectedGitVersionedPomFile = new File(baseDir, "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building api " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/api", VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/api", "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building logic " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/logic", VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/logic", "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }


    @Test
    public void withEnforcePlugin() throws Exception {
        // Given
        File baseDir = ResourceExtractor.simpleExtractResources(getClass(), "/testProjects/multiModuleProject_withEnforcePlugin");
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
            File expectedGitVersionedPomFile = new File(baseDir, "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building api " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/api", VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/api", "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
        {
            verifier.verifyTextInLog("Building logic " + providedCommit);
            File actualGitVersionedPomFile = new File(baseDir + "/logic", VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            File expectedGitVersionedPomFile = new File(baseDir + "/logic", "expected" + VersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME);
            assertThat(actualGitVersionedPomFile).hasSameContentAs(expectedGitVersionedPomFile);
        }
    }
}