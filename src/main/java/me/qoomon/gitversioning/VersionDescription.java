package me.qoomon.gitversioning;

import java.util.List;

public class VersionDescription {

    private String pattern;

    private String versionFormat;

    private List<PropertyDescription> properties;

    public VersionDescription() {
        this(null, null);
    }

    public VersionDescription(String pattern, String versionFormat) {
        this(pattern, versionFormat, null);
    }

    public VersionDescription(String pattern, String versionFormat, List<PropertyDescription> properties) {
        setPattern(pattern);
        setVersionFormat(versionFormat);
        setProperties(properties);
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

    public List<PropertyDescription> getProperties() {
        return properties;
    }

    public void setProperties(List<PropertyDescription> properties) {
        this.properties = properties;
    }
}

