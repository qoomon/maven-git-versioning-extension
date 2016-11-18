package com.qoomon.maven.extension.branchversioning;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.util.Properties;

/**
 * Created by qoomon on 18/11/2016.
 */
public class ExtensionUtil {

    public static Plugin projectPlugin() {
        Plugin plugin = new Plugin();
        try (InputStream inputStream = BranchVersioningFormatCheckMojo.class.getResourceAsStream("/mavenMeta.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            plugin.setGroupId(properties.getProperty("project.groupId"));
            plugin.setArtifactId(properties.getProperty("project.artifactId"));
            plugin.setVersion(properties.getProperty("project.version"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return plugin;
    }


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
