package fr.brouillard.oss.jgitver.cfg;

import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import fr.brouillard.oss.jgitver.BranchingPolicy.BranchNameTransformations;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class BranchPolicy {
    @XmlElement(name = "pattern")
    public String pattern;
    @XmlElementWrapper(name = "transformations")
    @XmlElement(name = "transformation")
    public List<String> transformations = Arrays.asList(
            BranchNameTransformations.REPLACE_UNEXPECTED_CHARS_UNDERSCORE.name(),
            BranchNameTransformations.LOWERCASE_EN.name());
}
