package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Comparator;

public class RefNameComparator implements Comparator<Ref> {

    private final Repository repository;

    public RefNameComparator(Repository repository) {
        this.repository = repository;
    }

    @Override
    public int compare(Ref ref1, Ref ref2) {
        ObjectId ref1PeeledObjectId = ref1.getPeeledObjectId();
        ObjectId ref2PeeledObjectId = ref2.getPeeledObjectId();

        // both tags are annotated tags
        if (ref1PeeledObjectId != null && ref2PeeledObjectId != null) {
            try {
                int ref1CommitTime = repository.parseCommit(ref1.getObjectId()).getCommitTime();
                int ref2CommitTime = repository.parseCommit(ref2.getObjectId()).getCommitTime();
                return Integer.compare(ref1CommitTime, ref2CommitTime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // only ref1 is annotated tag
        if (ref1PeeledObjectId != null) {
            return -1;
        }

        // only ref2 is annotated tag
        if (ref2PeeledObjectId != null) {
            return 1;
        }

        // both tags are light weight tags
        return ref1.getName().compareTo(ref2.getName());
    }
}
