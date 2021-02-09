package me.qoomon.maven.gitversioning;

import me.qoomon.gitversioning.commons.GitRefType;

import static com.google.common.base.Preconditions.checkNotNull;

public class GitVersionDetails {
    private final String commit;
    private final GitRefType refType;
    private final String refName;
    private final Configuration.VersionDescription config;

    public GitVersionDetails(String commit, GitRefType refType, String refName, Configuration.VersionDescription config) {

        this.commit = checkNotNull(commit);
        this.refType = checkNotNull(refType);
        this.refName = checkNotNull(refName);
        this.config = checkNotNull(config);
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

    public Configuration.VersionDescription getConfig() {
        return config;
    }
}
