package me.qoomon.gitversioning.commons;


import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class GitSituation {

    public static String NO_COMMIT = "0000000000000000000000000000000000000000";

    private final File rootDirectory;
    private final String headCommit;
    private final long headCommitTimestamp;
    private final String headBranch;
    private final List<String> headTags;
    private final boolean clean;

    public GitSituation(File rootDirectory, String headCommit, long headCommitTimestamp, String headBranch, List<String> headTags, boolean clean) {
        this.rootDirectory = requireNonNull(rootDirectory);
        this.headCommit = requireNonNull(headCommit);
        this.headCommitTimestamp = headCommitTimestamp;
        this.headBranch = headBranch;
        this.headTags = requireNonNull(headTags);
        this.clean = clean;
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public long getHeadCommitTimestamp() {
        return headCommitTimestamp;
    }

    public ZonedDateTime getHeadCommitDateTime() {
        Instant headCommitTimestamp = Instant.ofEpochSecond(getHeadCommitTimestamp());
        return ZonedDateTime.ofInstant(headCommitTimestamp, UTC);
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public List<String> getHeadTags() {
        return headTags;
    }

    public boolean isClean() {
        return clean;
    }

    public boolean isDetached() {
        return headBranch == null;
    }

    public static class Builder {
        private File rootDirectory;
        private String headCommit = NO_COMMIT;
        private long headCommitTimestamp = 0;
        private String headBranch = null;
        private List<String> headTags = emptyList();
        private boolean clean = true;


        public Builder setRootDirectory(File rootDirectory) {
            this.rootDirectory = rootDirectory;
            return this;
        }

        public Builder setHeadCommit(String headCommit) {
            this.headCommit = headCommit;
            return this;
        }

        public Builder setHeadCommitTimestamp(long headCommitTimestamp) {
            this.headCommitTimestamp = headCommitTimestamp;
            return this;
        }

        public Builder setHeadBranch(String headBranch) {
            this.headBranch = headBranch;
            return this;
        }

        public Builder setHeadTags(List<String> headTags) {
            this.headTags = headTags;
            return this;
        }

        public Builder setClean(boolean clean) {
            this.clean = clean;
            return this;
        }

        public GitSituation build() {
            return new GitSituation(rootDirectory, headCommit, headCommitTimestamp, headBranch, headTags, clean);
        }

        public static Builder of(GitSituation gitSituation) {
            return new Builder()
                    .setRootDirectory(gitSituation.rootDirectory)
                    .setClean(gitSituation.clean)
                    .setHeadCommit(gitSituation.headCommit)
                    .setHeadCommitTimestamp(gitSituation.headCommitTimestamp)
                    .setHeadBranch(gitSituation.headBranch)
                    .setHeadTags(gitSituation.headTags);
        }
    }
}