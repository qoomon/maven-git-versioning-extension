package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;

import static org.eclipse.jgit.lib.Constants.OBJ_TAG;

public class TagComparator implements Comparator<Ref> {

    private final RevWalk revWalk;

    public TagComparator(Repository repository) {
        this.revWalk = new RevWalk(repository);
    }

    @Override
    public int compare(Ref ref1, Ref ref2) {
        try {
            RevTag revTag1 = revWalk.parseTag(ref1.getObjectId());
            RevTag revTag2 = revWalk.parseTag(ref2.getObjectId());

            // both tags are annotated tags
            if (revTag1.getType() == OBJ_TAG && revTag2.getType() == OBJ_TAG) {
                Date revTag1Date = revTag1.getTaggerIdent().getWhen();
                Date revTag2Date = revTag2.getTaggerIdent().getWhen();
                // most recent tags first
                return -revTag1Date.compareTo(revTag2Date);
            }

            // only ref1 is annotated tag
            if (revTag1.getType() == OBJ_TAG) {
                return -1;
            }

            // only ref2 is annotated tag
            if (revTag2.getType() == OBJ_TAG) {
                return 1;
            }

            // both tags are lightweight tags
            return revTag1.name().compareTo(revTag1.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
