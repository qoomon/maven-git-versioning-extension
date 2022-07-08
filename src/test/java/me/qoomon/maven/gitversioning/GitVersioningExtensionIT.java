package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import me.qoomon.gitversioning.commons.GitRefType;
import me.qoomon.maven.gitversioning.Configuration.PatchDescription;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static me.qoomon.gitversioning.commons.GitRefType.BRANCH;
import static me.qoomon.gitversioning.commons.GitRefType.TAG;
import static me.qoomon.maven.gitversioning.GitVersioningModelProcessor.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GitVersioningExtensionIT {

    @TempDir
    Path projectDir;

    final Model pomModel = new Model() {{
        setModelVersion("4.0.0");
        setGroupId("test");
        setArtifactId("test-artifact");
        setVersion("0.0.0");
    }};


    @Test
    void noGitVersioning() throws Exception {
        // Given
        writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile(projectDir);
        writeExtensionConfigFile(projectDir, new Configuration());

        // When
        Verifier verifier = getVerifier(projectDir);
        verifier.displayStreamBuffers();
        verifier.executeGoal("verify");

        // Then
        verifier.verifyErrorFreeLog();
        String expectedVersion = "0.0.0";
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        verifier.verifyTextInLog("[WARNING] skip - project is not part of a git repository");
        verifier.assertFileNotPresent(GIT_VERSIONING_POM_NAME);
    }

    private static Verifier getVerifier(Path projectDir) throws VerificationException {
        return new Verifier(projectDir.toFile().getAbsolutePath(), true);
    }

    @Test
    void disableVersioning_cliOption() throws Exception {
        // Given
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration());

            // When
            Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath()) {{
                addCliOption("-Dversioning.disable");
            }};
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("skip - versioning is disabled by command option");
            String expectedVersion = pomModel.getVersion();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
            verifier.assertFileNotPresent(GIT_VERSIONING_POM_NAME);
        }
    }

    @Test
    void revVersioning_noCommit() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                rev = createRevVersionDescription();
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "0000000000000000000000000000000000000000";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void revVersioning() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                rev = createRevVersionDescription();
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = givenCommit.getName();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void branchVersioning() throws Exception {

        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void tagVersioning_atBranch() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setAnnotated(true).setName("v1.0.0").call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.considerTagsOnBranches = true;
                refs.list.add(createTagVersionDescription());
                refs.list.add(createBranchVersionDescription());
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "v1.0.0-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void tagVersioning_detachedHead() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            Ref tag = git.tag().setAnnotated(true).setName("v1.0.0").call();
            git.checkout().setName(tag.getName()).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
                refs.list.add(createTagVersionDescription());
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "v1.0.0-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void tagVersioning_lightweightTag_atBranch() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            Ref tag = git.tag().setName("v1.0.0").call();
            git.checkout().setName(tag.getName()).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createTagVersionDescription());
                refs.list.add(createBranchVersionDescription());
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "v1.0.0-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void tagVersioning_considerTagsOnlyIfHeadIsDetached() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setName("v1.0.0").call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createTagVersionDescription());
                refs.list.add(createBranchVersionDescription());
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "master-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void tagVersioning_environmentVariable() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createTagVersionDescription());
                refs.list.add(createBranchVersionDescription());
            }});

            // When
            Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath()) {{
                setEnvironmentVariable("VERSIONING_GIT_BRANCH", "v1.0.0");
            }};
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "v1.0.0-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void revVersioning_multiModuleProject() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
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
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${ref}-SNAPSHOT"));
            }});

            // api module
            Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
            writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setParent(new Parent() {{
                    setGroupId(pomModel.getGroupId());
                    setArtifactId(pomModel.getArtifactId());
                    setVersion(pomModel.getVersion());
                }});
                setArtifactId("api");
                setVersion(pomModel.getVersion());
            }});

            // logic module
            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setParent(new Parent() {{
                    setGroupId(pomModel.getGroupId());
                    setArtifactId(pomModel.getArtifactId());
                    setVersion(pomModel.getVersion());
                }});
                setArtifactId("logic");
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "master-SNAPSHOT";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);

            Model apiGitVersionedPomModel = readModel(apiProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(apiGitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);

            Model logicGitVersionedPomModel = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(logicGitVersionedPomModel.getVersion()).isEqualTo(null);
        }
    }

    @Test
    void revVersioning_multiModuleProject_ambiguous_artifactId() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            pomModel.setPackaging("pom");
            pomModel.addModule("logic");
            pomModel.addModule("another-group-logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${ref}-SNAPSHOT"));
            }});

            // logic module
            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setParent(new Parent() {{
                    setGroupId(pomModel.getGroupId());
                    setArtifactId(pomModel.getArtifactId());
                    setVersion(pomModel.getVersion());
                }});
                setArtifactId("logic");
            }});

            // another logic module
            Path anotherGroupLogicProjectDir = Files.createDirectories(projectDir.resolve("another-group-logic"));
            writeModel(anotherGroupLogicProjectDir.resolve("pom.xml").toFile(), new Model() {{
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
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "master-SNAPSHOT";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);

            Model apiGitVersionedPomModelLogic = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(apiGitVersionedPomModelLogic.getVersion()).isEqualTo(null);

            Model duplicateArtifactIdGitVersionedPomModelLogic = readModel(anotherGroupLogicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(duplicateArtifactIdGitVersionedPomModelLogic.getVersion()).isEqualTo(null);
        }
    }

    @Test
    void branchVersioning_multiModuleProject_noParent() throws Exception {

        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
            }});

            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("logic");
            }});

            // When
            Verifier verifier = getVerifier(logicProjectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void branchVersioning_multiModuleProject_withExternalParent() throws Exception {
        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("api");
            pomModel.addModule("logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            Configuration extensionConfig = new Configuration();
            extensionConfig.refs.list.add(createBranchVersionDescription());
            writeExtensionConfigFile(projectDir, extensionConfig);

            Parent externalParent = new Parent() {{
                setGroupId("org.springframework.boot");
                setArtifactId("spring-boot-starter-parent");
                setVersion("2.1.3.RELEASE");
            }};

            // api module
            Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
            writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("api");
                setParent(externalParent);
            }});

            // logic module
            Path logicProjectDir = Files.createDirectories(projectDir.resolve("logic"));
            Model logicPomModel = writeModel(logicProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setGroupId(pomModel.getGroupId());
                setVersion(pomModel.getVersion());
                setArtifactId("logic");
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void branchVersioning_multiModuleProject_withAggregationAndParent() throws Exception {
        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("parent");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
            }});

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
            Verifier verifier = getVerifier(logicProjectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void dependencyUpdates_multiModuleProject() throws Exception {
        try (Git git = Git.init().setInitialBranch("test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            pomModel.setPackaging("pom");
            pomModel.addModule("api");
            pomModel.addModule("logic");

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);

            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
            }});

            Path apiProjectDir = Files.createDirectories(projectDir.resolve("api"));
            Model apiPomModel = writeModel(apiProjectDir.resolve("pom.xml").toFile(), new Model() {{
                setModelVersion(pomModel.getModelVersion());
                setParent(new Parent() {{
                    setGroupId(pomModel.getGroupId());
                    setArtifactId(pomModel.getArtifactId());
                    setVersion(pomModel.getVersion());
                }});
                setArtifactId("api");
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
                addDependency(new Dependency() {{
                    setGroupId(pomModel.getGroupId());
                    setArtifactId("api");
                    setVersion(pomModel.getVersion());
                }});
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "test-gitVersioning";
            verifier.verifyTextInLog("Building " + logicPomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedLogicPomModel = readModel(logicProjectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedLogicPomModel).satisfies(it -> assertSoftly(softly -> {
                softly.assertThat(it.getDependencies().get(0)).satisfies(dependency -> {
                    softly.assertThat(dependency.getArtifactId()).isEqualTo(apiPomModel.getArtifactId());
                    softly.assertThat(dependency.getVersion()).isEqualTo(expectedVersion);
                });
            }));
        }
    }

    @Test
    void branchVersioning_WithBuildTime() throws Exception {

        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${build.timestamp.year}${build.timestamp.month}${build.timestamp.day}-gitVersioning"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.executeGoal("verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    // TODO add tests for property updates


    // -----------------------------------------------------------------------------------------------------------------

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

    private PatchDescription createRevVersionDescription() {
        PatchDescription versionDescription = new PatchDescription();
        versionDescription.version = "${commit}";
        return versionDescription;
    }

    private RefPatchDescription createBranchVersionDescription() {
        return createVersionDescription(BRANCH, "${ref}-gitVersioning");
    }

    private RefPatchDescription createTagVersionDescription() {
        return createVersionDescription(TAG, "${ref}-gitVersioning");
    }

    private RefPatchDescription createVersionDescription(GitRefType type, String format) {
        RefPatchDescription refVersionDescription = new RefPatchDescription();
        refVersionDescription.type = type;
        refVersionDescription.pattern = ".*";
        refVersionDescription.version = format;
        return refVersionDescription;
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
