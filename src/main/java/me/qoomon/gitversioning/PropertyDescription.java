package me.qoomon.gitversioning;

import java.util.Objects;

public class PropertyDescription {

    private String pattern;

    private PropertyValueDescription valueDescription;

    public PropertyDescription(String pattern, PropertyValueDescription valueDescription) {
        setPattern(pattern);
        setValueDescription(valueDescription);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(final String pattern) {
        this.pattern = pattern != null ? pattern : ".*";
    }

    public PropertyValueDescription getValueDescription() {
        return valueDescription;
    }

    public void setValueDescription(PropertyValueDescription valueDescription) {
        this.valueDescription = Objects.requireNonNull(valueDescription);
    }
}
