package me.qoomon.maven.gitversioning;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toMap;

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link ModelProcessor}
 */

@Mojo(name = GitVersioningMojo.GOAL,
        defaultPhase = LifecyclePhase.INITIALIZE,
        threadSafe = true)
public class GitVersioningMojo extends AbstractMojo {

    static final String GOAL = "git-versioning";
    static final String propertyKeyPrefix = GitVersioningMojo.class.getName() + ".";
    static final String propertyKeyUpdatePom = propertyKeyPrefix + "updatePom";

    static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public synchronized void execute() throws MojoExecutionException {
        try {
            // read plugin properties
            final boolean updatePom = parseBoolean(project.getProperties().getProperty(propertyKeyUpdatePom));

            getLog().info("Generating git-versioned POM " + GIT_VERSIONING_POM_NAME);

            Model projectModel = project.getOriginalModel();

            // read original pom file
            Document gitVersionedPomDocument = readXml(project.getOriginalModel().getPomFile());
            Element projectElement = gitVersionedPomDocument.getChild("project");

            // update version
            Element versionElement = projectElement.getChild("version");
            if (versionElement != null) {
                versionElement.setText(projectModel.getVersion());
            }

            // update properties
            updatePropertyValues(projectElement, projectModel);

            // update parent version
            Element parentElement = projectElement.getChild("parent");
            if (parentElement != null) {
                Element parentVersionElement = parentElement.getChild("version");
                parentVersionElement.setText(project.getParent().getVersion());
            }

            // update dependency versions
            updateDependencyVersions(projectElement, projectModel);

            // update plugin versions
            updatePluginVersions(projectElement, projectModel);

            // write git-versioned pom file
            File gitVersionedPomFile = new File(project.getBasedir(), GIT_VERSIONING_POM_NAME);
            Files.createDirectories(gitVersionedPomFile.getParentFile().toPath());
            writeXml(gitVersionedPomFile, gitVersionedPomDocument);

            // replace pom file with git-versioned pom file within current session
            project.setPomFile(gitVersionedPomFile);

            if (updatePom) {
                getLog().info("Updating original POM");
                Files.copy(
                        gitVersionedPomFile.toPath(),
                        project.getOriginalModel().getPomFile().toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Git Versioning Pom Replacement Mojo", e);
        }
    }

    private static void updatePropertyValues(Element projectElement, Model projectModel) {
        // properties section
        {
            Element propertiesElement = projectElement.getChild("properties");
            if (propertiesElement != null) {
                updatePropertyValues(propertiesElement, projectModel.getProperties());
            }
        }
        // profiles section
        Element profilesElement = projectElement.getChild("profiles");
        if (profilesElement != null) {
            Map<String, Profile> profileMap = projectModel.getProfiles().stream()
                    .collect(toMap(Profile::getId, it -> it));
            for (Element profileElement : profilesElement.getChildren()) {
                Profile profile = profileMap.get(profileElement.getChild("id").getText());
                // properties section
                {
                    Element propertiesElement = projectElement.getChild("properties");
                    if (propertiesElement != null) {
                        updatePropertyValues(propertiesElement, profile.getProperties());
                    }
                }
            }
        }
    }

    private static void updatePropertyValues(Element propertiesElement, Properties properties) {
        for (Element propertyElement : propertiesElement.getChildren()) {
            propertyElement.setText(properties.getProperty(propertyElement.getName()));
        }
    }

    private static void updateDependencyVersions(Element projectElement, Model projectModel) {
        // dependencies section
        {
            Element dependenciesElement = projectElement.getChild("dependencies");
            if (dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, projectModel.getDependencies());
            }
        }
        // dependencyManagement section
        {
            Element dependencyManagementElement = projectElement.getChild("dependencyManagement");
            if (dependencyManagementElement != null) {
                Element dependenciesElement = dependencyManagementElement.getChild("dependencies");
                if (dependenciesElement != null) {
                    updateDependencyVersions(dependenciesElement, projectModel.getDependencyManagement().getDependencies());
                }
            }
        }
        // profiles section
        Element profilesElement = projectElement.getChild("profiles");
        if (profilesElement != null) {
            Map<String, Profile> profileMap = projectModel.getProfiles().stream()
                    .collect(toMap(Profile::getId, it -> it));
            for (Element profileElement : profilesElement.getChildren("profile")) {
                Profile profile = profileMap.get(profileElement.getChild("id").getText());
                // dependencies section
                {
                    Element dependenciesElement = profileElement.getChild("dependencies");
                    if (dependenciesElement != null) {
                        updateDependencyVersions(dependenciesElement, profile.getDependencies());
                    }
                }
                // dependencyManagement section
                {
                    Element dependencyManagementElement = profileElement.getChild("dependencyManagement");
                    if (dependencyManagementElement != null) {
                        Element dependenciesElement = dependencyManagementElement.getChild("dependencies");
                        if (dependenciesElement != null) {
                            updateDependencyVersions(dependenciesElement, profile.getDependencyManagement().getDependencies());
                        }
                    }
                }
            }
        }
    }

    private static void updateDependencyVersions(Element dependenciesElement, List<Dependency> dependencies) {
        final Map<String, String> dependencyVersionMap = dependencies.stream()
                .filter(it -> it.getVersion() != null)
                .collect(toMap(it -> it.getGroupId() + ":" + it.getArtifactId(), Dependency::getVersion));

        for (Element dependencyElement : dependenciesElement.getChildren()) {
            String dependencyGroupId = dependencyElement.getChild("groupId").getText();
            String dependencyArtifactId = dependencyElement.getChild("artifactId").getText();
            Element dependencyVersionElement = dependencyElement.getChild("version");
            if (dependencyVersionElement != null) {
                dependencyVersionElement.setText(dependencyVersionMap.get(dependencyGroupId + ":" + dependencyArtifactId));
            }
        }
    }

    private static void updatePluginVersions(Element projectElement, Model projectModel) {
        // build section
        {
            Element buildElement = projectElement.getChild("build");
            if (buildElement != null) {
                // plugins section
                {
                    Element pluginsElement = buildElement.getChild("plugins");
                    if (pluginsElement != null) {
                        updatePluginVersions(pluginsElement, projectModel.getBuild().getPlugins());
                    }

                }
                // pluginManagement section
                {
                    Element pluginsManagementElement = buildElement.getChild("pluginsManagement");
                    if (pluginsManagementElement != null) {
                        Element pluginsElement = pluginsManagementElement.getChild("plugins");
                        if (pluginsElement != null) {
                            updatePluginVersions(pluginsElement, projectModel.getBuild().getPluginManagement().getPlugins());
                        }
                    }
                }
            }
        }
        // profiles section
        Element profilesElement = projectElement.getChild("profiles");
        if (profilesElement != null) {
            Map<String, Profile> profileMap = projectModel.getProfiles().stream()
                    .collect(toMap(Profile::getId, it -> it));
            for (Element profileElement : profilesElement.getChildren("profile")) {
                Profile profile = profileMap.get(profileElement.getChild("id").getText());
                // build section
                {
                    Element buildElement = projectElement.getChild("build");
                    if (buildElement != null) {
                        // plugins section
                        {
                            Element pluginsElement = buildElement.getChild("plugins");
                            if (pluginsElement != null) {
                                updatePluginVersions(pluginsElement, profile.getBuild().getPlugins());
                            }

                        }
                        // pluginManagement section
                        {
                            Element pluginsManagementElement = buildElement.getChild("pluginsManagement");
                            if (pluginsManagementElement != null) {
                                Element pluginsElement = pluginsManagementElement.getChild("plugins");
                                if (pluginsElement != null) {
                                    updatePluginVersions(pluginsElement, profile.getBuild().getPluginManagement().getPlugins());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void updatePluginVersions(Element pluginsElement, List<Plugin> plugins) {
        final Map<String, String> pluginVersionMap = plugins.stream()
                .filter(it -> it.getVersion() != null)
                .collect(toMap(it -> it.getGroupId() + ":" + it.getArtifactId(), Plugin::getVersion));

        for (Element pluginElement : pluginsElement.getChildren()) {
            String pluginGroupId = pluginElement.getChild("groupId").getText();
            String pluginArtifactId = pluginElement.getChild("artifactId").getText();
            Element pluginVersionElement = pluginElement.getChild("version");
            if (pluginVersionElement != null) {
                pluginVersionElement.setText(pluginVersionMap.get(pluginGroupId + ":" + pluginArtifactId));
            }
        }
    }

    private static void writeXml(final File file, final Document gitVersionedPom) throws IOException {
        Files.write(file.toPath(), gitVersionedPom.toXML().getBytes());
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
