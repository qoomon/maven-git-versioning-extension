package com.qoomon.maven.extension.branchversioning.config;

import com.qoomon.maven.BuildProperties;
import com.qoomon.maven.extension.branchversioning.ExtensionUtil;
import com.qoomon.maven.extension.branchversioning.SessionScopeUtil;
import com.qoomon.maven.extension.branchversioning.config.model.BranchVersionDescription;
import com.qoomon.maven.extension.branchversioning.config.model.Configuration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 30/11/2016.
 */
@Component(role = BranchVersioningConfigurationProvider.class, instantiationStrategy = "singleton")
public class BranchVersioningConfigurationProvider {

    private Logger logger;

    private SessionScope sessionScope;


    private static final String DEFAULT_BRANCH_VERSION_FORMAT = "${branch}-SNAPSHOT";

    private BranchVersioningConfiguration configuration;

    @Inject
    public BranchVersioningConfigurationProvider(Logger logger, SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

    public BranchVersioningConfiguration get() {

        if (configuration == null) {

            MavenSession session = SessionScopeUtil.get(sessionScope, MavenSession.class).get();

            Map<Pattern, String> branchVersionFormatMap = new LinkedHashMap<>();

            File configFile = ExtensionUtil.getConfigFile(session.getRequest(), BuildProperties.projectArtifactId());
            if (configFile.exists()) {

                Configuration configurationModel = loadConfiguration(configFile);
                branchVersionFormatMap = generateBranchVersionFormatMap(configurationModel);
            }

            // add default
            branchVersionFormatMap.put(Pattern.compile(".*"), DEFAULT_BRANCH_VERSION_FORMAT);


            configuration = new BranchVersioningConfiguration(branchVersionFormatMap);
        }

        return configuration;

    }


    private Configuration loadConfiguration(File configFile) {
        try {
            logger.debug("load config from " + configFile);
            Serializer serializer = new Persister();
            return serializer.read(Configuration.class, configFile);
        } catch (Exception e) {
            throw new RuntimeException(configFile.toString(), e);
        }
    }


    private Map<Pattern, String> generateBranchVersionFormatMap(Configuration configuration) {
        Map<Pattern, String> map = new LinkedHashMap<>();
        for (BranchVersionDescription branchVersionDescription : configuration.branches) {
            map.put(
                    Pattern.compile(branchVersionDescription.pattern),
                    branchVersionDescription.versionFormat
            );
        }
        return map;
    }

}
