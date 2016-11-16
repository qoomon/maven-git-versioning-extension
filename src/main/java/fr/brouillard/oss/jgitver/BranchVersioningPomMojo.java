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
package fr.brouillard.oss.jgitver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;

/**
 * Works in conjunction with BranchVersioningModelProcessor.
 */
@Mojo(name = BranchVersioningPomMojo.GOAL_ATTACH_TEMP_POMS,
        defaultPhase = LifecyclePhase.VERIFY,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class BranchVersioningPomMojo extends AbstractMojo {

    public static final String GOAL_ATTACH_TEMP_POMS = "attach-temp-poms";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

//    @Component
//    private BranchVersioningModelProcessor foo;

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {
        logger.error("--- BranchVersioningPomMojo ---");
        if (!mavenSession.getCurrentProject().isExecutionRoot()) {
            return; // We need to attach modified poms only once
        }

        try {
            for (MavenProject project : mavenSession.getAllProjects()) {
                attachTempBranchPomFileToProject(project);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Pom versioning failed!", e);
        }
    }

    /**
     * Attach temporary POM files to the projects so install and deployed files contains new version.
     *
     * @param project maven project
     * @throws IOException if project model cannot be read correctly
     */
    public void attachTempBranchPomFileToProject(MavenProject project) throws IOException {
        Model projectModelTmp = readModel(project.getFile());
        GAV projectGav = GAV.of(projectModelTmp);

        logger.info("generate tmp pom file @ " + projectGav);

        projectModelTmp.setVersion(BranchVersioningModelProcessor.getVersion(projectGav));

        // check for parent modification
        if (projectModelTmp.getParent() != null) {
            GAV parentGAV = GAV.of(project.getParent());
            if (BranchVersioningModelProcessor.hasVersion(parentGAV)) {
                projectModelTmp.getParent().setVersion(BranchVersioningModelProcessor.getVersion(projectGav));
            }
        }

        File tmpPomFile = File.createTempFile("pom", ".xml");
        tmpPomFile.deleteOnExit();

        writeModel(projectModelTmp, tmpPomFile);

        logger.info("    temp pom file " + tmpPomFile);

        project.setPomFile(tmpPomFile);
    }


    /**
     * Read model from pom file
     *
     * @param pomFile pomFile
     * @return Model
     * @throws IOException IOException
     */
    public Model readModel(File pomFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(pomFile)) {
            return new MavenXpp3Reader().read(inputStream);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Writes model to pom file
     *
     * @param model   model
     * @param pomFile pomFile
     * @throws IOException IOException
     */
    public void writeModel(Model model, File pomFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            new MavenXpp3Writer().write(fileWriter, model);
        }
    }

}
