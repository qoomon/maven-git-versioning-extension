package me.qoomon.maven.gitversioning;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static me.qoomon.maven.gitversioning.MavenUtil.*;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link ModelProcessor}
 */

@Mojo(name = VersioningMojo.GOAL,
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        threadSafe = true)
public class VersioningMojo extends AbstractMojo {

    static final String GOAL = "git-versioning";
    static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject currentProject;

    @Override
    public synchronized void execute() throws MojoExecutionException {
        try {
            getLog().info("Generating git versioned POM of project " + GAV.of(currentProject.getOriginalModel()) + "...");

            getLog().debug(currentProject.getModel().getArtifactId() + "remove this plugin from model");
            currentProject.getOriginalModel().getBuild().removePlugin(VersioningMojo.asPlugin());

            // generate git-versioned pom from current pom
            Document gitVersionedPom = readXml(currentProject.getFile());

            Element versionElement = gitVersionedPom.getChild("/project/version");
            if (versionElement != null) {
                versionElement.setText(currentProject.getVersion());
            }
            Element parentVersionElement = gitVersionedPom.getChild("/project/parent/version");
            if (parentVersionElement != null && isProjectPom(currentProject.getParent().getFile())) {
                parentVersionElement.setText(currentProject.getVersion());
            }

            File gitVersionedPomFile = new File(currentProject.getBuild().getDirectory(), GIT_VERSIONING_POM_NAME);
            Files.createDirectories(gitVersionedPomFile.getParentFile().toPath());
            writeXml(gitVersionedPomFile,gitVersionedPom);

            // replace session pom with git-versioned pom
            currentProject.setPomFile(gitVersionedPomFile);
        } catch (Exception e) {
            throw new MojoExecutionException("Git Versioning Pom Replacement Mojo", e);
        }
    }

    private static File writeXml(final File file, final Document gitVersionedPom) throws IOException {
        Files.write(file.toPath(), gitVersionedPom.toXML().getBytes());
        return file;
    }

    private static Document readXml(File file) throws IOException {
        String pomXml = new String(Files.readAllBytes(file.toPath()));
        XMLParser parser = new XMLParser();
        return parser.parse(new XMLStringSource(pomXml));
    }

    static Plugin asPlugin() {
        Plugin plugin = new Plugin();
        plugin.setGroupId(BuildProperties.projectGroupId());
        plugin.setArtifactId(BuildProperties.projectArtifactId());
        plugin.setVersion(BuildProperties.projectVersion());
        return plugin;
    }
}
