package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.qoomon.gitversioning.commons.GitUtil.NO_COMMIT;
import static org.eclipse.jgit.lib.Constants.HEAD;


public class GitSituation {

    private final Repository repository;
    private final File rootDirectory;

    private final ObjectId head;
    private final String hash;
    private final Supplier<ZonedDateTime> timestamp = Lazy.by(this::timestamp);
    private Supplier<String> branch = Lazy.by(this::branch);

    private final Supplier<Map<ObjectId, List<Ref>>> reverseTagRefMap = Lazy.by(this::reverseTagRefMap);
    private Supplier<List<String>> tags = Lazy.by(this::tags);

    private final Supplier<Boolean> clean = Lazy.by(this::clean);

    private Pattern describeTagPattern = Pattern.compile(".*");
    private Supplier<GitDescription> description = Lazy.by(this::describe);

    public GitSituation(Repository repository) throws IOException {
        this.repository = repository;
        this.rootDirectory = repository.getWorkTree();
        this.head = repository.resolve(HEAD);
        this.hash = head != null ? head.getName() : NO_COMMIT;
    }


    public File getRootDirectory() {
        return rootDirectory;
    }

    public String getHash() {
        return hash;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp.get();
    }

    public String getBranch() {
        return branch.get();
    }

    public void setBranch(String branch) {
        this.branch = () -> branch;
    }

    public boolean isDetached() {
        return branch.get() == null;
    }

    public List<String> getTags() {
        return tags.get();
    }

    public void setTags(List<String> tags) {
        this.tags = () -> requireNonNull(tags);
    }

    public boolean isClean() {
        return clean.get();
    }

    public void setDescribeTagPattern(Pattern describeTagPattern) {
        this.describeTagPattern = requireNonNull(describeTagPattern);
        this.description = Lazy.by(this::describe);
    }

    public Pattern getDescribeTagPattern() {
        return describeTagPattern;
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

    private List<String> tags() {
        return head != null ? GitUtil.tagsPointAt(repository, head, reverseTagRefMap.get()) : emptyList();
    }

    private boolean clean() {
        return GitUtil.status(repository).isClean();
    }

    private GitDescription describe() throws IOException {
        return GitUtil.describe(repository, head, describeTagPattern, reverseTagRefMap.get());
    }

    private Map<ObjectId, List<Ref>> reverseTagRefMap() throws IOException {
        return GitUtil.reverseTagRefMap(repository);
    }
}