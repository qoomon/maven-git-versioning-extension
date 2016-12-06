package com.qoomon.maven.extension.branchversioning;

import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.GAV;
import com.qoomon.maven.ModelUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Temporarily replace original pom files with pom files generated from in memory project models.
 * <p>
 * !!! DO NOT ADD THIS PLUGIN MANUALLY TO POM !!!
 * <p>
 * utilized by {@link BranchVersioningModelProcessor}
 */
@Mojo(name = BranchVersioningPomReplacementMojo.GOAL,
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class BranchVersioningPomReplacementMojo extends AbstractMojo {

    static final String GOAL = "pom-replacement";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {

        MavenProject currentProject = mavenSession.getCurrentProject();

        GAV gav = GAV.of(currentProject);

        logger.debug(gav + "remove plugin");

        currentProject.getOriginalModel().getBuild().removePlugin(this.asPlugin());

        temporaryOverridePomFileFromModel(currentProject);
    }

    static Plugin asPlugin() {
        Plugin plugin = new Plugin();
        plugin.setGroupId(BuildProperties.projectGroupId());
        plugin.setArtifactId(BuildProperties.projectArtifactId());
        plugin.setVersion(BuildProperties.projectVersion());
        return plugin;
    }

    /**
     * Attach temporary POM files to the projects so install and deployed files contains new version.
     *
     * @param project maven project
     */
    public void temporaryOverridePomFileFromModel(MavenProject project) {
        try {

            File tmpPomFile = new File(project.getBuild().getDirectory(), "branch_pom.xml");
            tmpPomFile.getParentFile().mkdirs();

            ModelUtil.writeModel(project.getOriginalModel(), tmpPomFile);

            logger.debug(project.getArtifact() + " temporary override pom file with " + tmpPomFile);

            project.setPomFile(tmpPomFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
