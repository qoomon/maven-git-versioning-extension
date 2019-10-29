package me.qoomon.gitversioning;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import static me.qoomon.gitversioning.StringUtil.substituteText;
import static me.qoomon.gitversioning.StringUtil.valueGroupMap;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import me.qoomon.gitversioning.GitVersionDetails.PropertiesTransformer;
import me.qoomon.gitversioning.GitVersionDetails.VersionTransformer;

public final class GitVersioning {

    public static final String VERSION_DATE_TIME_FORMAT = "yyyyMMdd.HHmmss";
    public static final String NO_COMMIT_DATE = "00000000.000000";

    private GitVersioning() {
    }

    @Nonnull
    public static GitVersionDetails determineVersion(
            final GitRepoSituation repoSituation,
            final VersionDescription commitVersionDescription,
            final List<VersionDescription> branchVersionDescriptions,
            final List<VersionDescription> tagVersionDescriptions,
            final boolean preferTags) {

        requireNonNull(repoSituation);
        requireNonNull(commitVersionDescription);
        requireNonNull(branchVersionDescriptions);
        requireNonNull(tagVersionDescriptions);

        final VersioningInfo versioningInfo = getVersioningInfo(repoSituation, commitVersionDescription, branchVersionDescriptions, tagVersionDescriptions, preferTags);

        final Map<String, String> refData = valueGroupMap(versioningInfo.description.getPattern(), versioningInfo.gitRefName);

        final Map<String, String> gitDataMap = new HashMap<>();
        gitDataMap.put("commit", repoSituation.getHeadCommit());
        gitDataMap.put("commit.short", repoSituation.getHeadCommit().substring(0, 7));
        gitDataMap.put("commit.timestamp", Long.toString(repoSituation.getHeadCommitTimestamp()));
        gitDataMap.put("commit.timestamp.datetime", formatHeadCommitTimestamp(repoSituation.getHeadCommitTimestamp()));
        gitDataMap.put("ref", versioningInfo.gitRefName);
        gitDataMap.put(versioningInfo.gitRefType, versioningInfo.gitRefName);
        gitDataMap.putAll(refData);

        final VersionTransformer versionTransformer = currentVersion -> {
            final Map<String, String> dataMap = new HashMap<>(gitDataMap);
            dataMap.put("version", currentVersion);
            dataMap.put("version.release", currentVersion.replaceFirst("-SNAPSHOT$", ""));
            return substituteText(versioningInfo.description.getVersionFormat(), dataMap)
                    .replace("/", "-");
        };

        final PropertiesTransformer propertiesTransformer = (currentProperties, currentVersion) -> {
            final Map<String, String> dataMap = new HashMap<>(gitDataMap);
            dataMap.put("version", currentVersion);
            dataMap.put("version.release", currentVersion.replaceFirst("-SNAPSHOT$", ""));
            return transformProperties(
                    currentProperties, versioningInfo.description.getPropertyDescriptions(), dataMap);
        };

        return new GitVersionDetails(
                repoSituation.isClean(),
                repoSituation.getHeadCommit(),
                versioningInfo.gitRefType,
                versioningInfo.gitRefName,
                versionTransformer,
                propertiesTransformer
        );
    }

    private static VersioningInfo getVersioningInfo(GitRepoSituation repoSituation, VersionDescription commitVersionDescription, List<VersionDescription> branchVersionDescriptions, List<VersionDescription> tagVersionDescriptions, boolean preferTags) {

        // tags take precedence over branches
        VersioningInfo versioningInfo = null;
        if (preferTags) {
            versioningInfo = getTagVersioningInfo(repoSituation, tagVersionDescriptions);
        }
        if (versioningInfo == null) {
            versioningInfo = getBranchVersioningInfo(repoSituation, branchVersionDescriptions);
        }
        if (versioningInfo == null && !preferTags) {
            versioningInfo = getTagVersioningInfo(repoSituation, tagVersionDescriptions);
        }

        // default versioning: commit
        if (versioningInfo == null) {
            versioningInfo = new VersioningInfo("commit", repoSituation.getHeadCommit(), commitVersionDescription);
        }
        return versioningInfo;
    }

    private static VersioningInfo getTagVersioningInfo(GitRepoSituation repoSituation, List<VersionDescription> tagVersionDescriptions) {
        if (!repoSituation.getHeadTags().isEmpty()) {
            for (final VersionDescription tagVersionDescription : tagVersionDescriptions) {
                Optional<String> versionTag = repoSituation.getHeadTags().stream()
                        .filter(tag -> tag.matches(tagVersionDescription.getPattern()))
                        .max(comparing(DefaultArtifactVersion::new));
                if (versionTag.isPresent()) {
                    return new VersioningInfo("tag", versionTag.get(), tagVersionDescription);
                }
            }
        }
        return null;
    }

    private static VersioningInfo getBranchVersioningInfo(GitRepoSituation repoSituation, List<VersionDescription> branchVersionDescriptions) {
        if (repoSituation.getHeadBranch() != null) {
            for (final VersionDescription branchVersionDescription : branchVersionDescriptions) {
                Optional<String> versionBranch = Optional.of(repoSituation.getHeadBranch())
                        .filter(branch -> branch.matches(branchVersionDescription.getPattern()));
                if (versionBranch.isPresent()) {
                    return new VersioningInfo("branch", versionBranch.get(), branchVersionDescription);
                }
            }
        }
        return null;
    }

    private static Map<String, String> transformProperties(Map<String, String> currentProperties,
                                                           List<PropertyDescription> propertyDescriptions,
                                                           Map<String, String> dataMap) {

        Map<String, String> resultProperties = new HashMap<>(currentProperties);

        for (Map.Entry<String, String> property : currentProperties.entrySet()) {
            Optional<PropertyDescription> propertyDescription = propertyDescriptions.stream()
                    .filter(it -> property.getKey().matches(it.getPattern()))
                    .findFirst();
            if (propertyDescription.isPresent()) {
                String valuePattern = propertyDescription.get().getValueDescription().getPattern();
                if (property.getValue().matches(valuePattern)) {
                    HashMap<String, String> propertyDataMap = new HashMap<>(dataMap);
                    propertyDataMap.put("property.name", property.getKey());
                    propertyDataMap.put("property.value", property.getValue());
                    Map<String, String> propertyFields = valueGroupMap(valuePattern, property.getValue());
                    propertyDataMap.putAll(propertyFields);

                    String valueFormat = propertyDescription.get().getValueDescription().getFormat();
                    String resultValue = substituteText(valueFormat, propertyDataMap);

                    resultProperties.replace(property.getKey(), resultValue);
                }
            }
        }

        return resultProperties;
    }

    private static String formatHeadCommitTimestamp(long headCommitDate) {
        if (headCommitDate == 0) {
            return NO_COMMIT_DATE;
        }
        return DateTimeFormatter
                .ofPattern(VERSION_DATE_TIME_FORMAT)
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(headCommitDate));
    }

    private static class VersioningInfo {
        private final String gitRefType;
        private final String gitRefName;
        private final VersionDescription description;

        private VersioningInfo(String gitRefType, String gitRefName, VersionDescription versionDescription) {
            this.gitRefType = gitRefType;
            this.gitRefName = gitRefName;
            this.description = versionDescription;
        }
    }
}
