package me.qoomon.gitversioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static me.qoomon.UncheckedExceptions.unchecked;
import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;
import static org.eclipse.jgit.lib.Constants.*;

public final class GitUtil {

    public static Status status(Repository repository) {
        return unchecked(() -> Git.wrap(repository).status().call());
    }

    public static String branch(Repository repository) {
        ObjectId head = unchecked(() -> repository.resolve(HEAD));
        if (head == null) {
            return MASTER;
        }
        String branch = unchecked(repository::getBranch);
        if (ObjectId.isId(branch)) {
            return null;
        }
        return branch;
    }

    public static List<String> tag_pointsAt(Repository repository, String revstr) {
        ObjectId rev = unchecked(() -> repository.resolve(revstr));
        return unchecked(() -> repository.getRefDatabase().getRefsByPrefix(R_TAGS)).stream()
                .map(ref -> unchecked(() -> repository.getRefDatabase().peel(ref)))
                .filter(ref -> (ref.isPeeled() && ref.getPeeledObjectId() != null ? ref.getPeeledObjectId() : ref.getObjectId()).equals(rev))
                .map(ref -> ref.getName().replaceFirst("^" + R_TAGS, ""))
                .collect(toList());
    }

    public static String revParse(Repository repository, String revstr) {
        ObjectId rev = unchecked(() -> repository.resolve(revstr));
        if (rev == null) {
            return NO_COMMIT;
        }
        return rev.getName();
    }

    public static GitRepoSituation situation(File directory) {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(directory);
        if (repositoryBuilder.getGitDir() == null) {
            throw new IllegalArgumentException(
                    directory + " directory is not a git repository (or any of the parent directories)");
        }
        try (Repository repository = unchecked(repositoryBuilder::build)) {
            boolean headClean = GitUtil.status(repository).isClean();
            String headCommit = GitUtil.revParse(repository, HEAD);
            String headBranch = GitUtil.branch(repository);
            List<String> headTags = GitUtil.tag_pointsAt(repository, HEAD);
            return new GitRepoSituation(headClean, headCommit, headBranch, headTags);
        }
    }
}