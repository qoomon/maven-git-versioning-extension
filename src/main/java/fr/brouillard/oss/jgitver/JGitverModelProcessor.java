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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.building.Source;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class JGitverModelProcessor extends DefaultModelProcessor {

    @Requirement
    private Logger logger = null;

    @Requirement
    private LegacySupport legacySupport = null;


    public JGitverModelProcessor() {
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

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        File location = new File(source.getLocation());
        if (!location.isFile()) {
            // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
            // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
            // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
            return model; // therefore the model shouldn't be modified.
        }

        File relativePath = location.getParentFile().getCanonicalFile();

        final File rootProjectDirectory = legacySupport.getSession().getRequest().getMultiModuleProjectDirectory();
        if (StringUtils.containsIgnoreCase(
                relativePath.getCanonicalPath(),
                rootProjectDirectory.getCanonicalPath()
        )) {
            logger.error("handling version of project Model from " + location);

            logger.error("rootProjectDirectory: " + rootProjectDirectory);
            String branchVersion = "sickOfItAll"; // TODO read from git and model

            if (model.getVersion() != null) {
                // TODO evaluate how to set the version only when it was originally set in the pom file
                model.setVersion(branchVersion);
            }

            if (model.getParent() != null) {
                // if the parent is part of the multi module project, let's update the parent version 
                File relativePathParent = new File(
                        relativePath.getCanonicalPath() + File.separator + model.getParent().getRelativePath())
                        .getParentFile().getCanonicalFile();
                if (StringUtils.containsIgnoreCase(
                        relativePathParent.getCanonicalPath(),
                        rootProjectDirectory.getCanonicalPath())) {
                    model.getParent().setVersion(branchVersion);
                }
            }

            // we should only register the plugin once, on the main project
            if (relativePath.getCanonicalPath().equals(rootProjectDirectory.getCanonicalPath())) {
                if (model.getBuild() == null) {
                    model.setBuild(new Build());
                }

                if (model.getBuild().getPlugins() == null) {
                    model.getBuild().setPlugins(new ArrayList<>());
                }

                Optional<Plugin> pluginOptional = model.getBuild().getPlugins().stream()
                        .filter(x -> JGitverUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                                && JGitverUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                        .findFirst();

                StringBuilder pluginVersion = new StringBuilder();

                try (InputStream inputStream = getClass()
                        .getResourceAsStream("/META-INF/maven/" + JGitverUtils.EXTENSION_GROUP_ID + "/"
                                + JGitverUtils.EXTENSION_ARTIFACT_ID + "/pom" + ".properties")) {
                    Properties properties = new Properties();
                    properties.load(inputStream);
                    pluginVersion.append(properties.getProperty("version"));
                } catch (IOException ignored) {
                    // TODO we should not ignore in case we have to reuse it
                    logger.warn(ignored.getMessage(), ignored);
                }

                Plugin plugin = pluginOptional.orElseGet(() -> {
                    Plugin plugin2 = new Plugin();
                    plugin2.setGroupId(JGitverUtils.EXTENSION_GROUP_ID);
                    plugin2.setArtifactId(JGitverUtils.EXTENSION_ARTIFACT_ID);
                    plugin2.setVersion(pluginVersion.toString());

                    model.getBuild().getPlugins().add(plugin2);
                    return plugin2;
                });

                if (plugin.getExecutions() == null) {
                    plugin.setExecutions(new ArrayList<>());
                }

                Optional<PluginExecution> pluginExecutionOptional = plugin.getExecutions().stream()
                        .filter(x -> "verify".equalsIgnoreCase(x.getPhase())).findFirst();

                PluginExecution pluginExecution = pluginExecutionOptional.orElseGet(() -> {
                    PluginExecution pluginExecution2 = new PluginExecution();
                    pluginExecution2.setPhase("verify");

                    plugin.getExecutions().add(pluginExecution2);
                    return pluginExecution2;
                });

                if (pluginExecution.getGoals() == null) {
                    pluginExecution.setGoals(new ArrayList<>());
                }

                if (!pluginExecution.getGoals().contains(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS)) {
                    pluginExecution.getGoals().add(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);
                }

                if (plugin.getDependencies() == null) {
                    plugin.setDependencies(new ArrayList<>());
                }

                Optional<Dependency> dependencyOptional = plugin.getDependencies().stream()
                        .filter(x -> JGitverUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                                && JGitverUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                        .findFirst();

                dependencyOptional.orElseGet(() -> {
                    Dependency dependency = new Dependency();
                    dependency.setGroupId(JGitverUtils.EXTENSION_GROUP_ID);
                    dependency.setArtifactId(JGitverUtils.EXTENSION_ARTIFACT_ID);
                    dependency.setVersion(pluginVersion.toString());

                    plugin.getDependencies().add(dependency);
                    return dependency;
                });
            }

        } else {
            logger.debug("skipping Model from " + location);
        }

        return model;
    }
}
