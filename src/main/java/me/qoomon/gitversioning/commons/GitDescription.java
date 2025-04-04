package me.qoomon.gitversioning.commons;

public class GitDescription {
    private final String commit;
    private final String tag;
    private final int distance;
    private final boolean tagFound;

    public GitDescription(String commit, String tag, int distance, boolean tagFound) {
        this.commit = commit;
        this.tag = tag;
        this.distance = distance;
        this.tagFound = tagFound;
    }

    public String getCommit() {
        return commit;
    }

    public String getTag() {
        return tag;
    }

    public boolean isTagFound() {
        return tagFound;
    }

    public int getDistance() {
        return distance;
    }

    public int getDistanceOrZero() {
        return tagFound ? distance : 0;
    }

    @Override
    public String toString() {
        return tag + "-" + distance + "-g" + commit.substring(0,7);
    }
}
