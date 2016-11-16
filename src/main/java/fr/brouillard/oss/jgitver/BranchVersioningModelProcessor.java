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

import com.google.inject.Key;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
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

        File location = new File(source.getLocation());
        if (!location.isFile()) {
            // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
            // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
            // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
            return model; // therefore the model shouldn't be modified.
        }

        File relativePath = location.getParentFile().getCanonicalFile();
        final File rootProjectDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();

        // we should only register the plugin once, on the main project
        if (relativePath.getCanonicalPath().equals(rootProjectDirectory.getCanonicalPath())) {
            addBranchVersioningPlugin(model);
        }

        if (StringUtils.containsIgnoreCase(
                relativePath.getCanonicalPath(),
                rootProjectDirectory.getCanonicalPath()
        )) {
            logger.info("handling version of project Model source " + location);

            String branchVersion = BranchVersioningVersionProvider.getVersion();

            if (model.getVersion() != null) {
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
        } else {
            // skip unrelated models
            logger.debug("skipping unrelated model " + location);
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
            plugin.setVersion("project.version");
        }

        logger.debug("add plugin: " + plugin);
        model.getBuild().getPlugins().add(plugin);
    }
}
