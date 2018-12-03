package me.qoomon.maven.extension.gitversioning;


public class GAVGit extends GAV {

    private final String commit;
    private final String commitRefType;
    private final String commitRefName;

    GAVGit(String groupId, String artifactId, String version, String commit, String commitRefType, String commitRefName) {
        super(groupId, artifactId, version);
        this.commit = commit;
        this.commitRefType = commitRefType;
        this.commitRefName = commitRefName;
    }

    String getCommit() {
        return commit;
    }

    String getCommitRefType() {
        return commitRefType;
    }

    String getCommitRefName() {
        return commitRefName;
    }
}