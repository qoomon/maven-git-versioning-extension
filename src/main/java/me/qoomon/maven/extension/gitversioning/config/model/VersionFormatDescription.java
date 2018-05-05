package me.qoomon.maven.extension.gitversioning.config.model;


import org.simpleframework.xml.Element;

/**
 * Created by qoomon on 26/11/2016.
 */
public class VersionFormatDescription {

    @Element
    public String pattern;

    @Element(required = false)
    public String prefix = "";

    @Element
    public String versionFormat;

}
