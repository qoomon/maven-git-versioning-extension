package me.qoomon.maven.gitversioning;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static java.lang.Boolean.parseBoolean;

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link ModelProcessor}
 */

@Mojo(name = VersioningMojo.GOAL,
      defaultPhase = LifecyclePhase.INITIALIZE,
      threadSafe = true)
public class VersioningMojo extends AbstractMojo {

    public static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";
    static final String GOAL = "git-versioning";
    static final String propertyKeyPrefix = VersioningMojo.class.getName() + ".";
    static final String propertyKeyUpdatePom = "updatePom";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public synchronized void execute() throws MojoExecutionException {
        try {
            // read plugin properties
            final boolean configUpdatePom = parseBoolean(
                    project.getProperties().getProperty(propertyKeyPrefix + propertyKeyUpdatePom));

            // remove plugin and properties
            getLog().debug(project.getModel().getArtifactId() + " remove this plugin and plugin properties from model");
            Model originalModel = project.getOriginalModel();
            if (originalModel.getBuild() != null) {
                originalModel.getBuild()
                        .removePlugin(asPlugin());
            }
            if (originalModel.getProperties() != null) {
                originalModel.getProperties()
                        .entrySet().removeIf(property -> ((String) property.getKey()).startsWith(propertyKeyPrefix));
            }

            getLog().info("Generating git versioned POM");

            File pomFile = project.getFile();
            Document gitVersionedPomDocument = readXml(pomFile);
            Element projectElement = gitVersionedPomDocument.getChild("project");

            Element versionElement = projectElement.getChild("version");
            if (versionElement != null) {
                versionElement.setText(project.getVersion());
            }

            Element propertiesElement = projectElement.getChild("properties");
            if (propertiesElement != null) {
                for (final Element propertyElement : propertiesElement.getChildren()) {
                    propertyElement.setText(project.getOriginalModel().getProperties().getProperty(propertyElement.getName()));
                }
            }

            // TODO
            // update version within dependencies, dependency management, plugins, plugin management

            Element parentElement = projectElement.getChild("parent");
            if (parentElement != null) {
                Element parentVersionElement = parentElement.getChild("version");
                parentVersionElement.setText(project.getParent().getVersion());
            }

            File gitVersionedPomFile = new File(project.getBuild().getDirectory(), GIT_VERSIONING_POM_NAME);
            Files.createDirectories(gitVersionedPomFile.getParentFile().toPath());
            writeXml(gitVersionedPomFile, gitVersionedPomDocument);

            // replace pom file with git-versioned pom file within current session
            project.setPomFile(gitVersionedPomFile);

            if (configUpdatePom) {
                getLog().info("Updating original POM");
                Files.copy(gitVersionedPomFile.toPath(), pomFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
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
