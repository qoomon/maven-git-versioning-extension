/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.brouillard.oss.jgitver;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component( role = AbstractMavenLifecycleParticipant.class, hint = "jgitver" )
public class JGitverExtension extends AbstractMavenLifecycleParticipant {
    @Requirement
    private Logger logger;
    
    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        logger.info("jgitver-maven-plugin is about to change project version");
        
        MavenProject rootProject = session.getTopLevelProject();
        
        String newVersion = calculateVersionForProject(rootProject);
        rootProject.setVersion(newVersion);
    }

    private String calculateVersionForProject(MavenProject rootProject) {
        GitVersionCalculator gvc = null;
        
        try {
        gvc = GitVersionCalculator.location(rootProject.getBasedir());
        return gvc.setAutoIncrementPatch(true)
                .setUseDistance(true)
                .setUseGitCommitId(false)
                .getVersion();
        } finally {
            if (gvc != null) {
                try {
                    gvc.close();
                } catch (Exception e) {
                    logger.warn("could not close jgitver delegate properly");
                    logger.debug("GitVersionCalculator#close() sent an error", e);
                }
            }
        }
    }
}
