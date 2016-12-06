package com.qoomon.maven.extension.branchversioning.config;

import org.codehaus.plexus.component.annotations.Component;

import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 30/11/2016.
 */
public class BranchVersioningConfiguration {

    private final boolean disable;

    private final LinkedHashMap<Pattern, String> branchVersionFormatMap;

    public BranchVersioningConfiguration(boolean disable, LinkedHashMap<Pattern, String> branchVersionFormatMap) {
        this.disable = disable;
        this.branchVersionFormatMap = branchVersionFormatMap;
    }

    public LinkedHashMap<Pattern, String> getBranchVersionFormatMap() {
        return branchVersionFormatMap;
    }

    public boolean isDisabled() {
        return disable;
    }
}
