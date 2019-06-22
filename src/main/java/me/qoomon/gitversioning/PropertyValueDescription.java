package me.qoomon.gitversioning;

public class PropertyValueDescription {

    private String pattern;

    private String format;

    public PropertyValueDescription() {
    }

    public PropertyValueDescription(String pattern, String format) {
        setPattern(pattern);
        setFormat(format);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(final String pattern) {
        this.pattern = pattern != null ? pattern : ".*";
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format != null ? format : "";
    }
}
