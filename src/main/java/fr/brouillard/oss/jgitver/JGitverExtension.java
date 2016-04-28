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

import java.util.Optional;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "jgitver")
public class JGitverExtension extends AbstractMavenLifecycleParticipant {
    @Requirement
    private Logger logger;

    @Requirement
    private PlexusContainer container;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        logger.info("jgitver-maven-plugin is about to change project version");

        MavenProject rootProject = session.getTopLevelProject();
        GAV rootProjectInitialGAV = GAV.from(rootProject);      // SUPPRESS CHECKSTYLE AbbreviationAsWordInName

        String newVersion = calculateVersionForProject(rootProject);
        rootProject.setVersion(newVersion);
        rootProject.getArtifact().setVersion(newVersion);
        
        logger.info("    " + rootProjectInitialGAV.toString() + " -> " + newVersion);
    }

    private String calculateVersionForProject(MavenProject rootProject) throws MavenExecutionException {
        GitVersionCalculator gvc = null;

        try {
            gvc = GitVersionCalculator.location(rootProject.getBasedir());

            Plugin plugin = rootProject.getPlugin("fr.brouillard.oss:jgitver-maven-plugin");
            
            JGitverPluginConfiguration pluginConfig =
                    new JGitverPluginConfiguration(
                        Optional.ofNullable(plugin)
                        .map(Plugin::getConfiguration)
                        .map(Xpp3Dom.class::cast)
                    );
            
            gvc.setAutoIncrementPatch(pluginConfig.autoIncrementPatch())
                .setUseDistance(pluginConfig.useCommitDistance())
                .setUseGitCommitId(pluginConfig.useGitCommitId())
                .setGitCommitIdLength(pluginConfig.gitCommitIdLength())
                .setNonQualifierBranches(pluginConfig.nonQualifierBranches());

            return gvc.getVersion();
        } finally {
            if (gvc != null) {
                try {
                    gvc.close();
                } catch (Exception ex) {
                    logger.warn("could not close jgitver delegate properly");
                    logger.debug("GitVersionCalculator#close() sent an error", ex);
                }
            }
        }
    }
}
