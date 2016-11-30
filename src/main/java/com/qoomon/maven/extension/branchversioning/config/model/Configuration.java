package com.qoomon.maven.extension.branchversioning.config.model;

import javax.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by qoomon on 26/11/2016.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Configuration {

    @XmlElementWrapper(name = "branches")
    @XmlElement(name = "branch")
    public List<BranchVersionDescription> branches = new LinkedList<>();

}
