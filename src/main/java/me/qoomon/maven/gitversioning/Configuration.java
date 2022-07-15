package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import me.qoomon.gitversioning.commons.GitRefType;

import java.io.IOException;
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

    private static final String MATCH_ALL = ".*";

    public Boolean disable = false;

    @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
    public String projectVersionPattern = null;

    public Pattern projectVersionPattern() {
        if(projectVersionPattern == null) {
            return null;
        }
        return Pattern.compile(projectVersionPattern);
    }

    @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
    public String describeTagPattern = null;

    public Pattern describeTagPattern() {
        if(describeTagPattern == null) {
            return null;
        }
        return Pattern.compile(describeTagPattern);
    }

    public Boolean updatePom = false;

    public RefPatchDescriptionList refs = new RefPatchDescriptionList();

    public PatchDescription rev;

    @JsonInclude(NON_EMPTY)
    @JacksonXmlElementWrapper
    @JsonProperty(required = true)
    public List<RelatedProject> relatedProjects = new ArrayList<>();

    @JsonInclude(NON_NULL)
    public static class PatchDescription {

        @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public String describeTagPattern;

        public Pattern describeTagPattern() {
            if(describeTagPattern == null) {
                return null;
            }
            return Pattern.compile(describeTagPattern);
        }

        @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public String version;

        @JsonInclude(NON_EMPTY)
        @JacksonXmlElementWrapper(useWrapping = false)
        // TODO  @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public Map<String, String> properties = new HashMap<>();

        public Boolean updatePom;
    }

    @JsonInclude(NON_NULL)
    public static class RefPatchDescription extends PatchDescription {

        @JacksonXmlProperty(isAttribute = true)
        public GitRefType type = COMMIT;

        @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public String pattern;

        public Pattern pattern() {
            if(pattern == null) {
                return null;
            }
            return Pattern.compile(pattern);
        }

        public RefPatchDescription() {
        }

        public RefPatchDescription(GitRefType type, Pattern pattern, PatchDescription description) {
            this.type = type;
            this.pattern = pattern != null ? pattern.pattern() : null;
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

        @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public String groupId;

        @JsonDeserialize(using = IgnoreWhitespaceDeserializer.class)
        public String artifactId;

        @JsonCreator
        public RelatedProject(
                @JsonProperty(required = true, value = "groupId") String groupId,
                @JsonProperty(required = true, value = "artifactId") String artifactId
        ) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }
    }


    public static class IgnoreWhitespaceDeserializer extends JsonDeserializer<Object> {
        @Override
        public Object deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            return jp.getText().replaceAll("\\s+", "");
        }
    }
}
