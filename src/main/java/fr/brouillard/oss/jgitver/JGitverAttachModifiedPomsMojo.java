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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Works in conjunction with JGitverModelProcessor.
 */
@Mojo(name = JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS,
        defaultPhase = LifecyclePhase.VERIFY,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class JGitverAttachModifiedPomsMojo extends AbstractMojo {
    public static final String GOAL_ATTACH_MODIFIED_POMS = "attach-modified-poms";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

    private boolean executed = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (executed) {
            // We don't need to attach modified poms anymore.
            // We need to attach modified poms only once
            return;
        }
        executed = true;

        try {
            File projectBasedir = mavenSession.getRequest().getMultiModuleProjectDirectory();
            logger.error("projectBasedir: " + projectBasedir);
            String branchVersion = "sickOfItAll"; // TODO read from git

            Map<Artifact, String> newProjectVersions = new HashMap<>();
            for (MavenProject project : mavenSession.getAllProjects()) {
                newProjectVersions.put(project.getArtifact(), branchVersion);
            }
            JGitverUtils.attachModifiedPomFilesToTheProject(
                    mavenSession.getAllProjects(),
                    newProjectVersions, mavenSession,
                    logger);

        } catch (XmlPullParserException | IOException ex) {
            throw new MojoExecutionException("Unable to execute goal: " + JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS, ex);
        }
    }
}
