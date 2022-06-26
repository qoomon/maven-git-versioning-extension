package me.qoomon.gitversioning.commons;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.Callable;

public class TagComparator implements Comparator<Ref> {

    private final RevWalk revWalk;

    public TagComparator(Repository repository) {
        this.revWalk = new RevWalk(repository);
    }

    @Override
    public int compare(Ref ref1, Ref ref2) {
        RevObject rev1 = tryUnchecked(() -> revWalk.parseAny(ref1.getObjectId()));
        RevObject rev2 = tryUnchecked(() -> revWalk.parseAny(ref2.getObjectId()));

        // both tags are annotated tags
        if (rev1 instanceof RevTag && rev2 instanceof RevTag) {
            return compareTaggerDate((RevTag) rev1, (RevTag) rev2);
        }

        // only ref1 is annotated tag
        if (rev1 instanceof RevTag) {
            return -1;
        }

        // only ref2 is annotated tag
        if (rev2 instanceof RevTag) {
            return 1;
        }

        // both tags are lightweight tags
        return compareTagVersion(ref1, ref2);
    }

    private static <R> R tryUnchecked(Callable<R> block) {
        try {
            return block.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int compareTagVersion(Ref ref1, Ref ref2) {
        DefaultArtifactVersion version1 = new DefaultArtifactVersion(ref1.getName());
        DefaultArtifactVersion version2 = new DefaultArtifactVersion(ref2.getName());
        // sort the highest version first
        return -version1.compareTo(version2);
    }

    private static int compareTaggerDate(RevTag rev1, RevTag rev2) {
        Date revTag1Date = rev1.getTaggerIdent().getWhen();
        Date revTag2Date = rev2.getTaggerIdent().getWhen();
        // sort the most recent tags first
        return -revTag1Date.compareTo(revTag2Date);
    }
}
