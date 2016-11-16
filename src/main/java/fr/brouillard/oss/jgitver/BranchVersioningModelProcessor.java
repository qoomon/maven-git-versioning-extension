// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package fr.brouillard.oss.jgitver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    @Requirement
    private Logger logger = null;

    @Requirement
    private SessionScope sessionScope;

    private static Map<GAV, String> branchVersionMap = Maps.newHashMap();


    public BranchVersioningModelProcessor() {
        super();
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    private Model provisionModel(Model model, Map<String, ?> options) throws IOException {
        // get current session from scope
        MavenSession mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
            // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
            // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
            return model; // therefore the model shouldn't be modified.
        }

        File relativePath = pomFile.getParentFile().getCanonicalFile();
        final File rootProjectDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();

        // we should only register the plugin once, on the main project
        if (relativePath.getCanonicalPath().equals(rootProjectDirectory.getCanonicalPath())) {
            addBranchVersioningPlugin(model);
        }


        if (StringUtils.containsIgnoreCase(relativePath.getCanonicalPath(), rootProjectDirectory.getCanonicalPath())) {

            GAV projectGav = GAV.of(model);
            String branchVersion = getVersion(projectGav, pomFile.getParentFile());

            if (model.getVersion() != null) {
                logger.info("update project version with branch version " + branchVersion + " @ " + projectGav);
                model.setVersion(branchVersion);
            }

            if (model.getParent() != null) {
                GAV parentProjectGav = GAV.of(model.getParent());
                if (hasVersion(parentProjectGav)) {
                    String parentBranchVersion = getVersion(parentProjectGav);
                    logger.info("update project parent version with branch version " + parentBranchVersion + " @ " + projectGav);
                    model.getParent().setVersion(parentBranchVersion);
                }
            }
        } else {
            // skip unrelated models
            logger.debug("skipping unrelated model " + pomFile);
        }

        return model;
    }

    /**
     * @param model
     * @throws IOException
     * @see BranchVersioningPomMojo
     */
    private void addBranchVersioningPlugin(Model model) throws IOException {

        if (model.getBuild() == null) {
            model.setBuild(new Build());
        }

        Plugin plugin = new Plugin();
        try (InputStream inputStream = getClass().getResourceAsStream("/mavenMeta.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            plugin.setGroupId(properties.getProperty("project.groupId"));
            plugin.setArtifactId(properties.getProperty("project.artifactId"));
            plugin.setVersion(properties.getProperty("project.version"));
        }

        logger.info("add plugin: " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion());
        model.getBuild().getPlugins().add(plugin);

        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.setPhase("verify");
        plugin.getExecutions().add(pluginExecution);

        pluginExecution.getGoals().add(BranchVersioningPomMojo.GOAL_ATTACH_TEMP_POMS);

        Dependency dependency = new Dependency();
        dependency.setGroupId(plugin.getGroupId());
        dependency.setArtifactId(plugin.getArtifactId());
        dependency.setVersion(plugin.getVersion());

        plugin.getDependencies().add(dependency);

    }


    public static String getVersion(GAV gav, File gitDirectory) {
        Preconditions.checkArgument(gitDirectory.isDirectory(), "git directory must be a directory, but was " + gitDirectory);

        if (!branchVersionMap.containsKey(gav)) {
            String version = "bondage-fairies";
            branchVersionMap.put(gav, version);
        }

        return getVersion(gav);
    }

    public static String getVersion(GAV gav) {
        if (!hasVersion(gav)) {
            throw new IllegalStateException("Unexpected version request for " + gav);
        }
        return branchVersionMap.get(gav);
    }

    public static boolean hasVersion(GAV gav) {
        return branchVersionMap.containsKey(gav);
    }
}
