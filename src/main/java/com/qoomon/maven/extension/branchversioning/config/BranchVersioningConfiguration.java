package com.qoomon.maven.extension.branchversioning.config;

import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 30/11/2016.
 */
public class BranchVersioningConfiguration {

    private final LinkedHashMap<Pattern, String> branchVersionFormatMap;

    public BranchVersioningConfiguration(LinkedHashMap<Pattern, String> branchVersionFormatMap) {
        this.branchVersionFormatMap = branchVersionFormatMap;
    }

    public LinkedHashMap<Pattern, String> getBranchVersionFormatMap() {
        return branchVersionFormatMap;
    }

}
