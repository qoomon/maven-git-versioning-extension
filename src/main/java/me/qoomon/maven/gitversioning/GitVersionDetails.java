package me.qoomon.maven.gitversioning;

import me.qoomon.gitversioning.commons.GitRefType;
import me.qoomon.maven.gitversioning.Configuration.RefPatchDescription;

import static java.util.Objects.requireNonNull;

public class GitVersionDetails {
    private final String commit;
    private final GitRefType refType;
    private final String refName;
    private final RefPatchDescription patchDescription;

    public GitVersionDetails(String commit, GitRefType refType, String refName, RefPatchDescription patchDescription) {

        this.commit = requireNonNull(commit);
        this.refType = requireNonNull(refType);
        this.refName = requireNonNull(refName);
        this.patchDescription = requireNonNull(patchDescription);
    }

    public String getCommit() {
        return commit;
    }

    public GitRefType getRefType() {
        return refType;
    }

    public String getRefName() {
        return refName;
    }

    public RefPatchDescription getPatchDescription() {
        return patchDescription;
    }
}
