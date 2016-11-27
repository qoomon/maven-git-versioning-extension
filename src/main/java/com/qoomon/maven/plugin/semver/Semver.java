package com.qoomon.maven.plugin.semver;

import java.util.regex.Pattern;

/**
 * Created by qoomon on 17/11/2016.
 */
public class Semver {
    // see http://semver.org/#semantic-versioning-200
    public static final String DESCRIPTION = "MAJOR.MINOR.PATCH-LABEL+METADATA";
    public static final Pattern PATTERN = Pattern.compile("^(0|[1-9][0-9]*)(\\.(0|[1-9][0-9]*))?(\\.(0|[1-9][0-9]*))?(-[a-zA-Z][a-zA-Z0-9]*)?(\\+[a-zA-Z0-9]+)?$");

}
