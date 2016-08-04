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

def log = new PrintWriter( new File(basedir, "prebuild.log").newWriter("UTF-8"), true )
log.println( "Prebuild started at: " + new Date() + " in: " + basedir )

[

  "git --version",
  "rm -rf .git",
  "git init",
  "git config user.name nobody",
  "git config user.email nobody@nowhere.com",
  "echo A > content",
  "git add .",
  "git commit --message=initial_commit",
  "git tag -a 1.0.0 --message=release_1.0.0",
  "echo B > content",
  "git add -u",
  "git commit --message=added_B_data",
  "git log --graph --oneline"

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

log.println( "Prebuild completed at: " + new Date() )
log.close()
return true
