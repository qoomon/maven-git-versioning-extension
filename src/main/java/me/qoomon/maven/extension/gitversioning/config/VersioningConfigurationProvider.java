package me.qoomon.maven.extension.gitversioning.config;

import me.qoomon.maven.BuildProperties;
import me.qoomon.maven.extension.gitversioning.ExtensionUtil;
import me.qoomon.maven.extension.gitversioning.SessionScopeUtil;
import me.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;
import me.qoomon.maven.extension.gitversioning.config.model.Configuration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by qoomon on 30/11/2016.
 */
@Component(role = VersioningConfigurationProvider.class, instantiationStrategy = "singleton")
public class VersioningConfigurationProvider {

    private Logger logger;

    private SessionScope sessionScope;

    private VersioningConfiguration configuration;

    @Inject
    public VersioningConfigurationProvider(Logger logger, SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

    public VersioningConfiguration get() {

        if (configuration == null) {

            MavenSession session = SessionScopeUtil.get(sessionScope, MavenSession.class).get();

            List<VersionFormatDescription> branchVersionDescriptions = new LinkedList<>();
            List<VersionFormatDescription> tagVersionDescriptions = new LinkedList<>();
            VersionFormatDescription commitVersionDescription = defaultCommitVersionFormat();

            File configFile = ExtensionUtil.getConfigFile(session.getRequest(), BuildProperties.projectArtifactId());
            if (configFile.exists()) {

                Configuration configurationModel = loadConfiguration(configFile);
                branchVersionDescriptions.addAll(configurationModel.branches);
                tagVersionDescriptions.addAll(configurationModel.tags);
            } else {
                logger.info("No configuration file found. Apply default configuration.");
            }

            branchVersionDescriptions.add(defaultBranchVersionFormat());

            configuration = new VersioningConfiguration(branchVersionDescriptions, tagVersionDescriptions, commitVersionDescription);
        }

        return configuration;

    }

    private static VersionFormatDescription defaultBranchVersionFormat() {
        VersionFormatDescription result = new VersionFormatDescription();
        result.pattern = ".*";
        result.versionFormat = "${branch}-SNAPSHOT";
        return result;
    }

    private static VersionFormatDescription defaultCommitVersionFormat() {
        VersionFormatDescription result = new VersionFormatDescription();
        result.pattern = ".*";
        result.versionFormat = "${commit}";
        return result;
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

}
