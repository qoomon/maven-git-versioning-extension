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

/**
 * Copyright (C) 2016 Yuriy Zaplavnov [https://github.com/xeagle2]
 * is original author of current class implementation and approach to use maven extension strategy
 * instead of maven lifecycle participants.
 * <p>Works in conjunction with JGitverModelProcessor.
 */
public class JGitverVersion {
    private final GitVersionCalculator gitVersionCalculator;
    private final String calculatedVersion;

    public JGitverVersion(GitVersionCalculator gitVersionCalculator) {
        this.gitVersionCalculator = gitVersionCalculator;
        this.calculatedVersion = gitVersionCalculator.getVersion();
    }

    public GitVersionCalculator getGitVersionCalculator() {
        return gitVersionCalculator;
    }

    public String getCalculatedVersion() {
        return calculatedVersion;
    }
}
