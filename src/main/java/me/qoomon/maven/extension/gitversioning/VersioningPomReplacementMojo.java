package me.qoomon.maven.extension.gitversioning;

import me.qoomon.maven.BuildProperties;
import me.qoomon.maven.GAV;
import me.qoomon.maven.ModelUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

import javax.inject.Inject;
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

    private MavenSession mavenSession;

    private Logger logger;

    @Inject
    public VersioningPomReplacementMojo(Logger logger, MavenSession mavenSession) {
        this.mavenSession = mavenSession;
        this.logger = logger;
    }

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {

        try {
            MavenProject currentProject = mavenSession.getCurrentProject();

            GAV gav = GAV.of(currentProject);

            logger.debug(gav + "remove plugin");

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
    public void temporaryOverridePomFileFromModel(MavenProject project) throws IOException {

        File tmpPomFile = new File(project.getBuild().getDirectory(), "git_pom.xml");
        tmpPomFile.getParentFile().mkdirs();

        ModelUtil.writeModel(project.getOriginalModel(), tmpPomFile);

        logger.debug(project.getArtifact() + " temporary override pom file with " + tmpPomFile);

        project.setPomFile(tmpPomFile);
    }

}
