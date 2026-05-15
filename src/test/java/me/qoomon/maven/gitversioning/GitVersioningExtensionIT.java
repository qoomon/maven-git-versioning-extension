package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import me.qoomon.gitversioning.commons.GitRefType;
import me.qoomon.maven.gitversioning.Configuration.PatchDescription;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static me.qoomon.gitversioning.commons.GitRefType.BRANCH;
import static me.qoomon.gitversioning.commons.GitRefType.TAG;
import static me.qoomon.maven.gitversioning.GitVersioningModelProcessor.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.eclipse.jgit.lib.Constants.MASTER;

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
        verifier.addCliArgument("verify");
        verifier.execute();

        // Then
        System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
        verifier.verifyErrorFreeLog();
        String expectedVersion = "0.0.0";
        verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        verifier.verifyTextInLog("[WARNING] skip - project is not part of a git repository");
        verifier.verifyFileNotPresent(GIT_VERSIONING_POM_NAME);
    }

    private static Verifier getVerifier(Path projectDir) throws Exception {
        Path settingsFile = writeIsolatedSettings(projectDir);
        Verifier verifier = new Verifier(projectDir.toFile().getAbsolutePath(), settingsFile.toString(), true);
        verifier.addCliArguments("-s", settingsFile.toString());
        return verifier;
    }

    private static Path writeIsolatedSettings(Path projectDir) throws IOException {
        Path settingsFile = projectDir.resolve("test-settings.xml");
        Path localRepo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        Files.write(settingsFile, ("" +
                "<settings>\n" +
                "  <localRepository>" + localRepo + "</localRepository>\n" +
                "</settings>\n").getBytes());
        return settingsFile;
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
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("-Dversioning.disable");
            verifier.addCliArguments("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("skip - versioning is disabled by command option");
            String expectedVersion = pomModel.getVersion();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
            verifier.verifyFileNotPresent(GIT_VERSIONING_POM_NAME);
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }
    @Test
    void branchVersioningPluginWithDeps() throws Exception {

        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            
            
            // Put there just some dummy plugin with some dummy dependency
            Plugin plugin = new Plugin();            
            Dependency dependency = new Dependency();
            dependency.setGroupId(pomModel.getGroupId());
            dependency.setArtifactId(pomModel.getArtifactId());
            dependency.setVersion(pomModel.getVersion());
            plugin.setArtifactId("maven-enforcer-plugin");
            plugin.addDependency(dependency);            
            Build build = new Build();
            build.addPlugin(plugin);
            pomModel.setBuild(build);
            
            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createBranchVersionDescription());
            }});
            
            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            System.out.println(new String(Files.readAllBytes(projectDir.resolve(GIT_VERSIONING_POM_NAME))));
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
            assertThat(gitVersionedPomModel.getBuild().getPlugins().get(0).getDependencies().get(0).getVersion()).isEqualTo(expectedVersion);
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            Verifier verifier = getVerifier(projectDir);
            verifier.setEnvironmentVariable("VERSIONING_GIT_BRANCH", "v1.0.0");
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
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
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void apply_emptyRepoGivesExpectedPlusDistanceResult() throws Exception {
        // This can only be a local build so the specifics of the build number don't really matter, but 0 makes reasonable sense
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag.version.label.plus.describe.distance}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "0";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }

    }

    @Test
    void apply_singleCommitUntaggedRepoGivesExpectedPlusDistanceResult() throws Exception {
        // This can only be a local build so the specifics of the build number don't really matter, but 0 makes reasonable sense
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag.version.label.plus.describe.distance}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "1";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }

    }

    @Test
    void apply_NoCommitsSinceLastTagGivesExpectedPlusDistanceResult() throws Exception {
        // This can only be a local build so the specifics of the build number don't really matter, but 0 makes reasonable sense
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            String givenTag = "2.0.4-677";
            git.tag().setName(givenTag).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag.version.label.plus.describe.distance}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "677";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }

    }

    @Test
    void apply_TwoCommitsSinceLastTagGivesExpectedPlusDistanceResult() throws Exception {
        // This can only be a local build so the specifics of the build number don't really matter, but 0 makes reasonable sense
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            String givenTag = "2.0.4-677";
            git.tag().setName(givenTag).call();
            git.commit().setMessage("commit two").setAllowEmpty(true).call();
            git.commit().setMessage("commit three").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag.version.label.plus.describe.distance}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "679";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void apply_DescribeDistanceSnapshotRelease() throws Exception {
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setName("2.0.4").call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag}${describe.distance.snapshot}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            String expectedVersion = "2.0.4";
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);

            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        }
    }

    @Test
    void apply_DescribeDistanceSnapshot() throws Exception {
        // given
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setName("2.0.4").call();
            git.commit().setMessage("commit one").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag}${describe.distance.snapshot}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            String expectedVersion = "2.0.4-SNAPSHOT";
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);

            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
        }
    }

    @Test
    void apply_TagOnMergedBranchWithFirstParent() throws Exception {
        apply_TagOnMerged(true);
    }

    @Test
    void apply_TagOnMergedBranchWithoutFirstParent() throws Exception {
        apply_TagOnMerged(false);
    }

    private void apply_TagOnMerged(final boolean firstParent) throws Exception {
        String tagOnMaster = "2.0.4-tag-on-master";
        String tagOnBranch = "2.0.4-tag-on-branch";
        try (Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call()) {
            RevCommit initialCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setName("2.0.4-tag-on-initial").call();
            git.commit().setMessage("commit on master").setAllowEmpty(true).call();
            git.tag().setName(tagOnMaster).call();
            git.checkout().setStartPoint(initialCommit).setCreateBranch(true).setName("branch").call();
            // make sure the commit on the branch is newer
            Thread.sleep(2000);
            RevCommit branchCommit = git.commit().setMessage("commit on branch").setAllowEmpty(true).call();
            git.tag().setName(tagOnBranch).call();
            git.checkout().setName(MASTER).call();
            git.merge().include(branchCommit).setFastForward(MergeCommand.FastForwardMode.NO_FF).setCommit(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                RefPatchDescription versionDescription = createVersionDescription(BRANCH, "${describe.tag.version}");
                versionDescription.describeTagFirstParent = firstParent;
                refs.list.add(versionDescription);
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = firstParent ? tagOnMaster : tagOnBranch;
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }
    // TODO add tests for property updates

    @Test
    void apply_UseDescribeTagVersionLabel() throws Exception {

        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setAnnotated(true).setName("v1.0.0-label").call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, "${describe.tag.version.label}"));
            }});

            // When
            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            // Then
            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            String expectedVersion = "label";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @Test
    void apply_UseDescribeTagVersion_FourSegmentIncluded() throws Exception {
        assertDescribeTagVersionResolves("1.0.0.3", "${describe.tag.version}", "1.0.0.3");
    }

    @Test
    void apply_UseDescribeTagVersionBuild() throws Exception {
        assertDescribeTagVersionResolves("1.0.0.3", "${describe.tag.version.build}", "3");
    }

    @Test
    void apply_UseDescribeTagVersionBuildNext() throws Exception {
        assertDescribeTagVersionResolves("1.0.0.3", "${describe.tag.version.build.next}", "4");
    }

    @Test
    void apply_UseDescribeTagVersionBuild_threeSegmentTag() throws Exception {
        assertDescribeTagVersionResolves("1.0.0", "${describe.tag.version.build.next}", "1");
    }

    @Test
    void apply_UseDescribeTagVersionBuildPlusDistance() throws Exception {
        assertDescribeTagVersionResolvesWithDistance("1.0.0.3", 2, "${describe.tag.version.build.plus.describe.distance}", "5");
    }

    @Test
    void apply_UseDescribeTagVersionBuildNextPlusDistance() throws Exception {
        assertDescribeTagVersionResolvesWithDistance("1.0.0.3", 2, "${describe.tag.version.build.next.plus.describe.distance}", "6");
    }

    @Test
    void apply_UseDescribeTagVersionNext_threeSegmentTag() throws Exception {
        assertDescribeTagVersionResolves("1.2.3", "${describe.tag.version.next}", "1.2.4");
    }

    @Test
    void apply_UseDescribeTagVersionNext_fourSegmentTag() throws Exception {
        assertDescribeTagVersionResolves("1.2.3.4", "${describe.tag.version.next}", "1.2.3.5");
    }

    @Test
    void apply_UseDescribeTagVersionNext_twoSegmentTag() throws Exception {
        assertDescribeTagVersionResolves("1.2", "${describe.tag.version.next}", "1.3");
    }

    @Test
    void apply_UseDescribeTagVersionNext_labelWithTrailingNum() throws Exception {
        assertDescribeTagVersionResolves("1.2.3-RC1", "${describe.tag.version.next}", "1.2.3-RC2");
    }

    @Test
    void apply_UseDescribeTagVersionNext_labelWithMultiDigitNum() throws Exception {
        assertDescribeTagVersionResolves("1.2.3-RC10", "${describe.tag.version.next}", "1.2.3-RC11");
    }

    @Test
    void apply_UseDescribeTagVersionNext_labelNoTrailingNum() throws Exception {
        assertDescribeTagVersionResolves("1.2.3-alpha", "${describe.tag.version.next}", "1.2.3-alpha1");
    }

    @Test
    void apply_UseDescribeTagVersionNext_fourSegmentWithLabel() throws Exception {
        assertDescribeTagVersionResolves("1.2.3.4-RC1", "${describe.tag.version.next}", "1.2.3.4-RC2");
    }

    @Test
    void apply_UseVersionBuild() throws Exception {
        pomModel.setVersion("1.0.0.3-SNAPSHOT");
        assertProjectVersionResolves("${version.build}", "3");
    }

    @Test
    void apply_UseVersionBuildNext() throws Exception {
        pomModel.setVersion("1.0.0.3-SNAPSHOT");
        assertProjectVersionResolves("${version.build.next}", "4");
    }

    @Test
    void apply_UseVersionNext_threeSegment() throws Exception {
        pomModel.setVersion("1.2.3");
        assertProjectVersionResolves("${version.next}", "1.2.4");
    }

    @Test
    void apply_UseVersionNext_fourSegment() throws Exception {
        pomModel.setVersion("1.2.3.4");
        assertProjectVersionResolves("${version.next}", "1.2.3.5");
    }

    @Test
    void apply_VersionBuildGroup_inProjectVersionPattern_throws() throws Exception {
        assertProjectVersionPatternGroupCollides("build");
    }

    @Test
    void apply_VersionNextGroup_inProjectVersionPattern_throws() throws Exception {
        assertProjectVersionPatternGroupCollides("next");
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void assertDescribeTagVersionResolves(String tagName, String versionTemplate, String expectedVersion) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setAnnotated(true).setName(tagName).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, versionTemplate));
            }});

            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    private void assertDescribeTagVersionResolvesWithDistance(String tagName, int commitsPastTag, String versionTemplate, String expectedVersion) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            git.tag().setAnnotated(true).setName(tagName).call();
            for (int i = 0; i < commitsPastTag; i++) {
                git.commit().setMessage("commit " + (i + 1)).setAllowEmpty(true).call();
            }

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, versionTemplate));
            }});

            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    private void assertProjectVersionResolves(String versionTemplate, String expectedVersion) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                refs.list.add(createVersionDescription(BRANCH, versionTemplate));
            }});

            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");
            verifier.execute();

            System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);

            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    private void assertProjectVersionPatternGroupCollides(String groupName) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();

            writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
            writeExtensionsFile(projectDir);
            writeExtensionConfigFile(projectDir, new Configuration() {{
                projectVersionPattern = "(?<" + groupName + ">\\d+).*";
                refs.list.add(createVersionDescription(BRANCH, "${version}"));
            }});

            Verifier verifier = getVerifier(projectDir);
            verifier.addCliArgument("verify");

            assertThatThrownBy(verifier::execute).isInstanceOf(VerificationException.class);
            verifier.verifyTextInLog("project version pattern capture group can not be named '" + groupName + "'");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private File writeExtensionsFile(Path projectDir) throws IOException {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        return Files.write(mvnDotDir.resolve("extensions.xml"), ("" +
                "<extensions>\n" +
                "  <extension>\n" +
                "    <groupId>me.qoomon</groupId>\n" +
                "    <artifactId>maven-git-versioning-extension</artifactId>\n" +
                "    <version>" + BuildProperties.projectVersion() + "</version>\n" +
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
