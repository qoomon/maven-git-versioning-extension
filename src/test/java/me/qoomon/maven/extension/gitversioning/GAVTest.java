package me.qoomon.maven.extension.gitversioning;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GAVTest {

    @Test
    void of_model() {
        // Given
        Model model = new Model();
        model.setGroupId("group");
        model.setArtifactId("artifact");
        model.setVersion("version");

        // When
        GAV gav = GAV.of(model);

        // Then
        assertThat(gav).isNotNull();
        assertThat(gav.getGroupId()).isEqualTo("group");
        assertThat(gav.getArtifactId()).isEqualTo("artifact");
        assertThat(gav.getVersion()).isEqualTo("version");
    }

    @Test
    void of_model_withParent_noInheritance() {
        // Given
        Model model = new Model();
        model.setGroupId("group");
        model.setArtifactId("artifact");
        model.setVersion("version");

        Parent parent = new Parent();
        parent.setGroupId("parentGroup");
        parent.setArtifactId("parentArtifact");
        parent.setVersion("parentVersion");
        model.setParent(parent);

        // When
        GAV gav = GAV.of(model);

        // Then
        assertThat(gav).isNotNull();
        assertThat(gav.getGroupId()).isEqualTo("group");
        assertThat(gav.getArtifactId()).isEqualTo("artifact");
        assertThat(gav.getVersion()).isEqualTo("version");
    }

    @Test
    void of_model_withParent_withInheritance() {
        // Given
        Model model = new Model();
        model.setArtifactId("artifact");

        Parent parent = new Parent();
        parent.setGroupId("parentGroup");
        parent.setArtifactId("parentArtifact");
        parent.setVersion("parentVersion");
        model.setParent(parent);

        // When
        GAV gav = GAV.of(model);

        // Then
        assertThat(gav).isNotNull();
        assertThat(gav.getGroupId()).isEqualTo("parentGroup");
        assertThat(gav.getArtifactId()).isEqualTo("artifact");
        assertThat(gav.getVersion()).isEqualTo("parentVersion");
    }

    @Test
    void of_parent() {
        // Given
        Parent parent = new Parent();
        parent.setGroupId("parentGroup");
        parent.setArtifactId("parentArtifact");
        parent.setVersion("parentVersion");

        // When
        GAV gav = GAV.of(parent);

        // Then
        assertThat(gav).isNotNull();
        assertThat(gav.getGroupId()).isEqualTo("parentGroup");
        assertThat(gav.getArtifactId()).isEqualTo("parentArtifact");
        assertThat(gav.getVersion()).isEqualTo("parentVersion");
    }

    @Test
    void toStringTest() {
        // Given
        GAV gav = new GAV("group", "artifact", "version");

        // When
        String gavString = gav.toString();

        // Then
        assertThat(gavString).isEqualTo("group:artifact:version");

    }
}