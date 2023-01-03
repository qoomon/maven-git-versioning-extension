package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
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

    public static GitDescription describe(ObjectId revObjectId, Pattern tagPattern, Repository repository, boolean firstParent) throws IOException {
        if (revObjectId == null) {
            return new GitDescription(NO_COMMIT, "root", 0);
        }

        Map<ObjectId, List<String>> objectIdListMap = reverseTagRefMap(repository);

        // Walk back commit ancestors looking for tagged one
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setRetainBody(false);
            walk.setFirstParent(firstParent);
            walk.markStart(walk.parseCommit(revObjectId));
            Iterator<RevCommit> walkIterator = walk.iterator();
            int depth = 0;
            while (walkIterator.hasNext()) {
                RevCommit rev = walkIterator.next();
                Optional<String> matchingTag = objectIdListMap.getOrDefault(rev, emptyList()).stream()
                        .filter(tag -> tagPattern.matcher(tag).matches())
                        .findFirst();

                if (matchingTag.isPresent()) {
                    return new GitDescription(revObjectId.getName(), matchingTag.get(), depth);
                }
                depth++;
            }

            if (isShallowRepository(repository)) {
                throw new IllegalStateException("couldn't find matching tag in shallow git repository");
            }

            return new GitDescription(revObjectId.getName(), "root", depth);
        }
    }

    public static boolean isShallowRepository(Repository repository) {
        return new File(repository.getDirectory(), "shallow").isFile();
    }

    public static List<Ref> tags(Repository repository) throws IOException {
        return repository.getRefDatabase().getRefsByPrefix(R_TAGS).stream()
//                .sorted(new TagComparator(repository)) // TODO may can be removed
                .collect(toList());
    }

    public static Map<ObjectId, List<String>> reverseTagRefMap(Repository repository) throws IOException {
        TagComparator tagComparator = new TagComparator(repository);
        return tags(repository).stream()
                .collect(groupingBy(r -> {
                    try {
                        Ref peel = repository.getRefDatabase().peel(r);
                        return peel.getPeeledObjectId() != null
                                ? peel.getPeeledObjectId()
                                : peel.getObjectId();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue().stream()
                                .sorted(tagComparator)
                                .map(v -> shortenRefName(v.getName())).collect(toList())
                ));
    }

    public static ZonedDateTime revTimestamp(Repository repository, ObjectId rev) throws IOException {
        Instant commitTime = Instant.ofEpochSecond(repository.parseCommit(rev).getCommitTime());
        return ZonedDateTime.ofInstant(commitTime, UTC);
    }
}
