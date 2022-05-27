package me.qoomon.gitversioning.commons;


import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class GitSituation {

    private final File directory;
    private final File rootDirectory;

    private final String rev;
    private final Supplier<ZonedDateTime> timestamp = Lazy.by(this::timestamp);
    private Supplier<String> branch = Lazy.by(this::branch);

    private Supplier<List<String>> tags = Lazy.by(this::tags);

    private final Supplier<Boolean> clean = Lazy.by(this::clean);

    private Pattern describeTagPattern = Pattern.compile(".*");
    private Supplier<GitDescription> description = Lazy.by(this::describe);

    public GitSituation(File directory) throws IOException {
        this.directory = directory;
        this.rootDirectory = GitUtil.getRootDirectory(directory) ;
        this.rev = GitUtil.getHash(directory, "HEAD");
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public String getRev() {
        return rev;
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
        return GitUtil.revTimestamp(directory, "HEAD");
    }

    private String branch() throws IOException {
        return GitUtil.branch(directory);
    }

    private List<String> tags() throws IOException {
        return GitUtil.tagsPointAt(directory, "HEAD");
    }

    private boolean clean() throws IOException{
        return GitUtil.isClean(directory);
    }

    private GitDescription describe() throws IOException {
        return GitUtil.describe(directory, "HEAD", describeTagPattern);
    }

}