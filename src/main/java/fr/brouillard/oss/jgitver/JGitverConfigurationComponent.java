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
package fr.brouillard.oss.jgitver;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import fr.brouillard.oss.jgitver.cfg.Configuration;
import fr.brouillard.oss.jgitver.cfg.ConfigurationLoader;

@Component(role = JGitverConfiguration.class, instantiationStrategy = "singleton")
public class JGitverConfigurationComponent implements JGitverConfiguration {
    @Requirement
    private LegacySupport legacySupport = null;

    @Requirement
    private Logger logger = null;

    private volatile Configuration configuration;

    private List<File> excludedDirectories = new LinkedList<>();

    @Override
    public Configuration getConfiguration() throws MavenExecutionException {
        if (configuration == null) {
            synchronized (this) {
                if (configuration == null) {
                    MavenSession mavenSession = legacySupport.getSession();
                    final File rootDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();

                    logger.debug("using " + JGitverUtils.EXTENSION_PREFIX + " on directory: " + rootDirectory);

                    configuration = ConfigurationLoader.loadFromRoot(rootDirectory, logger);
                    initFromRootDirectory(rootDirectory, configuration.exclusions);
                }
            }
        }

        return configuration;
    }
    
    private void initFromRootDirectory(File rootDirectory, List<String> exclusions) {
        exclusions.stream().map(dirName -> new File(rootDirectory, dirName)).forEach(excludedDirectories::add);
    }
    
    @Override
    public boolean ignore(File pomFile) throws IOException {
        for (File excludedDir : excludedDirectories) {
            if (StringUtils.containsIgnoreCase(pomFile.getParentFile().getCanonicalFile().getCanonicalPath(),
                    excludedDir.getCanonicalPath())) {
                return true;
            }
        }
        return false;
    }
}
