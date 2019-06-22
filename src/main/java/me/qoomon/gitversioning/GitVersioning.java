package me.qoomon.gitversioning;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static me.qoomon.gitversioning.StringUtil.substituteText;
import static me.qoomon.gitversioning.StringUtil.valueGroupMap;

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
            final String currentVersion,
            final Map<String,String> currentProperties) {

        requireNonNull(repoSituation);
        requireNonNull(commitVersionDescription);
        requireNonNull(branchVersionDescriptions);
        requireNonNull(tagVersionDescriptions);

        // default versioning
        String gitRefType = "commit";
        String gitRefName = repoSituation.getHeadCommit();
        VersionDescription versionDescription = commitVersionDescription;

        if (repoSituation.getHeadBranch() != null) {
            // branch versioning
            for (final VersionDescription branchVersionDescription : branchVersionDescriptions) {
                Optional<String> versionBranch = Optional.of(repoSituation.getHeadBranch())
                        .filter(branch -> branch.matches(branchVersionDescription.getPattern()));
                if (versionBranch.isPresent()) {
                    gitRefType = "branch";
                    gitRefName = versionBranch.get();
                    versionDescription = branchVersionDescription;
                    break;
                }
            }
        } else if (!repoSituation.getHeadTags().isEmpty()) {
            // tag versioning
            for (final VersionDescription tagVersionDescription : tagVersionDescriptions) {
                Optional<String> versionTag = repoSituation.getHeadTags().stream()
                        .filter(tag -> tag.matches(tagVersionDescription.getPattern()))
                        .max(comparing(DefaultArtifactVersion::new));
                if (versionTag.isPresent()) {
                    gitRefType = "tag";
                    gitRefName = versionTag.get();
                    versionDescription = tagVersionDescription;
                    break;
                }
            }
        }
        Map<String, String> refFields = valueGroupMap(versionDescription.getPattern(), gitRefName);

        Map<String, String> versionDataMap = new HashMap<>();
        versionDataMap.put("version", currentVersion);
        versionDataMap.put("version.release", currentVersion.replaceFirst("-SNAPSHOT$", ""));
        versionDataMap.put("commit", repoSituation.getHeadCommit());
        versionDataMap.put("commit.short", repoSituation.getHeadCommit().substring(0, 7));
        versionDataMap.put("commit.timestamp", Long.toString(repoSituation.getHeadCommitTimestamp()));
        versionDataMap.put("commit.timestamp.datetime", formatHeadCommitTimestamp(repoSituation.getHeadCommitTimestamp()));
        versionDataMap.put("ref", gitRefName);
        versionDataMap.put(gitRefType, gitRefName);
        versionDataMap.putAll(refFields);

        String gitVersion = substituteText(versionDescription.getVersionFormat(), versionDataMap)
                .replace("/", "-");

        Map<String, String> gitProperties = determineProperties(currentProperties, versionDescription.getPropertyDescriptions(), versionDataMap);

        return new GitVersionDetails(
                repoSituation.isClean(),
                repoSituation.getHeadCommit(),
                gitRefType,
                gitRefName,
                refFields,
                gitVersion,
                gitProperties);
    }

    private static Map<String, String> determineProperties(Map<String, String> currentProperties,
                                                           List<PropertyDescription> propertyDescriptions,
                                                           Map<String, String> versionDataMap) {

        Map<String, String> gitProperties =  new HashMap<>(currentProperties);

        for (Map.Entry<String, String> property : currentProperties.entrySet()) {
            Optional<PropertyDescription> propertyDescription = propertyDescriptions.stream().filter(it -> property.getKey().matches(it.getPattern())).findFirst();
            if(propertyDescription.isPresent()){
                if(property.getValue().matches(propertyDescription.get().getValueDescription().getPattern())){
                    Map<String, String> propertyFields = valueGroupMap(propertyDescription.get().getValueDescription().getPattern(), property.getValue());
                    HashMap<String, String> propertyDataMap = new HashMap<>(versionDataMap);
                    propertyDataMap.putAll(propertyFields);

                    String gitPropertyValue = substituteText(propertyDescription.get().getValueDescription().getFormat(), propertyDataMap);

                    gitProperties.put(property.getKey(), gitPropertyValue);
                }
            }
        }

        return gitProperties;
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

}
