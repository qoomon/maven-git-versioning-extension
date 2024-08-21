package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import me.qoomon.gitversioning.commons.GitRefType;
import me.qoomon.maven.gitversioning.Configuration.PatchDescription;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static me.qoomon.gitversioning.commons.GitRefType.BRANCH;
import static me.qoomon.gitversioning.commons.GitRefType.TAG;
import static me.qoomon.maven.gitversioning.GitVersioningModelProcessor.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;
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

    private static Verifier getVerifier(Path projectDir) throws VerificationException {
        return new Verifier(projectDir.toFile().getAbsolutePath(), true);
    }


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
