package me.qoomon.gitversioning;

public class PropertyDescription {

    private String pattern;

    private PropertyValueDescription value;

    public PropertyDescription() {
        this(null, null);
    }

    public PropertyDescription(String pattern, PropertyValueDescription value) {
        setPattern(pattern);
        setValue(value);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public PropertyValueDescription getValue() {
        return value;
    }

    public void setValue(PropertyValueDescription value) {
        this.value = value;
    }
}
