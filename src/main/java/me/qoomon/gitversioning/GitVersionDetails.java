package me.qoomon.gitversioning;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class GitVersionDetails {

    private boolean clean;
    private final String commit;
    private final String commitRefType;
    private final String commitRefName;
    private final VersionTransformer versionTransformer;
    private final PropertiesTransformer propertiesTransformer;

    public GitVersionDetails(final boolean clean,
                             final String commit,
                             final String commitRefType, final String commitRefName,
                             final VersionTransformer versionTransformer,
                             final PropertiesTransformer propertiesTransformer) {
        this.clean = clean;
        this.versionTransformer = Objects.requireNonNull(versionTransformer);
        this.propertiesTransformer = Objects.requireNonNull(propertiesTransformer);
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

    public VersionTransformer getVersionTransformer() {
        return versionTransformer;
    }

    public PropertiesTransformer getPropertiesTransformer() {
        return propertiesTransformer;
    }

    @FunctionalInterface
    public interface VersionTransformer {

        String apply(String currentVersion);
    }

    @FunctionalInterface
    public interface PropertiesTransformer {

        Map<String, String> apply(Map<String, String> properties, String currentVersion);
    }
}
