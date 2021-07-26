package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.google.common.collect.Lists;
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
    public Boolean updatePom;

    public PatchDescription rev;

    public RefPatchDescriptionList refs = new RefPatchDescriptionList();

    @JsonInclude(NON_NULL)
    public static class PatchDescription {

        public Pattern describeTagPattern;
        public Boolean updatePom;

        public String version;

        @JsonInclude(NON_EMPTY)
        @JacksonXmlElementWrapper(useWrapping = false)
        public Map<String, String> properties = new HashMap<>();
    }

    @JsonInclude(NON_NULL)
    public static class RefPatchDescription extends PatchDescription {

        @JacksonXmlProperty(isAttribute = true)
        public GitRefType type = COMMIT;
        public Pattern pattern;

        public static RefPatchDescription of(PatchDescription description) {
            RefPatchDescription refDescription = new RefPatchDescription();
            refDescription.describeTagPattern = description.describeTagPattern;
            refDescription.updatePom = description.updatePom;
            refDescription.version = description.version;
            refDescription.properties = new HashMap<>(description.properties);
            return refDescription;
        }
    }

    public static class RefPatchDescriptionList {
        @JsonInclude(NON_EMPTY)
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "ref")
        public List<RefPatchDescription> list = new ArrayList<>();

        @JacksonXmlProperty(isAttribute = true)
        public Boolean considerTagsOnlyIfHeadIsDetached = false;
    }
}
