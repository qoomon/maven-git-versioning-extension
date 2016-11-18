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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

/**
 * Works in conjunction with BranchVersioningModelProcessor.
 */
@Mojo(name = BranchVersioningFormatCheckMojo.GOAL,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class BranchVersioningFormatCheckMojo extends AbstractMojo {

    public static final String GOAL = "check-version";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private Logger logger;

    @Override
    public synchronized void execute() throws MojoExecutionException, MojoFailureException {
        if (!mavenSession.getCurrentProject().isExecutionRoot()) {
            return; // We need to attach modified poms only once
        }

        // remove plugin from top level original project model
        logger.debug("remove plugin");
        mavenSession.getCurrentProject().getOriginalModel()
                .getBuild().removePlugin(ExtensionUtil.projectPlugin());

        for (MavenProject project : mavenSession.getAllProjects()) {
            GAV gav = GAV.of(project);
            ensureSemanticVersionFormat(gav); // TODO extract to extra extension
            ensureSnapshotVersion(gav); // TODO extract to extra extension
        }
    }

    private void ensureSnapshotVersion(GAV gav) {
        logger.info(gav + " Ensure snapshot version");
        if (!gav.getVersion().endsWith("-SNAPSHOT")) {
            throw new IllegalArgumentException(gav + " version is not a snapshot version");
        }
    }

    private void ensureSemanticVersionFormat(GAV gav) {
        logger.info(gav + " Ensure semantic version format");
        if (!Semver.PATTERN.matcher(gav.getVersion()).matches()) {
            throw new IllegalArgumentException(gav + " Version does not match semantic versioning pattern " + Semver.PATTERN);
        }
    }
}
