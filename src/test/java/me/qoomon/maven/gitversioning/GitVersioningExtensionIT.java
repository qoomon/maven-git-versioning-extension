package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import me.qoomon.maven.gitversioning.Configuration.VersionDescription;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static me.qoomon.maven.gitversioning.VersioningMojo.GIT_VERSIONING_POM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GitVersioningExtensionIT {

    @TempDir
    Path projectDir;

    Model pomModel = new Model() {{
        setModelVersion("4.0.0");
        setGroupId("test");
        setArtifactId("test-artifact");
        setVersion("0.0.0");
    }};

    Configuration extensionConfig = new Configuration();

    @Test
    void commitVersioning() throws Exception {
        // Given
        Git.init().setDirectory(projectDir.toFile()).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = NO_COMMIT;
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);

        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
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
        extensionConfig.branch.add(branchVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = givenBranch.replace("/", "-") + "-gitVersioning";
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref",
                    "git.branch"
            );
        }));
    }

    @Test
    void tagVersioning_annotated_detached() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";

        git.tag().setAnnotated(true).setName(givenTag).call();
        git.checkout().setName(givenTag).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription tagVersionDescription = new VersionDescription();
        tagVersionDescription.pattern = ".*";
        tagVersionDescription.versionFormat = "${tag}-gitVersioning";
        extensionConfig.tag.add(tagVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = givenTag + "-gitVersioning";
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref",
                    "git.tag"
            );
        }));
    }

    @Test
    void tagVersioning_nonAnnotatedTagNotDetached() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";

        git.tag().setAnnotated(false).setName(givenTag).call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription tagVersionDescription = new VersionDescription();
        tagVersionDescription.pattern = ".*";
        tagVersionDescription.versionFormat = "${tag}-gitVersioning";
        extensionConfig.tag.add(tagVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = givenTag + "-gitVersioning";
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref",
                    "git.tag"
            );
        }));
    }

    @Test
    void tagVersioning_cliOption() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription tagVersionDescription = new VersionDescription();
        tagVersionDescription.pattern = ".*";
        tagVersionDescription.versionFormat = "${tag}-gitVersioning";
        extensionConfig.tag.add(tagVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.addCliOption("-Dgit.tag=" + givenTag);
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = givenTag + "-gitVersioning";
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref",
                    "git.tag"
            );
        }));
    }

    @Test
    void tagVersioning_cliOption_conflictingTag() throws Exception {
        // Given
        Git git = Git.init().setDirectory(projectDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";

        git.tag().setAnnotated(false).setName("v2").call();

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);

        VersionDescription tagVersionDescription = new VersionDescription();
        tagVersionDescription.pattern = ".*";
        tagVersionDescription.versionFormat = "${tag}-gitVersioning";
        extensionConfig.tag.add(tagVersionDescription);
        writeExtensionConfigFile(projectDir, extensionConfig);

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.addCliOption("-Dgit.tag=" + givenTag);
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = givenTag + "-gitVersioning";
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref",
                    "git.tag"
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
        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, extensionConfig);

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

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = NO_COMMIT;
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));

        Model apiGitVersionedPomModel = readModel(apiProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(apiGitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(apiPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(apiPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(apiPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));

        Model logicGitVersionedPomModel = readModel(logicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(logicGitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(null);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));
    }

    @Test
    void commitVersioning_multiModuleProject_ambiguous_artifactId() throws Exception {
        // Given
        Git.init().setDirectory(projectDir.toFile()).call();

        pomModel.setPackaging("pom");
        pomModel.addModule("logic");
        pomModel.addModule("another-group-logic");

        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, extensionConfig);

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
        Path anotherGroupLogicProjectDir = Files.createDirectories(projectDir.resolve("another-group-logic"));
        Model anotherGroupLogicPomModel = writeModel(anotherGroupLogicProjectDir.resolve("pom.xml").toFile(), new Model() {{
            setModelVersion(pomModel.getModelVersion());
            setParent(new Parent() {{
                setGroupId(pomModel.getGroupId());
                setArtifactId(pomModel.getArtifactId());
                setVersion(pomModel.getVersion());
            }});
            setGroupId("another.group");
            setArtifactId("logic");
        }});

        // When
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
        verifier.executeGoal("verify");

        // Then
        String log = getLog(verifier);
        assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
        String expectedVersion = NO_COMMIT;
        assertThat(log).contains("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        Model gitVersionedPomModel = readModel(projectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(gitVersionedPomModel).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(pomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(pomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(pomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));

        Model apiGitVersionedPomModelLogic = readModel(logicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(apiGitVersionedPomModelLogic).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(null);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));

        Model duplicateArtifactidGitVersionedPomModelLogic = readModel(anotherGroupLogicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
        assertThat(duplicateArtifactidGitVersionedPomModelLogic).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.getModelVersion()).isEqualTo(anotherGroupLogicPomModel.getModelVersion());
            softly.assertThat(it.getGroupId()).isEqualTo(anotherGroupLogicPomModel.getGroupId());
            softly.assertThat(it.getArtifactId()).isEqualTo(anotherGroupLogicPomModel.getArtifactId());
            softly.assertThat(it.getVersion()).isEqualTo(null);
            softly.assertThat(it.getProperties()).doesNotContainKeys(
                    "git.commit",
                    "git.ref"
            );
        }));
    }

    @Test
    void branchVersioning_multiModuleProject_noParent() throws Exception {
        // Given
        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            String givenBranch = "feature/test";
            git.branchCreate().setName(givenBranch).call();
            git.checkout().setName(givenBranch).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("api");
            pomModel.addModule("logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            VersionDescription branchVersionDescription = new VersionDescription();
            branchVersionDescription.pattern = ".*";
            branchVersionDescription.versionFormat = "${branch}-gitVersioning";
            extensionConfig.branch.add(branchVersionDescription);
            writeExtensionConfigFile(projectDir, extensionConfig);

            Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
            Model apiPomModel = writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("api");
            }});

            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("logic");
            }});

            // When
            Verifier verifier = new Verifier(logicProjectDir.toFile().getAbsolutePath());
            verifier.executeGoal("verify");

            // Then
            String log = getLog(verifier);
            assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
            String expectedVersion = givenBranch.replace("/", "-") + "-gitVersioning";
            //        assertThat(log).contains("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel).satisfies(it -> assertSoftly(softly -> {
                softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
                softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
                softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
                softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
                softly.assertThat(it.getProperties()).doesNotContainKeys(
                        "git.commit",
                        "git.ref"
                );
            }));
        }
    }

    @Test
    void branchVersioning_multiModuleProject_withExternalParent() throws Exception {
        // Given

        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            String givenBranch = "feature/test";
            git.branchCreate().setName(givenBranch).call();
            git.checkout().setName(givenBranch).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("api");
            pomModel.addModule("logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            VersionDescription branchVersionDescription = new VersionDescription();
            branchVersionDescription.pattern = ".*";
            branchVersionDescription.versionFormat = "${branch}-gitVersioning";
            extensionConfig.branch.add(branchVersionDescription);
            writeExtensionConfigFile(projectDir, extensionConfig);

            Parent externalParent = new Parent() {{
                setGroupId("org.springframework.boot");
                setArtifactId("spring-boot-starter-parent");
                setVersion("2.1.3.RELEASE");
            }};

            Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
            Model apiPomModel = writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("api");
                setParent(externalParent);
            }});

            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("logic");
            }});

            // When
            Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath());
            verifier.executeGoal("verify");

            // Then
            String log = getLog(verifier);
            assertThat(log).doesNotContain("[ERROR]", "[FATAL]");
            String expectedVersion = givenBranch.replace("/", "-") + "-gitVersioning";
            //        assertThat(log).contains("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel).satisfies(it -> assertSoftly(softly -> {
                softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
                softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
                softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
                softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
                softly.assertThat(it.getProperties()).doesNotContainKeys(
                        "git.commit",
                        "git.ref"
                );
            }));
        }
    }

    @Test
    void branchVersioning_multiModuleProject_withAggregationAndParent() throws Exception {
        // Given

        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            String givenBranch = "feature/test";
            git.branchCreate().setName(givenBranch).call();
            git.checkout().setName(givenBranch).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("parent");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            VersionDescription branchVersionDescription = new VersionDescription();
            branchVersionDescription.pattern = ".*";
            branchVersionDescription.versionFormat = "${branch}-gitVersioning";
            extensionConfig.branch.add(branchVersionDescription);
            writeExtensionConfigFile(projectDir, extensionConfig);

            final Path parentProjectDir = Files.createDirectories(projectDir.resolve("parent"));
            Model parentPomModel = writeModel(parentProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setArtifactId("parent");
                setVersion(pomModel.getVersion());

                setPackaging("pom");
                addModule("../logic");
            }});

            final Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(parentPomModel.getModelVersion());
                setParent(new Parent() {{
                    setGroupId(parentPomModel.getGroupId());
                    setArtifactId(parentPomModel.getArtifactId());
                    setVersion(parentPomModel.getVersion());
                    setRelativePath("../parent");
                }});
                setArtifactId("logic");
                setVersion(parentPomModel.getVersion());
            }});

            // When
            Verifier installVerifier = new Verifier(projectDir.toFile().getAbsolutePath());
            installVerifier.executeGoal("install");

            Verifier verifier = new Verifier(logicProjectDir.toFile().getAbsolutePath());
            verifier.executeGoal("verify");

            // Then
            assertThat(getLog(installVerifier)).doesNotContain("[ERROR]", "[FATAL]");
            assertThat(getLog(verifier)).doesNotContain("[ERROR]", "[FATAL]");
            String expectedVersion = givenBranch.replace("/", "-") + "-gitVersioning";
            //        assertThat(log).contains("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve("target/").resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel).satisfies(it -> assertSoftly(softly -> {
                softly.assertThat(it.getModelVersion()).isEqualTo(logicPomModel.getModelVersion());
                softly.assertThat(it.getGroupId()).isEqualTo(logicPomModel.getGroupId());
                softly.assertThat(it.getArtifactId()).isEqualTo(logicPomModel.getArtifactId());
                softly.assertThat(it.getVersion()).isEqualTo(expectedVersion);
                softly.assertThat(it.getProperties()).doesNotContainKeys(
                        "git.commit",
                        "git.ref"
                );
            }));
        }
    }

    private String getLog(Verifier verifier) throws IOException {
        return new String(Files.readAllBytes(Paths.get(verifier.getBasedir(), verifier.getLogFileName())));
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

    private File writeExtensionConfigFile(Path projectDir, Configuration config) throws Exception {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        File configFile = mvnDotDir.resolve("maven-git-versioning-extension.xml").toFile();
        new XmlMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(configFile, config);
        return configFile;
    }

    private Model writeModel(File pomFile, Model pomModel) throws IOException {
        MavenUtil.writeModel(pomFile, pomModel);
        return pomModel;
    }
}
