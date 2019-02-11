package me.qoomon.maven.gitversioning;

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

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link GitVersioningModelProcessor}
 */
@Mojo(name = GitVersioningPomReplacementMojo.GOAL,
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class GitVersioningPomReplacementMojo extends AbstractMojo {

    static final String GOAL = "pom-replacement";
    static final String GIT_VERSIONED_POM_FILE_NAME = ".git-versioned.pom.xml";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject currentProject;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession mavenSession;


    @Override
    public synchronized void execute() throws MojoExecutionException {
        try {
            GAV gav = GAV.of(currentProject.getModel());
            getLog().debug(gav + "remove plugin");
            currentProject.getOriginalModel().getBuild().removePlugin(asPlugin());

            File gitVersionedPomFile = new File(currentProject.getBasedir(), GIT_VERSIONED_POM_FILE_NAME);
            getLog().debug(currentProject.getArtifact() + " replace project pom file with " + gitVersionedPomFile);
            ModelUtil.writeModel(currentProject.getOriginalModel(), gitVersionedPomFile);
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
