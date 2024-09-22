package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Repository.shortenRefName;

public final class GitUtil {

    public static String NO_COMMIT = "0000000000000000000000000000000000000000";

    public static Status status(Repository repository) throws GitAPIException {
        return Git.wrap(repository).status().call();
    }

    public static String branch(Repository repository) throws IOException {
        String branch = repository.getBranch();
        if (ObjectId.isId(branch)) {
            return null;
        }
        return branch;
    }

    public static List<String> tagsPointAt(ObjectId revObjectId, Repository repository) throws IOException {
        return reverseTagRefMap(repository).getOrDefault(revObjectId, emptyList());
    }

    public static GitDescription describe(ObjectId revObjectId, Pattern tagPattern, Repository repository, boolean firstParent, Integer maxDepth) throws IOException {
        Repository commonRepository = worktreesFix_getCommonRepository(repository);
        if (revObjectId == null) {
            return new GitDescription(NO_COMMIT, "root", 0, false);
        }

        Map<ObjectId, List<String>> objectIdListMap = reverseTagRefMap(repository);

        // Walk back commit ancestors looking for tagged one
        try (RevWalk walk = new RevWalk(commonRepository)) {
            walk.setRetainBody(false);
            walk.setFirstParent(firstParent);
            walk.markStart(walk.parseCommit(revObjectId));
            Iterator<RevCommit> walkIterator = walk.iterator();
            final int positiveMaxDepth = maxDepth == null || maxDepth < 0 ? Integer.MAX_VALUE : maxDepth;
            int depth = 0;
            while (walkIterator.hasNext() && depth < positiveMaxDepth) {
                RevCommit rev = walkIterator.next();
                Optional<String> matchingTag = objectIdListMap.getOrDefault(rev, emptyList()).stream()
                        .filter(tag -> tagPattern.matcher(tag).matches())
                        .findFirst();

                if (matchingTag.isPresent()) {
                    return new GitDescription(revObjectId.getName(), matchingTag.get(), depth, true);
                }
                depth++;
            }

            if (isShallowRepository(repository)) {
                throw new IllegalStateException("couldn't find matching tag in shallow git repository");
            }

            return new GitDescription(revObjectId.getName(), "root", depth, false);
        }
    }

    public static boolean isShallowRepository(Repository repository) {
        return new File(repository.getDirectory(), "shallow").isFile();
    }

    public static List<Ref> tags(Repository repository) throws IOException {
        Repository commonRepository = worktreesFix_getCommonRepository(repository);
        return commonRepository.getRefDatabase().getRefsByPrefix(R_TAGS);
    }

    public static Map<ObjectId, List<String>> reverseTagRefMap(Repository repository) throws IOException {
        Repository commonRepository = worktreesFix_getCommonRepository(repository);
        TagComparator tagComparator = new TagComparator(commonRepository);
        return tags(commonRepository).stream()
                .collect(groupingBy(r -> {
                    try {
                        Ref peel = commonRepository.getRefDatabase().peel(r);
                        return peel.getPeeledObjectId() != null
                                ? peel.getPeeledObjectId()
                                : peel.getObjectId();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        e -> e.getValue().stream()
                                .sorted(tagComparator)
                                .map(v -> shortenRefName(v.getName())).collect(toList())
                ));
    }

    public static ZonedDateTime revTimestamp(Repository repository, ObjectId rev) throws IOException {
        Repository commonRepository = worktreesFix_getCommonRepository(repository);
        Instant commitTime = Instant.ofEpochSecond(commonRepository.parseCommit(rev).getCommitTime());
        return ZonedDateTime.ofInstant(commitTime, UTC);
    }

    /**
     * @see Repository#getWorkTree()
     */
    public static File worktreesFix_getWorkTree(Repository repository) throws IOException {
        try {
            return repository.getWorkTree();
        } catch (NoWorkTreeException e) {
            File gitDirFile = new File(repository.getDirectory(), "gitdir");
            if (gitDirFile.exists()) {
                String gitDirPath = Files.readAllLines(gitDirFile.toPath()).get(0);
                return new File(gitDirPath).getParentFile();
            }
            throw e;
        }
    }

    /**
     * @return common repository
     */
    public static Repository worktreesFix_getCommonRepository(Repository repository) throws IOException {
        try {
            repository.getWorkTree();
            return repository;
        } catch (NoWorkTreeException e) {
            File commonDirFile = new File(repository.getDirectory(), "commondir");
            if (!commonDirFile.exists()) {
                throw e;
            }

            String commonDirPath = Files.readAllLines(commonDirFile.toPath()).get(0);
            File commonGitDir = new File(repository.getDirectory(), commonDirPath);
            return new FileRepositoryBuilder().setGitDir(commonGitDir).build();
        }
    }

    /**
     * @see Repository#resolve(String)
     * @see Constants#HEAD
     */
    public static ObjectId worktreesFix_resolveHead(Repository repository) throws IOException {
        try {
            repository.getWorkTree();
            return repository.resolve(HEAD);
        } catch (NoWorkTreeException e) {
            File headFile = new File(repository.getDirectory(), "HEAD");
            if (!headFile.exists()) {
                throw e;
            }

            String head = Files.readAllLines(headFile.toPath()).get(0);
            if (head.startsWith("ref:")) {
                String refPath = head.replaceFirst("^ref: *", "");

                File commonDirFile = new File(repository.getDirectory(), "commondir");
                String commonDirPath = Files.readAllLines(commonDirFile.toPath()).get(0);
                File commonGitDir = new File(repository.getDirectory(), commonDirPath);

                File refFile = new File(commonGitDir, refPath);
                head = Files.readAllLines(refFile.toPath()).get(0);
            }
            return repository.resolve(head);
        }
    }
}
