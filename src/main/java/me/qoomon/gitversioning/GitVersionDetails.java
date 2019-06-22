package me.qoomon.gitversioning;

import java.util.Map;

public class GitVersionDetails {

    private boolean clean;
    private final String commit;
    private final String commitRefType;
    private final String commitRefName;
    private final Map<String,String> metaData;
    private final String version;
    private final Map<String,String> properties;

    public GitVersionDetails(final boolean clean,
                             final String commit,
                             final String commitRefType, final String commitRefName,
                             final Map<String, String> metaData,
                             final String version,
                             final Map<String, String> properties) {
        this.clean = clean;
        this.metaData = metaData;
        this.version = version;
        this.properties = properties;
        this.commit = commit;
        this.commitRefType = commitRefType;
        this.commitRefName = commitRefName;
    }

    public boolean isClean() {
        return clean;
    }

    public String getCommit() {
        return commit;
    }

    public String getCommitRefType() {
        return commitRefType;
    }

    public String getCommitRefName() {
        return commitRefName;
    }

    public Map<String, String> getMetaData() {
        return metaData;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getVersion() {
        return version;
    }
}
