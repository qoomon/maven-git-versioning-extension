package me.qoomon.gitversioning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class GitVersioningTest {

    @Test
    void determineVersion_forBranch() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${branch}-branch")),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadBranch() + "-branch")
        );
    }

    @Test
    void determineVersionWithProperties_forBranch() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("feature/my-feature");

        Map<String, String> currentProperties = new HashMap<>();
        currentProperties.put("my.first.properties", "1.0.x-SNAPSHOT");
        currentProperties.put("my.second.properties", "2.2.x-SNAPSHOT");

        VersionDescription branchVersionDescription = new VersionDescription("feature/(?<feature>.+)",
                "${version.release}-${feature}-SNAPSHOT");
        PropertyValueDescription propertyValueDescription = new PropertyValueDescription("(?<propertyVersion>.+?)(-SNAPSHOT)",
                "${propertyVersion}-${feature}-SNAPSHOT");
        PropertyDescription firstPropertyDescription = new PropertyDescription("my.first.properties", propertyValueDescription);
        PropertyDescription secondPropertyDescription = new PropertyDescription("my.second.properties", propertyValueDescription);
        branchVersionDescription.setPropertyDescriptions(Arrays.asList(firstPropertyDescription, secondPropertyDescription));

        String currentVersion = "1.0.x";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(branchVersionDescription),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);
        Map<String, String> gitProperties = gitVersionDetails.getPropertiesTransformer().apply(currentProperties, currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo("1.0.x-my-feature-SNAPSHOT"),
                () -> assertThat(gitProperties).isNotNull(),
                () -> assertThat(gitProperties.size()).isEqualTo(2),
                () -> assertThat(gitProperties.get("my.first.properties")).isEqualTo("1.0.x-my-feature-SNAPSHOT"),
                () -> assertThat(gitProperties.get("my.second.properties")).isEqualTo("2.2.x-my-feature-SNAPSHOT")
        );
    }

    @Test
    void determineVersion_forBranchWithTag() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        repoSituation.setHeadTags(singletonList("v1"));

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${branch}-branch")),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadBranch() + "-branch")
        );
    }

    @Test
    void determineVersion_forBranchWithTag_withTagRulePreferred() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        repoSituation.setHeadTags(singletonList("v1"));

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${branch}-branch")),
                singletonList(new VersionDescription(null, "${tag}-tag")),
                true);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("tag"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadTags().get(0)),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadTags().get(0) + "-tag")
        );
    }

    @Test
    void determineVersion_forBranchWithTag_withTagRuleNotPreferred() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        repoSituation.setHeadTags(singletonList("v1"));

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${branch}-branch")),
                singletonList(new VersionDescription(null, "${tag}-tag")),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadBranch() + "-branch")
        );
    }

    @Test
    void determineVersion_detachedHead() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(null, "${commit}-commit"),
                emptyList(),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("commit"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadCommit() + "-commit")
        );
    }

    @Test
    void determineVersion_detachedHeadWithTag() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadTags(singletonList("v1"));

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                emptyList(),
                singletonList(new VersionDescription("v.*", "${tag}-tag")),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("tag"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadTags().get(0)),
                () -> assertThat(gitVersion).isEqualTo(repoSituation.getHeadTags().get(0) + "-tag")
        );
    }

    @Test
    void determineVersion_forBranchWithTimestamp() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        Instant instant = ZonedDateTime.of(2019, 4, 23, 10, 12, 45, 0, ZoneOffset.UTC).toInstant();
        repoSituation.setHeadCommitTimestamp(instant.getEpochSecond());

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${commit.timestamp}-branch")),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo(instant.getEpochSecond() + "-branch")
        );
    }

    @Test
    void determineVersion_forBranchWithDateTime() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        Instant instant = ZonedDateTime.of(2019, 4, 23, 10, 12, 45, 0, ZoneOffset.UTC).toInstant();
        repoSituation.setHeadCommitTimestamp(instant.getEpochSecond());

        String currentVersion = "undefined";

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                singletonList(new VersionDescription(null, "${commit.timestamp.datetime}-branch")),
                emptyList(),
                false);

        String gitVersion = gitVersionDetails.getVersionTransformer().apply(currentVersion);

        // then
        assertAll(
                () -> assertThat(gitVersionDetails.isClean()).isTrue(),
                () -> assertThat(gitVersionDetails.getCommit()).isEqualTo(repoSituation.getHeadCommit()),
                () -> assertThat(gitVersionDetails.getCommitRefType()).isEqualTo("branch"),
                () -> assertThat(gitVersionDetails.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch()),
                () -> assertThat(gitVersion).isEqualTo(DateTimeFormatter.ofPattern(GitVersioning.VERSION_DATE_TIME_FORMAT)
                        .withZone(ZoneOffset.UTC).format(instant) + "-branch")
        );
    }
}
