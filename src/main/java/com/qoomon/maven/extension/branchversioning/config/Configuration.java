package com.qoomon.maven.extension.branchversioning.config;

import javax.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 26/11/2016.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Configuration {

    @XmlElementWrapper(name = "versionFormats")
    @XmlElement(name = "format")
    public List<VersionFormat> versionFormats = new LinkedList<>();

}
