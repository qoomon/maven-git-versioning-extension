package me.qoomon.gitversioning;

import java.util.Map;
import java.util.Objects;

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
        this.metaData = Objects.requireNonNull(metaData);
        this.version = Objects.requireNonNull(version);
        this.properties = Objects.requireNonNull(properties);
        this.commit = Objects.requireNonNull(commit);
        this.commitRefType = Objects.requireNonNull(commitRefType);
        this.commitRefName = Objects.requireNonNull(commitRefName);
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
