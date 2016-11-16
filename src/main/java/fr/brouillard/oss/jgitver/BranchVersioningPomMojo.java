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

import com.google.common.collect.Maps;
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
import java.util.Map;

/**
 * Works in conjunction with BranchVersioningModelProcessor.
 */
@Mojo(name = BranchVersioningPomMojo.GOAL_ATTACH_MODIFIED_POMS,
        defaultPhase = LifecyclePhase.VERIFY,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class BranchVersioningPomMojo extends AbstractMojo {
    public static final String GOAL_ATTACH_MODIFIED_POMS = "attach-modified-poms";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {
        if (!mavenSession.getCurrentProject().isExecutionRoot()) {
            return ; // We need to attach modified poms only once
        }

        try {
            String branchVersion = BranchVersioningVersionProvider.getVersion();

            Map<GAV, String> newProjectVersions = Maps.newHashMap();
            for (MavenProject project : mavenSession.getAllProjects()) {
                newProjectVersions.put(GAV.of(project.getArtifact()), branchVersion);
            }

            for (MavenProject project : mavenSession.getAllProjects()) {
                attachModifiedPomFileToProject(newProjectVersions, project);
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("Error", ex);
        }
    }

    /**
     * Attach modified POM files to the projects so install/deployed files contains new version.
     *
     * @param project          maven project
     * @param branchVersionMap branch versions.
     * @throws IOException if project model cannot be read correctly
     */
    public void attachModifiedPomFileToProject(Map<GAV, String> branchVersionMap, MavenProject project) throws IOException {

        GAV projectGavOriginal = GAV.of(project.getArtifact());
        logger.info("generate tmp pom file @ " + projectGavOriginal);

        Model projectModelTmp = readModel(project.getFile());

        if (branchVersionMap.containsKey(projectGavOriginal)) {
            projectModelTmp.setVersion(branchVersionMap.get(projectGavOriginal));
        }

        // check for parent modification
        if (projectModelTmp.getParent() != null) {
            GAV parentGAV = GAV.of(project.getParent().getArtifact());
            if (branchVersionMap.containsKey(parentGAV)) {
                projectModelTmp.getParent().setVersion(branchVersionMap.get(parentGAV));
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
