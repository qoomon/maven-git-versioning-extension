package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import me.qoomon.gitversioning.commons.GitRefType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static me.qoomon.gitversioning.commons.GitRefType.COMMIT;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "configuration")
public class Configuration {

    private static final Pattern MATCH_ALL = Pattern.compile(".*");

    public Boolean disable = false;

    public Pattern describeTagPattern = MATCH_ALL;

    public Boolean updatePom = false;

    public RefPatchDescriptionList refs = new RefPatchDescriptionList();

    public PatchDescription rev;

    @JsonInclude(NON_EMPTY)
    @JacksonXmlElementWrapper
    @JsonProperty(required = true)
    public List<RelatedProject> relatedProjects = new ArrayList<>();

    @JsonInclude(NON_NULL)
    public static class PatchDescription {

        public Pattern describeTagPattern;

        public String version;

        @JsonInclude(NON_EMPTY)
        @JacksonXmlElementWrapper(useWrapping = false)
        public Map<String, String> properties = new HashMap<>();

        public Boolean updatePom;
    }

    @JsonInclude(NON_NULL)
    public static class RefPatchDescription extends PatchDescription {

        @JacksonXmlProperty(isAttribute = true)
        public GitRefType type = COMMIT;

        public Pattern pattern;

        public RefPatchDescription(){}

        public RefPatchDescription(GitRefType type, Pattern pattern, PatchDescription description) {
            this.type = type;
            this.pattern = pattern;
            this.describeTagPattern = description.describeTagPattern;
            this.updatePom = description.updatePom;
            this.version = description.version;
            this.properties = new HashMap<>(description.properties);
        }
    }

    public static class RefPatchDescriptionList {

        @JacksonXmlProperty(isAttribute = true)
        public Boolean considerTagsOnBranches = false;

        @JsonInclude(NON_EMPTY)
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "ref")
        public List<RefPatchDescription> list = new ArrayList<>();
    }

    public static class RelatedProject {
        public String groupId;
        public String artifactId;

        @JsonCreator
        public RelatedProject(
                @JsonProperty(required = true,  value="groupId") String groupId,
                @JsonProperty(required = true, value="artifactId") String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }
    }
}
