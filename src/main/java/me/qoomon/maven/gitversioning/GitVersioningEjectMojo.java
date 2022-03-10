package me.qoomon.maven.gitversioning;

import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.util.stream.Collectors.toMap;

/**
 * Build plugin to set project POM file path to git versioned POM file.
 * <p>
 * utilized by {@link ModelProcessor}
 */
@Mojo(name = GitVersioningEjectMojo.GOAL,
        defaultPhase = LifecyclePhase.VALIDATE,
        threadSafe = true)
public class GitVersioningEjectMojo extends AbstractMojo {

    static final String GOAL = "eject";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public synchronized void execute() throws MojoExecutionException {
        getLog().debug("remove version build plugin");
        project.getModel().getBuild().getPlugins().remove(GitVersioningPomMojo.asPlugin());
        project.getOriginalModel().getBuild().getPlugins().remove(GitVersioningPomMojo.asPlugin());
    }
}
