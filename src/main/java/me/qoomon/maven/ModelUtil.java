package me.qoomon.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;

/**
 * Created by qoomon on 18/11/2016.
 */
public class ModelUtil {

    /**
     * Read model from pom file
     *
     * @param pomFile pomFile
     * @return Model
     * @throws IOException IOException
     */
    public static Model readModel(File pomFile) throws IOException {
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
    public static void writeModel(Model model, File pomFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            new MavenXpp3Writer().write(fileWriter, model);
        }
    }

}
