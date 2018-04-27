package me.qoomon.maven.extension.gitversioning.config.model;

import org.simpleframework.xml.ElementList;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by qoomon on 26/11/2016.
 */
public class Configuration {

    @ElementList(type = VersionFormatDescription.class)
    public List<VersionFormatDescription> branches = new LinkedList<>();

    @ElementList(type = VersionFormatDescription.class)
    public List<VersionFormatDescription> tags = new LinkedList<>();

}
