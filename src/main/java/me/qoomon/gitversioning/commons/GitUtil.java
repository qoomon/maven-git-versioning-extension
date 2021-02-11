package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

public final class GitUtil {

    public static String NO_COMMIT = "0000000000000000000000000000000000000000";

    public static Status status(Repository repository) {
        try {
            return Git.wrap(repository).status().call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    public static String branch(Repository repository) throws IOException {
        String branch = repository.getBranch();
        if (ObjectId.isId(branch)) {
            return null;
        }
        return branch;
    }

    public static List<String> tag_pointsAt(Repository repository, String revstr) throws IOException {
        ObjectId rev = repository.resolve(revstr);

        List<String> tagNames = new ArrayList<>();
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix(R_TAGS)) {
            ref = repository.getRefDatabase().peel(ref);
            ObjectId refObjectId;
            if (ref.isPeeled() && ref.getPeeledObjectId() != null) {
                refObjectId = ref.getPeeledObjectId();
            } else {
                refObjectId = ref.getObjectId();
            }
            if (refObjectId.equals(rev)) {
                String tagName = ref.getName().replaceFirst("^" + R_TAGS, "");
                tagNames.add(tagName);
            }
        }
        return tagNames;
    }

    public static String revParse(Repository repository, String revstr) throws IOException {
        ObjectId rev = repository.resolve(revstr);
        if (rev == null) {
            return NO_COMMIT;
        }
        return rev.getName();
    }

    public static long revTimestamp(Repository repository, String revstr) throws IOException {
        ObjectId rev = repository.resolve(revstr);
        if (rev == null) {
            return 0;
        }
        // The timestamp is expressed in seconds since epoch...
        return repository.parseCommit(rev).getCommitTime();
    }

    public static GitSituation situation(File directory) throws IOException {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(directory);
        if (repositoryBuilder.getGitDir() == null) {
            return null;
        }
        try (Repository repository = repositoryBuilder.build()) {
            String headCommit = GitUtil.revParse(repository, HEAD);
            long headCommitTimestamp = GitUtil.revTimestamp(repository, HEAD);
            String headBranch = GitUtil.branch(repository);
            List<String> headTags = GitUtil.tag_pointsAt(repository, HEAD);
            boolean isClean = GitUtil.status(repository).isClean();
            return new GitSituation(repositoryBuilder.getGitDir(), headCommit, headCommitTimestamp, headBranch, headTags, isClean);
        }
    }
}