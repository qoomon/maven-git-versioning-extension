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
package fr.brouillard.oss.jgitver.cfg;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.apache.maven.MavenExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationLoaderTest {
    private InMemoryLogger inMemoryLogger;

    @Before
    public void init() {
        inMemoryLogger = new InMemoryLogger();
    }
    
    @After
    public void dump() {
        // System.out.println(inMemoryLogger.toString());
    }
    
    @Test
    public void can_load_a_simple_configuration() throws MavenExecutionException, IOException {
        try (ResourceConfigurationProvider fromResource = ResourceConfigurationProvider.fromResource("/config/simple.cfg.xml")) {
            Configuration cfg = ConfigurationLoader.loadFromRoot(fromResource.getConfigurationDirectory(), inMemoryLogger);
            assertThat(cfg, notNullValue());
            
            assertThat(cfg.mavenLike, is(false));
            assertThat(cfg.useCommitDistance, is(true));
        }
    }
    
    @Test
    public void can_load_a_complex_configuration_with_branching_policy() throws MavenExecutionException, IOException {
        try (ResourceConfigurationProvider fromResource = ResourceConfigurationProvider.fromResource("/config/complex-branch.cfg.xml")) {
            Configuration cfg = ConfigurationLoader.loadFromRoot(fromResource.getConfigurationDirectory(), inMemoryLogger);
            assertThat(cfg, notNullValue());
            
            assertThat(cfg.mavenLike, is(false));
            assertThat(cfg.branchPolicies, notNullValue());
            assertThat(cfg.branchPolicies.size(), is(2));
            
            BranchPolicy masterPolicy = cfg.branchPolicies.get(0);
            assertThat(masterPolicy.pattern, is("(master)"));
            assertThat(masterPolicy.transformations, notNullValue());
            assertThat(masterPolicy.transformations.size(), is(1));
            assertThat(masterPolicy.transformations.get(0), is("IGNORE"));
            
            BranchPolicy allPolicy = cfg.branchPolicies.get(1);
            assertThat(allPolicy.pattern, is("(.*)"));
            assertThat(allPolicy.transformations, notNullValue());
            assertThat(allPolicy.transformations.size(), is(2));
            assertThat(allPolicy.transformations.get(0), is("REMOVE_UNEXPECTED_CHARS"));
            assertThat(allPolicy.transformations.get(1), is("UPPERCASE_EN"));
        }
    }
}
