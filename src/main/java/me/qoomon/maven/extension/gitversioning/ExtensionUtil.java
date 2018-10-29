package me.qoomon.maven.extension.gitversioning;

import java.io.File;

import org.apache.maven.execution.MavenExecutionRequest;

/**
 * Created by qoomon on 06/12/2016.
 */
public class ExtensionUtil {

    public static File getConfigFile(MavenExecutionRequest request, String artifactId) {
        File rootProjectDirectory = request.getMultiModuleProjectDirectory();
        return new File(rootProjectDirectory, ".mvn/" + artifactId + ".xml");
    }
}
