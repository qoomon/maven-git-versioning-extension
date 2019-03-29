package me.qoomon.maven.gitversioning;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;

import static me.qoomon.maven.gitversioning.MavenUtil.*;

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

            // read model from pom file because we dont want to apply any changes mady by plugins, except the version
            Model pomFileModel = readModel(currentProject.getFile());
            if (pomFileModel.getVersion() != null) {
                pomFileModel.setVersion(currentProject.getVersion());
            }
            if (pomFileModel.getParent() != null && isProjectPom(currentProject.getParent().getFile())) {
                pomFileModel.getParent().setVersion(currentProject.getVersion());
            }

            // write git-versioned pom file
            File gitVersionedPomFile = new File(currentProject.getBuild().getDirectory(), GIT_VERSIONING_POM_NAME);
            Files.createDirectories(gitVersionedPomFile.getParentFile().toPath());
            writeModel(gitVersionedPomFile, pomFileModel);
            // update project pom file
            currentProject.setPomFile(gitVersionedPomFile);
        } catch (Exception e) {
            throw new MojoExecutionException("Git Versioning Pom Replacement Mojo", e);
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
