package me.qoomon.maven.gitversioning;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Build plugin to set project POM file path to git versioned POM file.
 * <p>
 * utilized by {@link ModelProcessor}
 */
@Mojo(name = GitVersioningMojo.GOAL,
        defaultPhase = LifecyclePhase.INITIALIZE,
        threadSafe = true)
public class GitVersioningMojo extends AbstractMojo {

    static final String GOAL = "git-versioning";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public synchronized void execute() {
        File gitVersionedPomFile = new File(project.getBasedir(), GitVersioningModelProcessor.GIT_VERSIONING_POM_NAME);
        project.setPomFile(gitVersionedPomFile);

        getLog().debug("remove version build plugin");
        project.getModel().getBuild().getPlugins().remove(asPlugin());
        project.getOriginalModel().getBuild().getPlugins().remove(asPlugin());
    }

    static Plugin asPlugin() {
        Plugin plugin = new Plugin();
        plugin.setGroupId(BuildProperties.projectGroupId());
        plugin.setArtifactId(BuildProperties.projectArtifactId());
        plugin.setVersion(BuildProperties.projectVersion());
        return plugin;
    }
}
