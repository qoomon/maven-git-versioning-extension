package com.qoomon.maven.extension.branchversioning.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by qoomon on 26/11/2016.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class BranchVersionDescription {

    @XmlElement(name = "pattern")
    public String branchPattern;

    @XmlElement
    public String versionFormat;

    public BranchVersionDescription(String branchPattern, String versionFormat) {
        this.branchPattern = branchPattern;
        this.versionFormat = versionFormat;
    }

    private BranchVersionDescription() {
    }
}
