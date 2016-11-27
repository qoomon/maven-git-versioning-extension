package com.qoomon.maven;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildProperties {

    private static final String FILE_PATH = "mavenBuild.properties";

    private static final String PROJECT_GROUP_ID;

    private static final String PROJECT_ARTIFACT_ID;

    private static final String PROJECT_VERSION;

    static {
        Properties properties = getBuildProperties();

        PROJECT_GROUP_ID = properties.getProperty("project.groupId");
        PROJECT_ARTIFACT_ID = properties.getProperty("project.artifactId");
        PROJECT_VERSION = properties.getProperty("project.version");
    }

    private static Properties getBuildProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = BuildProperties.class.getClassLoader().getResource(FILE_PATH).openStream()) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    public static String projectGroupId() {
        return PROJECT_GROUP_ID;
    }

    public static String projectArtifactId() {
        return PROJECT_ARTIFACT_ID;
    }

    public static String projectVersion() {
        return PROJECT_VERSION;
    }

    public static void main(String[] args) {
        getBuildProperties().list(System.out);
    }
}