#! /bin/bash
#
# Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


JGITVER_LATEST_VERSION=`curl -s "http://search.maven.org/solrsearch/select?q=g:%22fr.brouillard.oss%22+AND+a:%22jgitver-maven-plugin%22&core=gav&rows=1&wt=json" | python -mjson.tool \
 | grep '"v"' | tr -d '"' | tr -d ' ' | cut -d ':' -f 2`


if [ ! -d "$PWD/.mvn" ]; then
  mkdir $PWD/.mvn
  cat > $PWD/.mvn/extensions.xml << EOF
  <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
    <extension>
      <groupId>fr.brouillard.oss</groupId>
      <artifactId>jgitver-maven-plugin</artifactId>
      <version>$JGITVER_LATEST_VERSION</version>
    </extension>
  </extensions>
EOF
  echo == jgitver-maven-plugin:$JGITVER_LATEST_VERSION added under $PWD/.mvn/extensions.xml
else
  echo == Maven extensions directory already exists
  echo == please update manually your '.mvn/extensions.xml' file to jgitver-maven-plugin:$JGITVER_LATEST_VERSION
fi
