package com.qoomon.maven.extension.branchversioning.config;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 30/11/2016.
 */
public class BranchVersioningConfiguration {

    private final Map<Pattern, String> branchVersionFormatMap;

    public BranchVersioningConfiguration(Map<Pattern, String> branchVersionFormatMap) {
        this.branchVersionFormatMap = branchVersionFormatMap;
    }

    public Map<Pattern, String> getBranchVersionFormatMap() {
        return branchVersionFormatMap;
    }

}
