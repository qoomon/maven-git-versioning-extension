package me.qoomon.gitversioning;

public class VersionDescription {

    private String pattern;

    private String versionFormat;

    public VersionDescription() {
        this(null, null);
    }

    public VersionDescription(String pattern, String versionFormat) {
        setPattern(pattern);
        setVersionFormat(versionFormat);
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
}

