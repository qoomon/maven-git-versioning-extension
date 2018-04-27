package me.qoomon.maven.extension.gitversioning;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.Optional;

public class App {

    public static void main(String[] args) throws Exception {
        Repository repository = new FileRepositoryBuilder()
                .findGitDir(new File("/Users/bengt.brodersen/Workspace/git-sandbox"))
                .build();

        ObjectId head = repository.resolve(Constants.HEAD);
        Optional<String> headTag = repository.getTags().values().stream()
                .map(repository::peel)
                .filter(ref -> {
                    ObjectId objectId;
                    if (ref.getPeeledObjectId() != null) {
                        objectId = ref.getPeeledObjectId();
                    } else {
                        objectId = ref.getObjectId();
                    }
                    return objectId.equals(head);
                })
                .map(ref -> ref.getName().replaceFirst("^refs/tags/", ""))
                .sorted((tagLeft, tagRight) -> {
                    DefaultArtifactVersion tagVersionLeft = new DefaultArtifactVersion(tagLeft);
                    DefaultArtifactVersion tagVersionRight = new DefaultArtifactVersion(tagRight);
                    return Math.negateExact(tagVersionLeft.compareTo(tagVersionRight));
                })
                .findFirst();

        if (headTag.isPresent()) {
            System.out.println(headTag.get());
        } else {
            System.out.println("no tag");
        }
    }
}
