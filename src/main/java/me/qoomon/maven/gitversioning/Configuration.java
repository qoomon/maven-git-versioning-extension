package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "configuration")
public class Configuration {

    public Boolean disable;

    public Boolean preferTags;

    public Boolean updatePom;
    public String describeTagPattern;

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> branch = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> tag = new ArrayList<>();

    public VersionDescription commit;

    public static class VersionDescription {
        public String pattern;
        public String versionFormat;
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<PropertyDescription> property = new ArrayList<>();
        public Boolean updatePom;
        public String describeTagPattern;
    }

    public static class PropertyDescription {

        public String name;
        public String valueFormat;
    }
}
