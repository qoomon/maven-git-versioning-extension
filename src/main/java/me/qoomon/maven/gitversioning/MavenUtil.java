package me.qoomon.maven.gitversioning;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;

/**
 * Created by qoomon on 18/11/2016.
 */
final class MavenUtil {

    /**
     * Read model from pom file
     *
     * @param pomFile pomFile
     * @return Model
     * @throws IOException IOException
     */
    static Model readModel(File pomFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(pomFile)) {
            return new MavenXpp3Reader().read(inputStream);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes model to pom file
     *
     * @param pomFile pomFile
     * @param model   model
     * @throws IOException IOException
     */
    static void writeModel(File pomFile, Model model) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            new MavenXpp3Writer().write(fileWriter, model);
        }
    }

    /**
     * checks if <code>pomFile</code> is part of a project
     *
     * @param pomFile the pom file
     * @return true if <code>pomFile</code> is part of a project
     */
    static boolean isProjectPom(File pomFile) {
        return pomFile != null
                && pomFile.exists()
                && pomFile.isFile()
                // only project pom files ends in .xml, pom files from dependencies from repositories ends in .pom
                && pomFile.getName().endsWith(".xml");
    }

}
