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

def baseDir = new File("$basedir")

File actions = new File(baseDir, "verify-actions.log")
actions.write 'Actions started at: ' + new Date() + '\n'

actions << 'rm -rf .git'.execute(null, baseDir).text

// Check the version was used by the plugin execution
def foundLines = new File("$basedir", "build.log").readLines().findAll { it =~ /fr.brouillard.oss.it::pom-plugin-extension-with-basic-configuration-it::0 -> 1.0.1-1/ } 
assert 0 < foundLines.size

// And check that the produced artifact was installed with the good version
File installedPomFile = new File("$basedir" + "/../../local-repo/fr/brouillard/oss/it/pom-plugin-extension-with-basic-configuration-it/1.0.1-1/", "pom-plugin-extension-with-basic-configuration-it-1.0.1-1.pom")
assert installedPomFile.isFile()
assert 1 == installedPomFile.readLines().findAll { it =~ /<version>1.0.1-1<\/version>/ }.size()

return true
