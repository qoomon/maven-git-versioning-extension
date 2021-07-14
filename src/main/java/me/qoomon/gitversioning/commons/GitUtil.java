package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
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

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Repository.shortenRefName;

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

    public static List<String> tagsPointAt(Repository repository, ObjectId revObjectId) throws IOException {
        return tagsPointAt(repository, revObjectId, reverseTagRefMap(repository));
    }

    public static List<String> tagsPointAt(Repository repository, ObjectId revObjectId,
                                           Map<ObjectId, List<Ref>> reverseTagRefMap) {
        return reverseTagRefMap.getOrDefault(revObjectId, emptyList()).stream()
                .sorted(new RefNameComparator(repository))
                .map(ref -> shortenRefName(ref.getName()))
                .collect(toList());
    }

    public static GitDescription describe(Repository repository, ObjectId revObjectId, Pattern tagPattern) throws IOException {
        return describe(repository, revObjectId, tagPattern, reverseTagRefMap(repository));
    }

    public static GitDescription describe(Repository repository, ObjectId revObjectId, Pattern tagPattern,
                                          Map<ObjectId, List<Ref>> reverseTagRefMap) throws IOException {

        if(revObjectId == null) {
            return new GitDescription(NO_COMMIT, "root", 0);
        }

        // Walk back commit ancestors looking for tagged one
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setRetainBody(false);
            walk.setFirstParent(true);
            walk.markStart(walk.parseCommit(revObjectId));
            Iterator<RevCommit> walkIterator = walk.iterator();
            int depth = -1;
            while (walkIterator.hasNext()) {
                RevCommit rev = walkIterator.next();
                depth++;

                Optional<Ref> matchingTag = reverseTagRefMap.getOrDefault(rev, emptyList()).stream()
                        .sorted(new RefNameComparator(repository))
                        .filter(tag -> tagPattern.matcher(shortenRefName(tag.getName())).matches())
                        .findFirst();

                if (matchingTag.isPresent()) {
                    return new GitDescription(revObjectId.getName(), shortenRefName(matchingTag.get().getName()), depth);
                }
            }

            if (isShallowRepository(repository)) {
                throw new IllegalStateException("couldn't find matching tag in shallow git repository");
            }

            return new GitDescription(revObjectId.getName(), "root", depth);
        }
    }

    public static boolean isShallowRepository(Repository repository){
        return new File(repository.getDirectory(), "shallow").isFile();
    }

    public static List<Ref> tags(Repository repository) throws IOException {
        return tags(repository.getRefDatabase());
    }

    public static List<Ref> tags(RefDatabase refDatabase) throws IOException {
        return refDatabase.getRefsByPrefix(R_TAGS);
    }

    public static Map<ObjectId, List<Ref>> reverseTagRefMap(Repository repository) throws IOException {
        RefDatabase refDatabase = repository.getRefDatabase();
        return tags(refDatabase).stream().collect(groupingBy(ref -> {
            try {
                ObjectId peeledObjectId = refDatabase.peel(ref).getPeeledObjectId();
                return peeledObjectId != null ? peeledObjectId : ref.getObjectId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public static ZonedDateTime revTimestamp(Repository repository, ObjectId rev) throws IOException {
        Instant commitTime = Instant.ofEpochSecond(repository.parseCommit(rev).getCommitTime());
        return ZonedDateTime.ofInstant(commitTime, UTC);
    }
}
