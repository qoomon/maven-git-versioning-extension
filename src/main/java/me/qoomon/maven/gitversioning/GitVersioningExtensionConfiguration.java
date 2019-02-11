package me.qoomon.maven.gitversioning;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by qoomon on 26/11/2016.
 */
@Root(name="gitVersioning")
public class GitVersioningExtensionConfiguration {

    @Element(required = false)
    public CommitVersionDescription commit;

    @ElementList(inline= true, type = VersionDescription.class, entry = "branch")
    public List<VersionDescription> branches = new ArrayList<>();

    @ElementList(inline= true, type = VersionDescription.class, entry = "tag" )
    public List<VersionDescription> tags = new ArrayList<>();

    public static class VersionDescription {

        @Element
        public String pattern;
        @Element
        public String versionFormat;
    }

    public static class CommitVersionDescription {

        @Element
        public String versionFormat;
    }


}
