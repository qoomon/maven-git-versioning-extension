package me.qoomon.maven.extension.gitversioning.config;

import me.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;

import java.util.List;
import java.util.Objects;

/**
 * Created by qoomon on 30/11/2016.
 */
public class VersioningConfiguration {

    private final List<VersionFormatDescription> branchVersionDescriptions;
    private final List<VersionFormatDescription> tagVersionDescriptions;
    private final VersionFormatDescription commitVersionDescription;

    public VersioningConfiguration(List<VersionFormatDescription> branchVersionDescriptions,
                                   List<VersionFormatDescription> tagVersionDescriptions,
                                   VersionFormatDescription commitVersionDescription) {
        this.branchVersionDescriptions = Objects.requireNonNull(branchVersionDescriptions);
        this.tagVersionDescriptions = Objects.requireNonNull(tagVersionDescriptions);
        this.commitVersionDescription = Objects.requireNonNull(commitVersionDescription);
    }

    public List<VersionFormatDescription> getBranchVersionDescriptions() {
        return branchVersionDescriptions;
    }

    public List<VersionFormatDescription> getTagVersionDescriptions() {
        return tagVersionDescriptions;
    }

    public VersionFormatDescription getCommitVersionDescription() {
        return commitVersionDescription;
    }
}
