package me.qoomon.maven.extension.gitversioning;

import me.qoomon.maven.BuildProperties;
import me.qoomon.maven.ModelUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link VersioningModelProcessor}
 */
@Mojo(name = VersioningPomReplacementMojo.GOAL,
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class VersioningPomReplacementMojo extends AbstractMojo {

    static final String GOAL = "pom-replacement";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject currentProject;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession mavenSession;

    @Override
    public synchronized void execute() throws MojoExecutionException {
        try {
            GAV gav = GAV.of(currentProject);

            getLog().debug(gav + "remove plugin");
            currentProject.getOriginalModel().getBuild().removePlugin(asPlugin());

            temporaryOverridePomFileFromModel(currentProject);
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

    /**
     * Attach temporary POM files to the projects so install and deployed files contains new getVersion.
     *
     * @param project maven project
     * @throws IOException if pom model write fails
     */
    private void temporaryOverridePomFileFromModel(MavenProject project) throws IOException {

        File tmpPomFile = new File(project.getBuild().getDirectory(), "pom.virtual.xml");
        tmpPomFile.getParentFile().mkdirs();

        ModelUtil.writeModel(project.getOriginalModel(), tmpPomFile);

        getLog().debug(project.getArtifact() + " replace project pom file with " + tmpPomFile);

        project.setPomFile(tmpPomFile);
    }

}
