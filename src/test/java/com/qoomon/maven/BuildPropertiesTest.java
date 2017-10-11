package com.qoomon.maven;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by qoomon on 30/11/2016.
 */
public class BuildPropertiesTest {

    @Test
    public void value() throws Exception {
        // GIVEN

        // WHEN
        String projectGroupId = BuildProperties.value("project.groupId");

        // THEN
        assertEquals("com.qoomon", projectGroupId);

    }

}