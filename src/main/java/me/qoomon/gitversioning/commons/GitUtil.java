package me.qoomon.gitversioning.commons;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public final class GitUtil {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private static final Pattern GIT_DESCRIBE_PATTER = Pattern.compile("^(?<tag>.*)-(?<distance>\\d*)-g(?<commit>[^-]*?)$");
    private static final Pattern GIT_LOG_TAG_DECORATION_PATTER = Pattern.compile("tag: (?<tag>[^,]*)");

    public static String NO_COMMIT = "0000000000000000000000000000000000000000";


    public static boolean isClean(File directory) throws IOException {
        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "status", "--porcelain")
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        return processResult.stdout.isBlank();
    }

    public static String branch(File directory) throws IOException {
        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "branch", "--show-current")
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        return processResult.stdout.isBlank()
                ? null
                : processResult.stdout.stripTrailing();
    }

    public static List<String> tagsPointAt(File directory, String rev) throws IOException {
        if (isEmpty(directory)) {
            return emptyList();
        }

        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "tag", "--points-at", rev)
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }
        return processResult.stdout.isBlank()
                ? emptyList()
                : Arrays.asList(processResult.stdout.stripTrailing().split("\\n"));

    }

    public static GitDescription describe(File directory, String rev, Pattern tagPattern) throws IOException {
        if (isEmpty(directory)) {
            return new GitDescription(NO_COMMIT, "root", 0);
        }

        List<String> tags = tags(directory, rev);
        Optional<String> tag = tags.stream().filter(it -> tagPattern.matcher(it).matches()).findFirst();
        if (tag.isEmpty()) {
            throw new RuntimeException("No matching tag found for tag pattern" + tagPattern);
        }

        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "describe", "--first-parent", "--tags", "--long", "--match", tag.get())
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        Matcher gitDescribeMatcher = GIT_DESCRIBE_PATTER.matcher(processResult.stdout);
        if (!gitDescribeMatcher.matches()) {
            throw new RuntimeException("Unexpected git describe result " + processResult.stdout);
        }
        String describeCommit = gitDescribeMatcher.group("commit");
        String describeTag = gitDescribeMatcher.group("tag");
        int describeDistance = Integer.parseInt(gitDescribeMatcher.group("distance"));
        return new GitDescription(describeCommit, describeTag, describeDistance);

    }

    public static boolean isEmpty(File directory) throws IOException {
        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "reflog", "-n0")
                .start());

        if (processResult.exitCode != 0) {
            if (processResult.stderr.stripTrailing().endsWith(" does not have any commits yet")) {
                return true;
            }
            throw new RuntimeException(processResult.stderr);
        }

        return false;
    }

    public static List<String> tags(File directory, String rev) throws IOException {
        if (isEmpty(directory)) {
            return emptyList();
        }

        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "log", "--first-parent", "--simplify-by-decoration", "--pretty='format:%D'", rev)
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }
        // tag: build-1.7.20-dev-1349, tag: build-1.7.20-dev-1349, origin/rr/skuzmich/dont-generate-wat-in-box-tests-by-default

        return processResult.stdout.isBlank()
                ? emptyList()
                : Arrays.stream(processResult.stdout.stripTrailing().split("\\n"))
                .map(it -> {
                    LinkedList<String> tags = new LinkedList<>();
                    Matcher matcher = GIT_LOG_TAG_DECORATION_PATTER.matcher(it);
                    while (matcher.find()) {
                        tags.add(matcher.group("tag"));
                    }
                    return tags;
                }).flatMap(List::stream)
                .collect(toList());
    }

    public static ZonedDateTime revTimestamp(File directory, String rev) throws IOException {
        if (isEmpty(directory)) {
            return ZonedDateTime.ofInstant(EPOCH, UTC);
        }

        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "show", "--format=%ct", "-s", rev)
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        int commitEpochSeconds = Integer.parseInt(processResult.stdout.trim());
        Instant commitTime = Instant.ofEpochSecond(commitEpochSeconds);
        return ZonedDateTime.ofInstant(commitTime, UTC);
    }

    public static String getHash(File directory, String rev) throws IOException {
        if (isEmpty(directory)) {
            return NO_COMMIT;
        }

        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "rev-parse", rev)
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        return processResult.stdout.trim();
    }

    public static File getRootDirectory(File directory) throws IOException {
        ProcessResult processResult = ProcessResult.of(new ProcessBuilder()
                .directory(directory)
                .command("git", "rev-parse", "--show-toplevel")
                .start());

        if (processResult.exitCode != 0) {
            throw new RuntimeException(processResult.stderr);
        }

        return new File(processResult.stdout.trim());
    }


    private static class ProcessResult {
        final String stdout;
        final String stderr;
        final int exitCode;

        private ProcessResult(Process process) throws IOException {
            stdout = new String(process.getInputStream().readAllBytes());
            stderr = new String(process.getErrorStream().readAllBytes());
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public static ProcessResult of(Process process) throws IOException {
            return new ProcessResult(process);
        }
    }
}
