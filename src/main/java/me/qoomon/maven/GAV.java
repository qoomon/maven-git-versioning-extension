package me.qoomon.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;

/**
 * Maven artifact identifier consisting of groupId / artifactId / getVersion.
 */
public class GAV {
    private String groupId;
    private String artifactId;
    private String version;

    /**
     * Builds an immutable GAV object.
     *
     * @param groupId    the groupId of the maven object
     * @param artifactId the artifactId of the maven object
     * @param version    the version of the maven object
     */
    public GAV(String groupId, String artifactId, String version) {

        if (groupId == null) throw new IllegalArgumentException("groupId must not be null");
        if (artifactId == null) throw new IllegalArgumentException("artifactId must not be null");
        if (version == null) throw new IllegalArgumentException("version must not be null");

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

    public static GAV of(Model model) {

        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        String version = model.getVersion();

        if (model.getParent() != null) {
            if (groupId == null) {
                groupId = model.getParent().getGroupId();
            }
            if (version == null) {
                version = model.getParent().getVersion();
            }
        }

        return new GAV(groupId, artifactId, version);
    }

    public static GAV of(Parent parent) {
        return new GAV(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion()
        );
    }

    public static GAV of(MavenProject project) {

        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();

        if (project.getParent() != null) {
            if (groupId == null) {
                groupId = project.getParent().getGroupId();
            }
            if (version == null) {
                version = project.getParent().getVersion();
            }
        }
        return new GAV(groupId, artifactId, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        GAV gav = (GAV) o;

        if (!groupId.equals(gav.groupId))
            return false;
        if (!artifactId.equals(gav.artifactId))
            return false;
        return version.equals(gav.version);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }


}
