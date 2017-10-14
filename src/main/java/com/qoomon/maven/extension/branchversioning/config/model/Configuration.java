package com.qoomon.maven.extension.branchversioning.config.model;

import org.simpleframework.xml.ElementList;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by qoomon on 26/11/2016.
 */
public class Configuration {

    @ElementList(type = BranchVersionDescription.class)
    public List<BranchVersionDescription> branches = new LinkedList<>();

}
