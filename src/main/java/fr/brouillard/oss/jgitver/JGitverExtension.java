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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "jgitver")
public class JGitverExtension extends AbstractMavenLifecycleParticipant {
    @Requirement
    private Logger logger;

    @Requirement
    private PlexusContainer container;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        logger.info("jgitver-maven-plugin is about to change project(s) version(s)");

        MavenProject rootProject = session.getTopLevelProject();
        
        String initialVersion = rootProject.getVersion();
        String newVersion = calculateVersionForProject(rootProject);

        Map<GAV, String> newProjectVersions = new LinkedHashMap<>();
        
        // Let's modify in memory resolved projects model
        for (MavenProject project: session.getAllProjects()) {
            GAV projectGAV = GAV.from(project);      // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
            
            // First the project itself
            project.setVersion(newVersion);
            project.getArtifact().setVersion(newVersion);
            project.getBuild().setFinalName(rootProject.getBuild().getFinalName().replaceAll(Pattern.quote(initialVersion), newVersion));
            
            newProjectVersions.put(projectGAV, newVersion);
            
            // No need to worry about parent link, because model is in memory
            
            // TODO dependencyManagement?
            // TODO dependencies?
        }
        
        // Then attache modified POM files to the projects so install/deployed files contains new version
        for (MavenProject project: session.getAllProjects()) {
            try {
                Model model = loadInitialModel(project.getFile());
                GAV initalProjectGAV = GAV.from(model);     // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
                model.setVersion(newProjectVersions.get(initalProjectGAV));
                
                if (model.getParent() != null) {
                    GAV parentGAV = GAV.from(model.getParent());    // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
                    
                    if (newProjectVersions.keySet().contains(parentGAV)) {
                        // parent has been modified
                        model.getParent().setVersion(newProjectVersions.get(parentGAV));
                    }
                }
                
                // TODO dependencyManagement?
                // TODO dependencies?
                File newPom = createPomDumpFile();
                writeModelPom(model, newPom);
                project.setPomFile(newPom);
            } catch (IOException | XmlPullParserException ex) {
                throw new MavenExecutionException("cannot write new POM file for " + project.getGroupId() + "::", ex);
            }
        }
        
        newProjectVersions.entrySet().forEach(e -> logger.info("    " + e.getKey().toString() + " -> " + e.getValue()));
    }

    private Model loadInitialModel(File pomFile) throws IOException, XmlPullParserException {
        try (FileReader fileReader = new FileReader(pomFile)) {
            return new MavenXpp3Reader().read(fileReader);
        }
    }

    private void writeModelPom(Model mavenModel, File pomFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            new MavenXpp3Writer().write(fileWriter, mavenModel);
        }
    }

    private File createPomDumpFile() throws IOException {
        File tmp = File.createTempFile("pom", ".jgitver-maven-plugin");
        tmp.deleteOnExit();
        return tmp;
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
