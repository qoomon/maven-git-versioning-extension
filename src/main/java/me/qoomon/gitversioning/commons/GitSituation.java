package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static me.qoomon.gitversioning.commons.GitUtil.*;

public class GitSituation {

    private static final Pattern PATTERN_PLACEHOLDERS_EXPRESSION = Pattern.compile("\\{\\{((?!describe\\.?)[^}]+)\\}\\}");
    private final Logger logger = LoggerFactory.getLogger(GitSituation.class);

    private final Repository repository;
    private final File rootDirectory;

    private final ObjectId head;
    private final Supplier<ZonedDateTime> timestamp = Lazy.by(this::timestamp);
    private Supplier<String> branch = Lazy.by(this::branch);

    private Supplier<List<String>> tags = Lazy.by(this::tags);

    private final Supplier<Boolean> clean = Lazy.by(this::clean);

    private Supplier<Pattern> describeTagPattern = Lazy.by(() -> Pattern.compile(".*"));

    private Set<String> decribeTagPatternGroups = Collections.emptySet();

    private boolean firstParent = true;

    private Supplier<GitDescription> description = Lazy.by(this::describe);

    public GitSituation(Repository repository) throws IOException {
        this.repository = repository;
        this.rootDirectory = worktreesFix_getWorkTree(repository);
        this.head = worktreesFix_resolveHead(repository);
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public String getRev() {
        return head != null ? head.getName() : NO_COMMIT;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp.get();
    }

    public String getBranch() {
        return branch.get();
    }

    protected void setBranch(String branch) {
        if (branch != null) {
            if (branch.startsWith("refs/tags/")) {
                throw new IllegalArgumentException("invalid branch ref" + branch);
            }
            branch = branch
                    // support default branches (heads)
                    .replaceFirst("^refs/heads/", "")
                    // support other refs e.g. GitHub pull requests refs/pull/1000/head
                    .replaceFirst("^refs/", "");
        }
        final String finalBranch = branch;
        this.branch = () -> finalBranch;
    }

    public boolean isDetached() {
        return branch.get() == null;
    }

    public List<String> getTags() {
        return tags.get();
    }

    protected void addTag(String tag) {
        requireNonNull(tag);

        if (tag.startsWith("refs/") && !tag.startsWith("refs/tags/")) {
            throw new IllegalArgumentException("invalid tag ref" + tag);
        }

        final String finalTag = tag.replaceFirst("^refs/tags/", "");

        final Supplier<List<String>> currentTags = this.tags;
        this.tags = Lazy.by(() -> {
            List<String> tags = new ArrayList<>(currentTags.get());
            tags.add(finalTag);
            return tags;
        });
    }

    protected void setTags(List<String> tags) {
        requireNonNull(tags);
        tags.forEach(tag -> {
            requireNonNull(tag);
            if (tag.startsWith("refs/") && !tag.startsWith("refs/tags/")) {
                throw new IllegalArgumentException("invalid tag ref" + tag);
            }
        });

        tags = tags.stream()
                .map(tag -> tag.replaceFirst("^refs/tags/", ""))
                .collect(toList());

        final List<String> finalTags = tags;
        this.tags = () -> finalTags;
    }

    public boolean isClean() {
        return clean.get();
    }

    public void setDescribeTagPattern(String describeTagPattern, Supplier<Map<String, Supplier<String>>> placeholderMapSupplier) {
        final String rawPattern = requireNonNull(describeTagPattern);
        this.describeTagPattern = preProcessDescribeTagPattern(rawPattern, placeholderMapSupplier);
        this.description = Lazy.by(this::describe);
    }

    private Supplier<Pattern> preProcessDescribeTagPattern(String describeTagPattern, Supplier<Map<String, Supplier<String>>> placeholderMapSupplier) {
        final Matcher placeHolderMatcher = PATTERN_PLACEHOLDERS_EXPRESSION.matcher(describeTagPattern);
        final String placeHolderStrippedPattern = placeHolderMatcher.replaceAll("");
        decribeTagPatternGroups = StringUtil.patternGroups(Pattern.compile(placeHolderStrippedPattern));
        return Lazy.by(() -> {
            placeHolderMatcher.reset();
            final StringBuffer sb = new StringBuffer();
            while (placeHolderMatcher.find()) {
                final String capture = placeHolderMatcher.group(1);
                if (placeholderMapSupplier.get().containsKey(capture)) {
                    final String value = placeholderMapSupplier.get().get(capture).get();
                    placeHolderMatcher.appendReplacement(sb, value);
                }
            }
            placeHolderMatcher.appendTail(sb);
            final String pattern = sb.toString();
            if (!describeTagPattern.equals(pattern)) {
                logger.info("Computed pattern '{}' from describeTagPattern '{}'", pattern, describeTagPattern);
            }
            return Pattern.compile(pattern);
        });
    }

    public Set<String> getDescribeTagPatternGroups() {
        return decribeTagPatternGroups;
    }

    public Pattern getDescribeTagPattern() {
        return describeTagPattern.get();
    }

    public boolean isFirstParent() {
        return firstParent;
    }

    public void setFirstParent(boolean firstParent) {
        this.firstParent = firstParent;
    }

    public GitDescription getDescription() {
        return description.get();
    }

    // ----- initialization methods ------------------------------------------------------------------------------------

    private ZonedDateTime timestamp() throws IOException {
        return head != null
                ? GitUtil.revTimestamp(repository, head)
                : ZonedDateTime.ofInstant(EPOCH, UTC);
    }

    private String branch() throws IOException {
        return GitUtil.branch(repository);
    }

    private List<String> tags() throws IOException {
        return head != null ? GitUtil.tagsPointAt(head, repository) : emptyList();
    }

    private boolean clean() throws GitAPIException {
        return GitUtil.status(repository).isClean();
    }

    private GitDescription describe() throws IOException {
        return GitUtil.describe(head, describeTagPattern.get(), repository, firstParent);
    }
}