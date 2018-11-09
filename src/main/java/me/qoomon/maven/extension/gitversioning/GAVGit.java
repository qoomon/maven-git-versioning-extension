package me.qoomon.maven.extension.gitversioning;


public class GAVGit extends GAV {

    private final String commit;
    private final String commitRefName;
    private final String commitRefType;

    GAVGit(String groupId, String artifactId, String version, String commit, String commitRefName, String commitRefType) {
        super(groupId, artifactId, version);
        this.commit = commit;
        this.commitRefName = commitRefName;
        this.commitRefType = commitRefType;
    }

    String getCommit() {
        return commit;
    }

    String getCommitRefName() {
        return commitRefName;
    }

    String getCommitRefType() {
        return commitRefType;
    }
}