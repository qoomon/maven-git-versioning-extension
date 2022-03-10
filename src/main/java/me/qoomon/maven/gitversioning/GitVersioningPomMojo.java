package me.qoomon.maven.gitversioning;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import org.apache.maven.model.*;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.toMap;
import static me.qoomon.maven.gitversioning.MavenUtil.readXml;
import static me.qoomon.maven.gitversioning.MavenUtil.writeXml;

/**
 * Build plugin to set project POM file path to git versioned POM file.
 * <p>
 * utilized by {@link ModelProcessor}
 */
@Mojo(name = GitVersioningPomMojo.GOAL,
        defaultPhase = LifecyclePhase.VALIDATE,
        threadSafe = true)
public class GitVersioningPomMojo extends AbstractMojo {

    static final String GOAL = "pom";

    static final String GIT_VERSIONING_POM_NAME = ".git-versioned-pom.xml";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "query.url", required = true)
    private boolean updatePom;


    @Override
    public synchronized void execute() throws MojoExecutionException {
        getLog().debug("remove version build plugin");
        project.getModel().getBuild().getPlugins().remove(GitVersioningPomMojo.asPlugin());
        project.getOriginalModel().getBuild().getPlugins().remove(GitVersioningPomMojo.asPlugin());

        final File gitVersionedPomFile;
        if (updatePom) {
            gitVersionedPomFile = project.getModel().getPomFile();
            getLog().info("updating original POM file " + gitVersionedPomFile);
        } else { // TODO read from config
            gitVersionedPomFile = new File(project.getBasedir(), GIT_VERSIONING_POM_NAME);
            getLog().info("generate git versioned POM file " + gitVersionedPomFile);
        }

        try {
            writePomFile(gitVersionedPomFile, project.getOriginalModel());
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }

        project.setPomFile(gitVersionedPomFile);
    }

    private void writePomFile(File pomFile, Model projectModel) throws IOException {

        // read original pom file
        Document gitVersionedPomDocument = readXml(projectModel.getPomFile());
        Element projectElement = gitVersionedPomDocument.getChild("project");

        // update project
        updateParentVersion(projectElement, projectModel.getParent());
        updateVersion(projectElement, projectModel);
        updatePropertyValues(projectElement, projectModel);
        updateDependencyVersions(projectElement, projectModel);
        updatePluginVersions(projectElement, projectModel.getBuild(), projectModel.getReporting());

        updateProfiles(projectElement, projectModel.getProfiles());

        writeXml(pomFile, gitVersionedPomDocument);
    }

    // ---- generate git versioned pom file ----------------------------------------------------------------------------


    private static void updateParentVersion(Element projectElement, Parent parent) {
        Element parentElement = projectElement.getChild("parent");
        if (parentElement != null) {
            Element parentVersionElement = parentElement.getChild("version");
            parentVersionElement.setText(parent.getVersion());
        }
    }

    private static void updateVersion(Element projectElement, Model projectModel) {
        Element versionElement = projectElement.getChild("version");
        if (versionElement != null) {
            versionElement.setText(projectModel.getVersion());
        }
    }

    private void updatePropertyValues(Element element, ModelBase model) {
        // properties section
        Element propertiesElement = element.getChild("properties");
        if (propertiesElement != null) {
            propertiesElement.getChildren().forEach(propertyElement -> {
                if (propertyElement != null) {
                    String pomPropertyValue = propertyElement.getText();
                    String modelPropertyValue = (String) model.getProperties().get(propertyElement.getName());
                    if (!Objects.equals(modelPropertyValue, pomPropertyValue)) {
                        propertyElement.setText(modelPropertyValue);
                    }
                }
            });
        }
    }

    private static void updateDependencyVersions(Element element, ModelBase model) {
        // dependencies section
        {
            Element dependenciesElement = element.getChild("dependencies");
            if (dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, model.getDependencies());
            }
        }
        // dependencyManagement section
        Element dependencyManagementElement = element.getChild("dependencyManagement");
        if (dependencyManagementElement != null) {
            Element dependenciesElement = dependencyManagementElement.getChild("dependencies");
            if (dependenciesElement != null) {
                updateDependencyVersions(dependenciesElement, model.getDependencyManagement().getDependencies());
            }
        }
    }

    private static void updateDependencyVersions(Element dependenciesElement, List<Dependency> dependencies) {
        forEachPair(dependenciesElement.getChildren(), dependencies, (dependencyElement, dependency) -> {
            // sanity check
            if (!Objects.equals(dependency.getManagementKey(), getDependencyManagementKey(dependencyElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model dependencies order "
                        + dependency.getManagementKey() +" <> "+ getDependencyManagementKey(dependencyElement));
            }

            Element dependencyVersionElement = dependencyElement.getChild("version");
            if (dependencyVersionElement != null) {
                dependencyVersionElement.setText(dependency.getVersion());
            }
        });
    }

    private static String getDependencyManagementKey(Element element) {
        Element groupId = element.getChild("groupId");
        Element artifactId = element.getChild("artifactId");
        Element type = element.getChild("type");
        Element classifier = element.getChild("classifier");
        return (groupId != null ? groupId.getText().trim() : "")
                + ":" + (artifactId != null ? artifactId.getText().trim() : "")
                + ":" + (type != null ? type.getText().trim() : "jar")
                + (classifier != null ? ":" + classifier.getText().trim() : "");
    }

    private static void updatePluginVersions(Element projectElement, BuildBase build, Reporting reporting) {
        // build section
        Element buildElement = projectElement.getChild("build");
        if (buildElement != null) {
            // plugins section
            {
                Element pluginsElement = buildElement.getChild("plugins");
                if (pluginsElement != null) {
                    updatePluginVersions(pluginsElement, build.getPlugins());
                }
            }
            // pluginManagement section
            Element pluginsManagementElement = buildElement.getChild("pluginsManagement");
            if (pluginsManagementElement != null) {
                Element pluginsElement = pluginsManagementElement.getChild("plugins");
                if (pluginsElement != null) {
                    updatePluginVersions(pluginsElement, build.getPluginManagement().getPlugins());
                }
            }
        }

        Element reportingElement = projectElement.getChild("reporting");
        if (reportingElement != null) {
            // plugins section
            {
                Element pluginsElement = reportingElement.getChild("plugins");
                if (pluginsElement != null) {
                    updateReportPluginVersions(pluginsElement, reporting.getPlugins());
                }
            }
        }
    }

    private static void updatePluginVersions(Element pluginsElement, List<Plugin> plugins) {
        forEachPair(pluginsElement.getChildren(), plugins, (pluginElement, plugin) -> {
            // sanity check
            if (!Objects.equals(plugin.getKey(), getPluginKey(pluginElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model plugin order");
            }

            Element pluginVersionElement = pluginElement.getChild("version");
            if (pluginVersionElement != null) {
                pluginVersionElement.setText(plugin.getVersion());
            }
        });
    }

    private static void updateReportPluginVersions(Element pluginsElement, List<ReportPlugin> plugins) {
        forEachPair(pluginsElement.getChildren(), plugins, (pluginElement, plugin) -> {
            // sanity check
            if (!Objects.equals(plugin.getKey(), getPluginKey(pluginElement))) {
                throw new IllegalArgumentException("Unexpected difference of xml and model plugin order");
            }

            Element pluginVersionElement = pluginElement.getChild("version");
            if (pluginVersionElement != null) {
                pluginVersionElement.setText(plugin.getVersion());
            }
        });
    }

    private static String getPluginKey(Element element) {
        Element groupId = element.getChild("groupId");
        Element artifactId = element.getChild("artifactId");
        return (groupId != null ? groupId.getText().trim() : "org.apache.maven.plugins")
                + ":" + (artifactId != null ? artifactId.getText().trim() : "");
    }

    private void updateProfiles(Element projectElement, List<Profile> profiles) {
        Element profilesElement = projectElement.getChild("profiles");
        if (profilesElement != null) {
            Map<String, Profile> profileMap = profiles.stream()
                    .collect(toMap(Profile::getId, it -> it));
            for (Element profileElement : profilesElement.getChildren("profile")) {
                Profile profile = profileMap.get(profileElement.getChild("id").getText());
                updatePropertyValues(profileElement, profile);
                updateDependencyVersions(profileElement, profile);
                updatePluginVersions(profileElement, profile.getBuild(), profile.getReporting());
            }
        }
    }

    public static <T1, T2> void forEachPair(Collection<T1> collection1, Collection<T2> collection2, BiConsumer<T1, T2> consumer) {
        if (collection1.size() != collection2.size()) {
            throw new IllegalArgumentException("Collections sizes are not equals");
        }

        Iterator<T1> iter1 = collection1.iterator();
        Iterator<T2> iter2 = collection2.iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            consumer.accept(iter1.next(), iter2.next());
        }
    }

    static Plugin asPlugin() {
        Plugin plugin = new Plugin();
        plugin.setGroupId(BuildProperties.projectGroupId());
        plugin.setArtifactId(BuildProperties.projectArtifactId());
        plugin.setVersion(BuildProperties.projectVersion());
        return plugin;
    }
}
