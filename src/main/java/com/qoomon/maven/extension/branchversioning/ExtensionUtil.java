package com.qoomon.maven.extension.branchversioning;

import org.apache.maven.model.Plugin;

import java.io.IOException;
import java.io.InputStream;
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
}
