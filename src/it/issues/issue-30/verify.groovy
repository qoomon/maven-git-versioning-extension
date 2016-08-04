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

def log = new PrintWriter( new File(basedir, "verify.log").newWriter("UTF-8"), true )
log.println( "Verify started at: " + new Date() + " in: " + basedir )

[
  "rm -rf .git"
].each{ command ->

  def proc = command.execute(null, basedir)
  def sout = new StringBuilder(), serr = new StringBuilder()
  proc.waitForProcessOutput(sout, serr)

  log.println( "cmd: " + command )
  log.println( "out:" ) ; log.println( sout.toString().trim() )
  log.println( "err:" ) ; log.println( serr.toString().trim() )
  log.println( "ret: " + proc.exitValue() )

  assert proc.exitValue() == 0

}

def buildLog = new File(basedir, "build.log").readLines()

// Check the version was used by the plugin execution
def versionChanges = buildLog.findAll { it =~ /fr.brouillard.oss.it::issue-30::0 -> 1.0.1-SNAPSHOT/ }
log.println( "versionChanges: " + versionChanges )
assert 0 < versionChanges.size()

// And check that the produced artifact was installed with the good version
File installedPomFile = new File(localRepositoryPath, "fr/brouillard/oss/it/issue-30/1.0.1-SNAPSHOT/issue-30-1.0.1-SNAPSHOT.pom")
log.println( "installedPomFile: " + installedPomFile.isFile() + " " + installedPomFile )
assert installedPomFile.isFile()
def installedPomFileVersion = installedPomFile.readLines().findAll { it =~ /<version>1.0.1-SNAPSHOT<\/version>/ }
log.println( "installedPomFileVersion: " + installedPomFileVersion )
assert 1 == installedPomFileVersion.size()

log.println( "Verify completed at: " + new Date() )
log.close()
return true
