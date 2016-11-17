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
package com.qoomon.maven.extension.branchversioning;

import com.google.common.collect.Maps;
import com.google.inject.Key;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
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

/**
 * Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class BranchVersioningModelProcessor extends DefaultModelProcessor {

    @Requirement
    private Logger logger = null;

    @Requirement
    private SessionScope sessionScope;

    private Map<GAV, String> branchVersionMap = Maps.newHashMap();

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

        Source source = (Source) options.get(ModelProcessor.SOURCE);
        if (source == null) {
            return model;
        }

        // get current session from scope
        MavenSession mavenSession = sessionScope.scope(Key.get(MavenSession.class), null).get();
        File requestPomFile = mavenSession.getRequest().getPom();

        File pomFile = new File(source.getLocation());
        if (!pomFile.isFile()) {
            // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
            // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
            // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
            return model; // therefore the model shouldn't be modified.
        }

        GAV projectGav = gavOf(model);

        // add build plugin to top level project model
        // will be removed from model by plugin
        if (pomFile.equals(requestPomFile)) {
            logger.debug("add build plugin @ " + projectGav);
            if (model.getBuild() == null) {
                model.setBuild(new Build());
            }
            model.getBuild().getPlugins().add(BranchVersioningTempPomUpdateMojo.asPlugin());
        }

        if (pomFile.getParentFile().getCanonicalPath()
                .startsWith(requestPomFile.getParentFile().getCanonicalPath())) {
            String branchVersion = getBranchVersion(model);
            if (model.getParent() != null) {
                GAV parentProjectGav = gavOf(model.getParent());
                if (hasBranchVersion(parentProjectGav)) {
                    String parentBranchVersion = this.getBranchVersion(parentProjectGav);
                    logger.debug(projectGav + " adjust parent version to " + parentBranchVersion);
                    model.getParent().setVersion(parentBranchVersion);
                }
            }

            if (model.getVersion() != null) {
                logger.info(projectGav + " override version with branch version " + branchVersion);
                model.setVersion(branchVersion);
            }

        } else {
            // skip unrelated models
            logger.debug("skipping unrelated model " + pomFile);
        }

        return model;
    }


    public String getBranchVersion(Model model) {
        GAV gav = gavOf(model);
        if (!branchVersionMap.containsKey(gav)) {
            String version = generateBranchVersion(model);
            branchVersionMap.put(gav, version);
        }
        return getBranchVersion(gav);
    }

    public String getBranchVersion(GAV gav) {
        if (!hasBranchVersion(gav)) {
            throw new IllegalStateException("Unknown branch version for " + gav);
        }
        return branchVersionMap.get(gav);
    }

    public String generateBranchVersion(Model model) {
        return "SpongeBob";
    }

    public boolean hasBranchVersion(GAV gav) {
        return branchVersionMap.containsKey(gav);
    }

    public GAV gavOf(Model model) {

        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        String version = model.getVersion();

        if (model.getParent() != null) {
            if (groupId == null) {
                groupId = model.getParent().getGroupId();
            }
            if (version == null) {
                version = model.getParent().getVersion();
            }
        }

        return new GAV(groupId, artifactId, version);
    }

    public GAV gavOf(Parent parent) {
        return new GAV(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion()
        );
    }
}
