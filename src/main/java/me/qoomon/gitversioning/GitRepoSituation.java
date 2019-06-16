package me.qoomon.gitversioning;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;

public class GitRepoSituation {

    private boolean clean;
    private String headCommit;
    private String headBranch;
    private long headCommitTimestamp;
    private List<String> headTags;

    public GitRepoSituation(){
        this(true, NO_COMMIT, 0, emptyList(), null);
    }

    public GitRepoSituation(boolean clean, String headCommit, long headCommitTimestamp, List<String> headTags, String headBranch) {
        setClean(clean);
        setHeadCommit(headCommit);
        setHeadCommitTimestamp(headCommitTimestamp);
        setHeadBranch(headBranch);
        setHeadTags(headTags);
    }

    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public void setHeadCommit(String headCommit) {
        this.headCommit = requireNonNull(headCommit);
        if (headCommit.length() != 40){
            throw new IllegalArgumentException("headCommit sha-1 hash must contains of 40 hex characters");
        }
    }

    public long getHeadCommitTimestamp()
    {
        return headCommitTimestamp;
    }

    public void setHeadCommitTimestamp(long headCommitTimestamp)
    {
        this.headCommitTimestamp = headCommitTimestamp;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
    }

    public List<String> getHeadTags() {
        return headTags;
    }

    public void setHeadTags(List<String> headTags) {
        this.headTags = requireNonNull(headTags);
    }
}