package me.qoomon.gitversioning;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

public class VersionDescription {

    private String pattern;

    private String versionFormat;

    private List<PropertyDescription> propertyDescriptions;

    public VersionDescription() {
        this(null, null);
    }

    public VersionDescription(String pattern, String versionFormat) {
        this(pattern, versionFormat, ImmutableList.of());
    }

    public VersionDescription(String pattern, String versionFormat, List<PropertyDescription> properties) {
        setPattern(pattern);
        setVersionFormat(versionFormat);
        setPropertyDescriptions(properties);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(final String pattern) {
        this.pattern = pattern != null ? pattern : ".*";
    }

    public String getVersionFormat() {
        return versionFormat;
    }

    public void setVersionFormat(final String versionFormat) {
        this.versionFormat = versionFormat != null ? versionFormat : "${commit}";
    }

    public List<PropertyDescription> getPropertyDescriptions() {
        return propertyDescriptions;
    }

    public void setPropertyDescriptions(List<PropertyDescription> propertyDescriptions) {
        this.propertyDescriptions = Objects.requireNonNull(propertyDescriptions);
    }
}

