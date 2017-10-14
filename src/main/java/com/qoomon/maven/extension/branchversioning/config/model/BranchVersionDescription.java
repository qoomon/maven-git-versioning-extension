package com.qoomon.maven.extension.branchversioning.config.model;


import org.simpleframework.xml.Element;

/**
 * Created by qoomon on 26/11/2016.
 */
public class BranchVersionDescription {

    @Element
    public String pattern;

    @Element
    public String versionFormat;

}
