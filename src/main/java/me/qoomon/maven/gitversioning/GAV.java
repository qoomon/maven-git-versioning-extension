package me.qoomon.maven.gitversioning;

import org.apache.maven.model.*;

import java.util.Objects;

/**
 * Maven artifact identifier consisting of groupId, artifactId and version.
 */
public class GAV {
    private final String groupId;
    private final String artifactId;
    private final String version;

    /**
     * Builds an immutable GAV object.
     *
     * @param groupId    the groupId of the maven object
     * @param artifactId the artifactId of the maven object
     * @param version    the version of the maven object
     */
    GAV(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getProjectId() {
        return getGroupId() + ":" + getArtifactId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GAV gav = (GAV) o;
        return Objects.equals(groupId, gav.groupId) &&
                Objects.equals(artifactId, gav.artifactId) &&
                Objects.equals(version, gav.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }

    public static GAV of(Model model) {

        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        String version = model.getVersion();

        Parent parent = model.getParent();
        if (parent != null) {
            if (groupId == null) {
                groupId = parent.getGroupId();
            }
            if (version == null) {
                version = parent.getVersion();
            }
        }

        return new GAV(groupId, artifactId, version);
    }

    public static GAV of(Parent parent) {

        String groupId = parent.getGroupId();
        String artifactId = parent.getArtifactId();
        String version = parent.getVersion();

        return new GAV(groupId, artifactId, version);
    }

    public static GAV of(Dependency dependency) {

        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String version = dependency.getVersion();

        return new GAV(groupId, artifactId, version);
    }

    public static GAV of(Plugin plugin) {

        String groupId = plugin.getGroupId();
        String artifactId = plugin.getArtifactId();
        String version = plugin.getVersion();

        return new GAV(groupId, artifactId, version);
    }

    public static GAV of(ReportPlugin plugin) {

        String groupId = plugin.getGroupId();
        String artifactId = plugin.getArtifactId();
        String version = plugin.getVersion();

        return new GAV(groupId, artifactId, version);
    }

}
