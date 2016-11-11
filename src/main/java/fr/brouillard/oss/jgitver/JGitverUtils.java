// @formatter:off
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
// @formatter:on
package fr.brouillard.oss.jgitver;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import fr.brouillard.oss.jgitver.metadata.Metadatas;

/**
 * Misc utils used by the plugin.
 */
public final class JGitverUtils {
    public static final String EXTENSION_PREFIX = "jgitver";
    public static final String EXTENSION_GROUP_ID = "fr.brouillard.oss";
    public static final String EXTENSION_ARTIFACT_ID = "jgitver-maven-plugin";

    private JGitverUtils() {
    }

    /**
     * Loads initial model from pom file.
     *
     * @param pomFile pomFile.
     * @return Model.
     * @throws IOException            IOException.
     * @throws XmlPullParserException XmlPullParserException.
     */
    public static Model loadInitialModel(File pomFile) throws IOException, XmlPullParserException {
        try (FileReader fileReader = new FileReader(pomFile)) {
            return new MavenXpp3Reader().read(fileReader);
        }
    }

    /**
     * Creates temporary file to save updated pom mode.
     *
     * @return File.
     * @throws IOException IOException.
     */
    public static File createPomDumpFile() throws IOException {
        File tmp = File.createTempFile("pom", ".jgitver-maven-plugin.xml");
        tmp.deleteOnExit();
        return tmp;
    }

    /**
     * Writes updated model to temporary pom file.
     *
     * @param mavenModel mavenModel.
     * @param pomFile    pomFile.
     * @throws IOException IOException.
     */
    public static void writeModelPom(Model mavenModel, File pomFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            new MavenXpp3Writer().write(fileWriter, mavenModel);
        }
    }

    /**
     * Changes basedir(dangerous).
     *
     * @param project project.
     * @param initialBaseDir initialBaseDir.
     * @throws NoSuchFieldException NoSuchFieldException.
     * @throws IllegalAccessException IllegalAccessException.
     */
    // It breaks the build process, because it changes the basedir to 'tmp'/... where other plugins are not able to
    // find the classes and resources during the phases.
    public static void changeBaseDir(MavenProject project, File initialBaseDir) throws NoSuchFieldException,
            IllegalAccessException {
        Field basedirField = project.getClass().getField("basedir");
        basedirField.setAccessible(true);
        basedirField.set(project, initialBaseDir);
    }

    /**
     * Changes the pom file of the given project.
     * @param project the project to change the pom
     * @param newPom the pom file to set on the project
     * @param logger a logger to use 
     */
    public static void setProjectPomFile(MavenProject project, File newPom, Logger logger) {
        try {
            project.setPomFile(newPom);
        } catch (Throwable unused) {
            logger.warn("maven version might be <= 3.2.4, changing pom file using old mechanism");
            File initialBaseDir = project.getBasedir();
            project.setFile(newPom);
            File newBaseDir = project.getBasedir();
            try {
                if (!initialBaseDir.getCanonicalPath().equals(newBaseDir.getCanonicalPath())) {
                    changeBaseDir(project, initialBaseDir);
                }
            } catch (Exception ex) {
                GAV gav = GAV.from(project);
                logger.warn("cannot reset basedir of project " + gav.toString(), ex);
            }
        }
    }





    /**
     * Attach modified POM files to the projects so install/deployed files contains new version.
     *
     * @param projects           projects.
     * @param newProjectVersions newProjectVersions.
     * @param mavenSession the current maven build session
     * @param logger the logger to report to 
     * @throws IOException if project model cannot be read correctly
     * @throws XmlPullParserException if project model cannot be interpreted correctly
     */
    public static void attachModifiedPomFilesToTheProject(List<MavenProject> projects, Map<GAV, String>
            newProjectVersions, MavenSession mavenSession, Logger logger) throws IOException, XmlPullParserException {
        for (MavenProject project : projects) {
            Model model = project.getOriginalModel();
            GAV initalProjectGAV = GAV.from(model);

            logger.debug("about to change file pom for: " + initalProjectGAV);

            if (newProjectVersions.containsKey(initalProjectGAV)) {
                model.setVersion(newProjectVersions.get(initalProjectGAV));
            }

            if (model.getParent() != null) {
                GAV parentGAV = GAV.from(model.getParent());

                if (newProjectVersions.keySet().contains(parentGAV)) {
                    // parent has been modified
                    model.getParent().setVersion(newProjectVersions.get(parentGAV));
                }
            }

            File newPom = createPomDumpFile();
            writeModelPom(model, newPom);
            logger.debug("    new pom file created for " + initalProjectGAV + " under " + newPom);

            setProjectPomFile(project, newPom, logger);
            logger.debug("    pom file set");
        }
    }
}
