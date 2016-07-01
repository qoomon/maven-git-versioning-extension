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

File actions = new File(baseDir, "actions-prebuild.log")
actions.write 'Actions started at: ' + new Date() + '\n'

actions << 'git init'.execute(null, baseDir).text
actions << 'git config user.name "nobody"'.execute(null, baseDir).text 
actions << 'git config user.email "nobody@nowhere.com"'.execute(null, baseDir).text 
actions << 'echo A > content'.execute(null, baseDir).text 
actions << 'git add .'.execute(null, baseDir).text
actions << 'git commit -m "initial commit"'.execute(null, baseDir).text
actions << 'git tag -a 1.0.0 -m "release 1.0.0"'.execute(null, baseDir).text 
actions << 'echo B > content'.execute(null, baseDir).text
actions << 'git add -u'.execute(null, baseDir).text
actions << 'git commit -m "added B data"'.execute(null, baseDir).text

// $ git lg
// * 25ba22f - (46 seconds ago) added B data - Matthieu Brouillard (HEAD -> master)
// * 85bffb7 - (46 seconds ago) initial commit - Matthieu Brouillard (tag: 1.0.0)
 
return true
