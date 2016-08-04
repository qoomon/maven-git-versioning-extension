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

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Configuration {
    @XmlElement(name = "mavenLike")
    public boolean mavenLike = true;
    @XmlElement
    public boolean autoIncrementPatch = true;
    @XmlElement
    public boolean useCommitDistance = false;
    @XmlElement
    public boolean useDirty = false;
    @XmlElement
    public boolean useDefaultBranchingPolicy = true;
    @XmlElement
    public boolean useGitCommitId = false;
    @XmlElement
    public int gitCommitIdLength = 8;
    @XmlElement
    public String nonQualifierBranches = "master";
    @XmlElementWrapper(name = "exclusions")
    @XmlElement(name = "exclusion")
    public List<String> exclusions = new LinkedList<>();
    @XmlElementWrapper(name = "branchPolicies")
    @XmlElement(name = "branchPolicy")
    public List<BranchPolicy> branchPolicies;
}
