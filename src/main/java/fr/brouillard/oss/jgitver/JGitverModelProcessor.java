// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package fr.brouillard.oss.jgitver;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

/**
 * Copyright (C) 2016 Yuriy Zaplavnov [https://github.com/xeagle2]
 * is original author of current class implementation and approach to use maven extension strategy
 * instead of maven lifecycle participants.
 * <p><strong>Configuration:</strong></p>
 * 1. Create ${maven.projectBasedir}/.mvn/extensions.xml under a root directory of project.
 * 2. Put the following content to ${maven.projectBasedir}/.mvn/extensions.xml (adapt the version).
 * <pre>{@code
 * <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *   xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache
 *   .org/xsd/core-extensions-1.0.0.xsd">
 *   <extension>
 *     <groupId>fr.brouillard.oss</groupId>
 *     <artifactId>jgitver-maven-plugin</artifactId>
 *     <version>0.2.0-SNAPSHOT</version>
 *   </extension>
 * </extensions>
 * }</pre>
 * 3. Adding the plugin to project .pom files is not necessary anymore.
 * <p>
 * Other parameters could be passed through ${maven.projectBasedir}/.mvn/maven.config as
 * </p>
 * <pre>{@code
 * -Dvariable_name1=variable_value1 -Dvariable_name2=variable_value2
 * }</pre>
 * </p>
 * <p><strong>Known issues</strong></p>
 * 1. Feature is not available if building with Jenkins <a href="https://issues.jenkins-ci
 * .org/browse/JENKINS-30058?jql=project%20%3D%20JENKINS%20AND%20status%20in%20
 * (Open%2C%20%22In%20Progress%22%2C%20Reopened)
 * %20AND%20component%20%3D%20maven-plugin%20AND%20text%20~%20%22extensions%22">JENKINS-30058</a>
 *
 * @see <a href="http://maven.apache.org/ref/3.3.9/maven-embedder">Maven Embedder</a>
 * @see <a href="https://maven.apache.org/docs/3.3.1/release-notes.html">Release Notes – Maven 3.3.1</a>
 * @see <a href="http://maven.apache.org/configure.html">Configuring Apache Maven</a>
 * @since Maven 3.3.0
 */
@Component(role = ModelProcessor.class)
public class JGitverModelProcessor extends DefaultModelProcessor {
    @Requirement
    private Logger logger = null;

    @Requirement
    private LegacySupport legacySupport = null;

    private volatile JGitverModelProcessorWorkingConfiguration workingConfiguration;

    public JGitverModelProcessor() {
        super();
    }

    public JGitverModelProcessorWorkingConfiguration getWorkingConfiguration() {
        return workingConfiguration;
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

    private void calculateVersionIfNecessary() throws Exception {
        if (workingConfiguration == null) {
            synchronized (this) {
                if (workingConfiguration == null) {
                    logger.info("jgitver-maven-plugin is about to change project(s) version(s)");

                    MavenSession mavenSession = legacySupport.getSession();

                    logger.debug("using " + JGitverUtils.EXTENSION_PREFIX + " on directory: " 
                            + mavenSession.getRequest().getMultiModuleProjectDirectory());

                    try (GitVersionCalculator gitVersionCalculator = GitVersionCalculator.location(mavenSession
                            .getRequest().getMultiModuleProjectDirectory())) {
                        gitVersionCalculator.setMavenLike(true).setNonQualifierBranches("master");

                        JGitverVersion jGitverVersion = new JGitverVersion(gitVersionCalculator);
                        JGitverUtils.fillPropertiesFromMetadatas(mavenSession.getUserProperties(), jGitverVersion, logger);

                        workingConfiguration = new JGitverModelProcessorWorkingConfiguration(
                                jGitverVersion.getCalculatedVersion(),
                                mavenSession.getRequest().getMultiModuleProjectDirectory());
                    }
                }
            }
        }
    }

    private Model provisionModel(Model model, Map<String, ?> options) throws IOException {
        try {
            calculateVersionIfNecessary();
        } catch (Exception ex) {
            throw new IOException("cannot build a Model object using jgitver", ex);
        }

        if (Objects.isNull(options.get(ModelProcessor.SOURCE))) {
            return model;
        }

        Source source = Source.class.cast(options.get(ModelProcessor.SOURCE));
        File relativePath = new File(source.getLocation()).getParentFile().getCanonicalFile();

        if (StringUtils.containsIgnoreCase(relativePath.getCanonicalPath(), workingConfiguration
                .getMultiModuleProjectDirectory().getCanonicalPath())) {
            workingConfiguration.getNewProjectVersions().put(GAV.from(model.clone()), workingConfiguration
                    .getCalculatedVersion());

            if (Objects.nonNull(model.getVersion())) {
                // TODO evaluate how to set the version only when it was originally set in the pom file 
                model.setVersion(workingConfiguration.getCalculatedVersion());
            }

            if (Objects.nonNull(model.getParent())) {
                File relativePathParent = new File(relativePath.getCanonicalPath() + File.separator + model.getParent()
                        .getRelativePath()).getParentFile().getCanonicalFile();
                if (StringUtils.containsIgnoreCase(relativePathParent.getCanonicalPath(), workingConfiguration
                        .getMultiModuleProjectDirectory().getCanonicalPath())) {
                    model.getParent().setVersion(workingConfiguration.getCalculatedVersion());
                }
            } else {
                if (Objects.isNull(model.getBuild())) {
                    model.setBuild(new Build());
                }

                if (Objects.isNull(model.getBuild().getPlugins())) {
                    model.getBuild().setPlugins(new ArrayList<>());
                }

                Optional<Plugin> pluginOptional = model.getBuild().getPlugins().stream()
                        .filter(x -> JGitverUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                                && JGitverUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                        .findFirst();

                StringBuilder pluginVersion = new StringBuilder();

                try (InputStream inputStream = getClass().getResourceAsStream("/META-INF/maven/"
                        + JGitverUtils.EXTENSION_GROUP_ID + "/" + JGitverUtils.EXTENSION_ARTIFACT_ID + "/pom"
                        + ".properties")) {
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

                if (Objects.isNull(plugin.getExecutions())) {
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

                if (Objects.isNull(pluginExecution.getGoals())) {
                    pluginExecution.setGoals(new ArrayList<>());
                }

                if (!pluginExecution.getGoals().contains(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS)) {
                    pluginExecution.getGoals().add(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);
                }

                if (Objects.isNull(plugin.getDependencies())) {
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

            try {
                legacySupport.getSession().getUserProperties().put(JGitverModelProcessorWorkingConfiguration.class
                        .getName(), JGitverModelProcessorWorkingConfiguration.serializeTo(workingConfiguration));
            } catch (JAXBException ex) {
                throw new IOException("unexpected Model serialization issue", ex);
            }
        }

        return model;
    }
}