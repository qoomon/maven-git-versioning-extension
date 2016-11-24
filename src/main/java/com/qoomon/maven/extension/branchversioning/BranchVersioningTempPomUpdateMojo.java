// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package com.qoomon.maven.extension.branchversioning;

import com.google.common.io.Files;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Works in conjunction with BranchVersioningModelProcessor.
 */
@Mojo(name = BranchVersioningTempPomUpdateMojo.GOAL,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class BranchVersioningTempPomUpdateMojo extends AbstractMojo {

    public static final String GOAL = "temp-pom-update";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {

        if (mavenSession.getCurrentProject().isExecutionRoot()) {
            // remove plugin from top level original project model
            logger.debug("remove plugin");
            mavenSession.getCurrentProject().getOriginalModel().getBuild()
                    .removePlugin(ExtensionUtil.projectPlugin());
        }

        try {
            temporaryOverridePomFileFromModel(mavenSession.getCurrentProject());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attach temporary POM files to the projects so install and deployed files contains new version.
     *
     * @param project maven project
     * @throws IOException if project model cannot be write correctly
     */
    public void temporaryOverridePomFileFromModel(MavenProject project) throws IOException {

        File tmpPomFile = File.createTempFile("pom_"
                        + project.getGroupId() + "_"
                        + project.getArtifactId() + "_"
                        + project.getVersion() + "_",
                ".xml");
        tmpPomFile.deleteOnExit();

        ExtensionUtil.writeModel(project.getOriginalModel(), tmpPomFile);

        logger.info(project.getArtifact() + " temporary override pom file with " + tmpPomFile);

        project.setPomFile(tmpPomFile);
    }

}
