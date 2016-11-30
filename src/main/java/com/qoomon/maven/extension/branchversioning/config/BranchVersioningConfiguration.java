package com.qoomon.maven.extension.branchversioning.config;

import com.google.common.collect.Maps;
import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.extension.branchversioning.SessionUtil;
import com.qoomon.maven.extension.branchversioning.config.model.BranchVersionDescription;
import com.qoomon.maven.extension.branchversioning.config.model.Configuration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 30/11/2016.
 */
@Component(role = BranchVersioningConfiguration.class, instantiationStrategy = "singleton")
public class BranchVersioningConfiguration {

    private static final String DISABLE_BRANCH_VERSIONING_PROPERTY_KEY = "disableBranchVersioning";

    @Requirement
    private Logger logger;

    @Requirement
    private SessionScope sessionScope;

    private Configuration configuration = null;

    private LinkedHashMap<Pattern, String> branchVersionFormatMap = getDefaultBranchVersionFormatMap();


    public BranchVersioningConfiguration() {
    }

    private Configuration getConfiguration() {
        if (configuration == null) {
            MavenSession session = SessionUtil.getMavenSession(sessionScope);
            File rootProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
            File configFile = new File(rootProjectDirectory, ".mvn/" + BuildProperties.projectArtifactId() + ".xml");
            if (configFile.exists()) {
                configuration = loadConfiguration(configFile);
            } else {
                configuration = new Configuration();
            }
        }
        return configuration;
    }

    private Configuration loadConfiguration(File configFile) {
        logger.debug("load config from " + configFile);
        try (FileInputStream configFileInputStream = new FileInputStream(configFile)) {
            Unmarshaller unmarshaller = JAXBContext.newInstance(Configuration.class).createUnmarshaller();
            return (Configuration) unmarshaller.unmarshal(configFileInputStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public LinkedHashMap<Pattern, String> getBranchVersionFormatMap() {
        for (BranchVersionDescription branchVersionDescription : getConfiguration().branches) {
            logger.debug("branchVersionFormat: " + branchVersionDescription.branchPattern + " -> " + branchVersionDescription.versionFormat);
            branchVersionFormatMap.put(
                    Pattern.compile(branchVersionDescription.branchPattern),
                    branchVersionDescription.versionFormat
            );
        }
        return branchVersionFormatMap;
    }

    public boolean isDisabled() {
        MavenSession mavenSession = SessionUtil.getMavenSession(sessionScope);
        String disablePropertyValue = mavenSession.getUserProperties().getProperty(DISABLE_BRANCH_VERSIONING_PROPERTY_KEY, "false");
        return Boolean.valueOf(disablePropertyValue);
    }

    private static LinkedHashMap<Pattern, String> getDefaultBranchVersionFormatMap() {
        LinkedHashMap<Pattern, String> result = Maps.newLinkedHashMap();
        result.put(Pattern.compile("^master$"), "${pomReleaseVersion}");
        return result;
    }

}
