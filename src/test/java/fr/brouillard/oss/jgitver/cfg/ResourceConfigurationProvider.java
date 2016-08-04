package fr.brouillard.oss.jgitver.cfg;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import com.google.common.io.Resources;

public class ResourceConfigurationProvider implements Closeable {
    private File configurationDirectory;

    public ResourceConfigurationProvider(File directory) {
        configurationDirectory = directory;
    }

    /**
     * Builds a {@link ResourceConfigurationProvider} object by saving the given resource as a "jgitver.config.xml" file
     * inside a newly created directory.
     * 
     * @param configurationResource the resource to read as "jgitver.config.xml" content
     * @return a non null {@link ResourceConfigurationProvider}
     */
    public static ResourceConfigurationProvider fromResource(String configurationResource) {
        File dir = com.google.common.io.Files.createTempDir();
        try {
            File mvn = new File(dir, ".mvn");
            mvn.mkdir();
            File f = new File(mvn, "jgitver.config.xml");
            final FileOutputStream fos = new FileOutputStream(f);
            Resources.copy(
                    ResourceConfigurationProvider.class.getResource(configurationResource),
                    fos);
            fos.close();
            return new ResourceConfigurationProvider(dir);
        } catch (Exception ex) {
            try {
                deleteDirectory(dir);
            } catch (IOException ignore) {
                // cannot do anything
            }
            throw new IllegalStateException(
                    "cannot create configuration directory for resource: " + configurationResource, ex);
        }
    }

    public File getConfigurationDirectory() {
        return configurationDirectory;
    }

    @Override
    public void close() throws IOException {
        deleteDirectory(configurationDirectory);
    }

    private static void deleteDirectory(File fileDir) throws IOException {
        Path directory = fileDir.toPath();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
