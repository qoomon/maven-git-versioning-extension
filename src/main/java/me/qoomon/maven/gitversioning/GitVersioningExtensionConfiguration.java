package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;


@JacksonXmlRootElement(localName = "gitVersioning")
public class GitVersioningExtensionConfiguration {

    public CommitVersionDescription commit;

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> branch = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<VersionDescription> tag = new ArrayList<>();

    public static class VersionDescription {

        public String pattern;
        public String versionFormat;
    }

    public static class CommitVersionDescription {

        public String versionFormat;
    }
}
