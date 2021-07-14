package me.qoomon.gitversioning.commons;

public class GitDescription {
    private final String commit;
    private final String tag;
    private final int distance;

    public GitDescription(String commit, String tag, int distance) {
        this.commit = commit;
        this.tag = tag;
        this.distance = distance;
    }

    public String getCommit() {
        return commit;
    }

    public String getTag() {
        return tag;
    }

    public int getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return tag + "-" + distance + "-g" + commit.substring(0,7);
    }
}
