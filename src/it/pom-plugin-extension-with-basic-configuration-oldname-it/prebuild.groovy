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
def isWindows = {
    return System.properties['os.name'].toLowerCase().contains('windows')
}

def toFilePath = { filename ->
    isWindows() ? filename : "$basedir/" + filename
}

def runCommand = { command, basedir ->
    def commandToExecute = isWindows() ? command : command.replace('"', '\'')
    def proc = commandToExecute.execute(null, basedir) 
    def sout = new StringBuilder(), serr = new StringBuilder()
    proc.waitForProcessOutput(sout, serr)
    return commandToExecute + "\n" + "out:\n" + sout.toString() + "err:\n" + serr.toString()
}

File actions = new File(baseDir, "actions-prebuild.log")
actions.write "Actions started at: " + new Date() + " in: $basedir\n"

actions << runCommand("git --version", baseDir)
actions << runCommand("git init", baseDir)
actions << runCommand("git config user.name \"nobody\"", baseDir) 
actions << runCommand("git config user.email \"nobody@nowhere.com\"", baseDir) 
actions << runCommand("echo A > " + toFilePath("content"), baseDir) 
actions << runCommand("git add " + toFilePath("."), baseDir)
actions << runCommand("git commit --message=initial_commit", baseDir)
actions << runCommand("git tag -a 1.0.0 --message=release_1.0.0", baseDir) 
actions << runCommand("echo B > " + toFilePath("content"), baseDir)
actions << runCommand("git add -u", baseDir)
actions << runCommand("git commit --message=added_B_data", baseDir)
actions << runCommand("git log --graph --oneline", basedir)

// $ git lg
// * d86e12c - (4 minutes ago) added B data - Matthieu Brouillard (HEAD -> master)
// * 9103c75 - (4 minutes ago) initial commit - Matthieu Brouillard (tag: 1.0.0)

return true
