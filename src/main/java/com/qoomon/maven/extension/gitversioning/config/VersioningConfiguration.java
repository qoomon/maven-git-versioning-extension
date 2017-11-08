package com.qoomon.maven.extension.gitversioning.config;

import com.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;

import java.util.List;

/**
 * Created by qoomon on 30/11/2016.
 */
public class VersioningConfiguration {

    private final List<VersionFormatDescription> branchVersionDescriptions;
    private final List<VersionFormatDescription> tagVersionDescriptions;

    public VersioningConfiguration(List<VersionFormatDescription> branchVersionDescriptions,
                                   List<VersionFormatDescription> tagVersionDescriptions
    ) {
        this.branchVersionDescriptions = branchVersionDescriptions;
        this.tagVersionDescriptions = tagVersionDescriptions;
    }

    public List<VersionFormatDescription> getBranchVersionDescriptions() {
        return branchVersionDescriptions;
    }

    public List<VersionFormatDescription> getTagVersionDescriptions() {
        return tagVersionDescriptions;
    }
}
