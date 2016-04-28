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

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;

/**
 * Wrapper for a maven project/dependency identified by a groupId/artifactId/version.
 */
public class GAV { // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
    private String groupId;
    private String artifactId;
    private String version;

    /**
     * Builds an immutable GAV object.
     * 
     * @param groupId the groupId of the maven object
     * @param artifactId the artifactId of the maven object
     * @param version the version of the maven object
     */
    public GAV(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Retrieves the groupId.
     * 
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Retrieves the artifactId.
     * 
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Retrieves the version.
     * 
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Builds a GAV object from the given MavenProject object.
     * 
     * @param project the project to extract info from
     * @return a new GAV object
     */
    public static GAV from(MavenProject project) {
        return new GAV(project.getGroupId(), project.getArtifactId(), project.getVersion());
    }
    
    /**
     * Builds a GAV object from the given Model object.
     * 
     * @param model the project model to extract info from
     * @return a new GAV object
     */
    public static GAV from(Model model) {
        return new GAV(model.getGroupId(), model.getArtifactId(), model.getVersion());
    }

    /**
     * Builds a GAV object from the given Parent object.
     * 
     * @param parent the parent to extract info from
     * @return a new GAV object
     */
    public static GAV from(Parent parent) {
        return new GAV(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GAV other = (GAV) obj;
        if (artifactId == null) {
            if (other.artifactId != null) {
                return false;
            }
        } else if (!artifactId.equals(other.artifactId)) {
            return false;
        }
        if (groupId == null) {
            if (other.groupId != null) {
                return false;
            }
        } else if (!groupId.equals(other.groupId)) {
            return false;
        }
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("%s::%s::%s", groupId, artifactId, version);
    }

}
