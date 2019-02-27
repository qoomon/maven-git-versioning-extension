package me.qoomon.maven.gitversioning;

import me.qoomon.maven.gitversioning.GitVersioningExtensionConfiguration.VersionDescription;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.assertj.core.data.MapEntry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;
import static me.qoomon.maven.gitversioning.GitVersioningPomReplacementMojo.GIT_VERSIONED_POM_FILE_NAME;
import static me.qoomon.maven.gitversioning.ModelUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

public class GitVersioningExtensionIT {

    @TempDir
    Path projectDir;

    Model pomModel = new Model() {
        {
            setModelVersion("4.0.0");
            setGroupId("test");
            setArtifactId("test-artifact");
            setVersion("0.0.0");
        }
    };

    GitVersioningExtensionConfiguration extensionConfig = new GitVersioningExtensionConfiguration();


    @Test
    public void commitVersioning() throws Exception {
        // Given
        Git.init().setDirectory(projectDir.toFile()).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        verifier.verifyErrorFreeLog();
        String expectedVersion = NO_COMMIT;
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", NO_COMMIT),
                    MapEntry.entry("git.ref", NO_COMMIT)
            );
        }));
    }

    @Test
    void branchVersioning() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenBranch = "feature/test";
        git.branchCreate().setName(givenBranch).call();
        git.checkout().setName(givenBranch).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription branchVersionDescription = new VersionDescription();
        branchVersionDescription.pattern = ".*";
        branchVersionDescription.versionFormat = "${branch}-gitVersioning";
        extensionConfig.branches.add(branchVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        verifier.verifyErrorFreeLog();
        String expectedVersion = givenBranch.replace("/", "-") + "-gitVersioning";
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", givenCommit.name()),
                    MapEntry.entry("git.ref", givenBranch),
                    MapEntry.entry("git.branch", givenBranch)
            );
        }));
    }

    @Test
    void tagVersioning() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).call();
        git.checkout().setName(givenTag).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription tagVersionDescription = new VersionDescription();
        tagVersionDescription.pattern = ".*";
        tagVersionDescription.versionFormat = "${tag}-gitVersioning";
        extensionConfig.tags.add(tagVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");
        verifier.displayStreamBuffers();

        // Then
        verifier.verifyErrorFreeLog();
        String expectedVersion = givenTag + "-gitVersioning";
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", givenCommit.name()),
                    MapEntry.entry("git.ref", givenTag),
                    MapEntry.entry("git.tag", givenTag)
            );
        }));
    }

    @Test
    void commitVersioning_multiModuleProject() throws Exception {
        // Given
        Git.init().setDirectory(projectDir.toFile()).call();

        pomModel.setPackaging("pom");
        pomModel.addModule("api");
        pomModel.addModule("logic");
        pomModel.setDependencyManagement(new DependencyManagement() {{
            addDependency(new Dependency() {{
                setGroupId("${project.groupId}");
                setArtifactId("api");
                setVersion("${project.version}");
            }});
            addDependency(new Dependency() {{
                setGroupId("${project.groupId}");
                setArtifactId("logic");
                setVersion("${project.version}");
            }});
        }});
        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);

        Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
        Model apiPomModel = writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
            setModelVersion(pomModel.getModelVersion());
            setParent(new Parent() {{
                setGroupId(pomModel.getGroupId());
                setArtifactId(pomModel.getArtifactId());
                setVersion(pomModel.getVersion());
            }});
            setArtifactId("api");
            setVersion(pomModel.getVersion());
        }});

        Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
        Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
            setModelVersion(pomModel.getModelVersion());
            setParent(new Parent() {{
                setGroupId(pomModel.getGroupId());
                setArtifactId(pomModel.getArtifactId());
                setVersion(pomModel.getVersion());
            }});
            setArtifactId("logic");
        }});

        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");
        verifier.resetStreams();

        // Then
        verifier.verifyErrorFreeLog();
        String expectedVersion = NO_COMMIT;
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", NO_COMMIT),
                    MapEntry.entry("git.ref", NO_COMMIT)
            );
        }));

        Model apiGitVersionedPomModel = readModel(apiProjectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(apiGitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(apiPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(apiPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(apiPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(null);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", NO_COMMIT),
                    MapEntry.entry("git.ref", NO_COMMIT)
            );
        }));

        Model apiGitVersionedPomModelLogic = readModel(logicProjectDir.resolve(GIT_VERSIONED_POM_FILE_NAME).toFile());
        assertThat(apiGitVersionedPomModelLogic).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(null);
            softly.assertThat(it.getProperties()).containsOnly(
                    MapEntry.entry("git.commit", NO_COMMIT),
                    MapEntry.entry("git.ref", NO_COMMIT)
            );
        }));
    }

    private File writeExtensionsFile(Path projectDir) throws IOException {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        return Files.write(mvnDotDir.resolve("extensions.xml"), ("" +
                "<extensions>\n" +
                "  <extension>\n" +
                "    <groupId>me.qoomon</groupId>\n" +
                "    <artifactId>maven-git-versioning-extension</artifactId>\n" +
                "    <version>LATEST</version>\n" +
                "  </extension>\n" +
                "</extensions>").getBytes()).toFile();
    }

    private File writeExtensionConfigFile(Path projectDir, GitVersioningExtensionConfiguration config) throws Exception {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        Path configFile = mvnDotDir.resolve("maven-git-versioning-extension.xml");
        new Persister().write(config, configFile.toFile());
        return configFile.toFile();
    }

    private Model writeModel(File pomFile, Model pomModel) throws IOException {
        ModelUtil.writeModel(pomFile, pomModel);
        return pomModel;
    }
}
