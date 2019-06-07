package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;


@JacksonXmlRootElement(localName = "gitVersioning")
public class Configuration {

    public Boolean updatePom;

    public CommitVersionDescription commit;

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> branch = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> tag = new ArrayList<>();

    public static class VersionDescription {

        public String pattern;
        public String versionFormat;
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<PropertyDescription> property = new ArrayList<>();
        public Boolean updatePom;
    }

    public static class CommitVersionDescription {

        public String versionFormat;
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<PropertyDescription> property = new ArrayList<>();
        public Boolean updatePom;
    }

    public static class PropertyDescription {

        public String pattern;
        public PropertyValueDescription value;
    }

    public static class PropertyValueDescription {

        public String pattern;
        public String format;
    }
}
