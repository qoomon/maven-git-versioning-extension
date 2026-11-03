package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Model;
import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.qoomon.gitversioning.commons.GitRefType.BRANCH;
import static me.qoomon.maven.gitversioning.GitVersioningModelProcessor.GIT_VERSIONING_POM_NAME;
import static me.qoomon.maven.gitversioning.MavenUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the extension against different Maven distributions, see {@code unpack-it-maven-distributions} in pom.xml.
 * <p>
 * Core extensions run in a class realm of Maven's core, so they are affected by changes of Maven internals
 * (e.g. Maven 3.10 no longer ships maven-shared-utils).
 */
class MavenCompatibilityIT {

    private static final String ESC = "\u001B[";
    private static final String EXTENSION_ID = "me.qoomon:maven-git-versioning-extension:" + BuildProperties.projectVersion();

    @TempDir
    Path projectDir;

    final Model pomModel = new Model() {{
        setModelVersion("4.0.0");
        setGroupId("test");
        setArtifactId("test-artifact");
        setVersion("0.0.0");
    }};

    static Stream<Path> mavenHomes() throws IOException {
        Path distributionsDir = Paths.get(System.getProperty("it.maven.distributions.dir", "target/it-maven-distributions"));
        if (!Files.isDirectory(distributionsDir)) {
            throw new IllegalStateException("Maven distributions directory " + distributionsDir.toAbsolutePath() + " does not exist, "
                    + "it is created in phase 'generate-test-resources' - run './mvnw install' first");
        }
        List<Path> mavenHomes;
        try (Stream<Path> paths = Files.list(distributionsDir)) {
            mavenHomes = paths.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }
        assertThat(mavenHomes).as("Maven distributions in " + distributionsDir).isNotEmpty();
        return mavenHomes.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mavenHomes")
    void branchVersioning(Path mavenHome) throws Exception {
        try (Git git = Git.init().setInitialBranch("feature/test").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            givenProject();

            // When
            Verifier verifier = executeMaven(mavenHome, "verify");

            // Then
            verifier.verifyErrorFreeLog();
            String expectedVersion = "feature-test-gitVersioning";
            verifier.verifyTextInLog("Building " + pomModel.getArtifactId() + " " + expectedVersion);
            Model gitVersionedPomModel = readModel(projectDir.resolve(GIT_VERSIONING_POM_NAME).toFile());
            assertThat(gitVersionedPomModel.getVersion()).isEqualTo(expectedVersion);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mavenHomes")
    void logHeader_colorDisabled(Path mavenHome) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            givenProject();

            // When
            Verifier verifier = executeMaven(mavenHome, "-Dstyle.color=never", "validate");

            // Then
            verifier.verifyErrorFreeLog();
            assertThat(extensionLogHeader(verifier))
                    .contains(" " + EXTENSION_ID + " [core extension] ")
                    .doesNotContain(ESC);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mavenHomes")
    void logHeader_colorEnabled(Path mavenHome) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            givenProject();

            // When
            Verifier verifier = executeMaven(mavenHome, "-Dstyle.color=always", "validate");

            // Then
            verifier.verifyErrorFreeLog();
            // the actual style is Maven's choice, see logHeader_customStyle
            assertThat(extensionLogHeader(verifier)).contains(ESC);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mavenHomes")
    void logHeader_customStyle(Path mavenHome) throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(projectDir.toFile()).call()) {
            // Given
            git.commit().setMessage("initial commit").setAllowEmpty(true).call();
            givenProject();

            // When
            // Maven 3.10+ uses JLine style syntax
            String magenta = mavenVersion(mavenHome).compareTo(new DefaultArtifactVersion("3.10")) >= 0 ? "fg:magenta" : "magenta";
            Verifier verifier = executeMaven(mavenHome, "-Dstyle.color=always", "-Dstyle.mojo=" + magenta, "validate");

            // Then
            verifier.verifyErrorFreeLog();
            assertThat(extensionLogHeader(verifier)).contains(ESC + "35m" + EXTENSION_ID);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private void givenProject() throws Exception {
        MavenUtil.writeModel(projectDir.resolve("pom.xml").toFile(), pomModel);
        writeExtensionsFile();
        writeExtensionConfigFile(new Configuration() {{
            RefPatchDescription branchDescription = new RefPatchDescription();
            branchDescription.type = BRANCH;
            branchDescription.pattern = ".*";
            branchDescription.version = "${ref}-gitVersioning";
            refs.list.add(branchDescription);
        }});
    }

    private static DefaultArtifactVersion mavenVersion(Path mavenHome) {
        return new DefaultArtifactVersion(mavenHome.getFileName().toString().replaceFirst("^apache-maven-", ""));
    }

    private Verifier executeMaven(Path mavenHome, String... cliArguments) throws Exception {
        Path settingsFile = writeIsolatedSettings();
        Verifier verifier;
        // Verifier reads the Maven distribution to use from system property 'maven.home'
        String originalMavenHome = System.getProperty("maven.home");
        System.setProperty("maven.home", mavenHome.toAbsolutePath().toString());
        try {
            verifier = new Verifier(projectDir.toFile().getAbsolutePath(), settingsFile.toString(), true);
        } finally {
            if (originalMavenHome != null) {
                System.setProperty("maven.home", originalMavenHome);
            } else {
                System.clearProperty("maven.home");
            }
        }
        // fork to not mix up different Maven versions within the test JVM
        verifier.setForkJvm(true);
        verifier.addCliArguments("-s", settingsFile.toString());
        verifier.addCliArguments(cliArguments);
        verifier.execute();
        System.err.println(String.join("\n", verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false)));
        return verifier;
    }

    private String extensionLogHeader(Verifier verifier) throws Exception {
        return verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false).stream()
                .filter(line -> line.contains(EXTENSION_ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("extension log header not found"));
    }

    private Path writeIsolatedSettings() throws IOException {
        Path settingsFile = projectDir.resolve("test-settings.xml");
        Path localRepo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        Files.write(settingsFile, ("" +
                "<settings>\n" +
                "  <localRepository>" + localRepo + "</localRepository>\n" +
                "</settings>\n").getBytes());
        return settingsFile;
    }

    private void writeExtensionsFile() throws IOException {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        Files.write(mvnDotDir.resolve("extensions.xml"), ("" +
                "<extensions>\n" +
                "  <extension>\n" +
                "    <groupId>me.qoomon</groupId>\n" +
                "    <artifactId>maven-git-versioning-extension</artifactId>\n" +
                "    <version>" + BuildProperties.projectVersion() + "</version>\n" +
                "  </extension>\n" +
                "</extensions>").getBytes());
    }

    private void writeExtensionConfigFile(Configuration config) throws Exception {
        Path mvnDotDir = Files.createDirectories(projectDir.resolve(".mvn"));
        new XmlMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(mvnDotDir.resolve("maven-git-versioning-extension.xml").toFile(), config);
    }
}
