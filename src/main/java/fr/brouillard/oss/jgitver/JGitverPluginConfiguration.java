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

import java.util.Optional;

import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JGitverPluginConfiguration {
    private Xpp3Dom pomPluginConfiguration;

    public JGitverPluginConfiguration(Xpp3Dom pomPluginConfiguration) {
        this.pomPluginConfiguration = pomPluginConfiguration;
    }
    
    public boolean autoIncrementPatch() {
        return booleanConfigChild("autoIncrementPatch", true);
    }

    public boolean useCommitDistance() {
        return booleanConfigChild("useCommitDistance", true);
    }
    
    public boolean useGitCommitId() {
        return booleanConfigChild("useGitCommitId", false);
    }
    
    public int gitCommitIdLength() {
        return intConfigChild("gitCommitIdLength", 8);
    }
    
    public String nonQualifierBranches() {
        return Optional.ofNullable(pomPluginConfiguration)
                .map(node -> node.getChild("nonQualifierBranches")).map(Xpp3Dom::getValue).orElse("master");
    }

    private boolean booleanConfigChild(String childName, boolean defaultValue) {
        return Optional.ofNullable(pomPluginConfiguration)
                .map(node -> node.getChild(childName)).map(Xpp3Dom::getValue).map(Boolean::valueOf).orElse(Boolean.valueOf(defaultValue));
    }

    private int intConfigChild(String childName, int defaultValue) {
        return Optional.ofNullable(pomPluginConfiguration)
                .map(node -> node.getChild(childName)).map(Xpp3Dom::getValue).map(Integer::parseInt).orElse(Integer.valueOf(defaultValue));
    }
}
