# Branch Versioning Extension 
[![Maven Central](https://img.shields.io/maven-central/v/com.qoomon/maven-branch-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.qoomon%22%20AND%20a%3A%22maven-branch-versioning-extension%22)
[![Build Status](https://travis-ci.org/qoomon/maven-branch-versioning-extension.svg?branch=master)](https://travis-ci.org/qoomon/maven-branch-versioning-extension)
[![Dependency Status](https://dependencyci.com/github/qoomon/maven-branch-versioning-extension/badge)](https://dependencyci.com/github/qoomon/maven-branch-versioning-extension)
 
This extension will generate project versions, based on current git branch.

**The pom files will not be modified, versions are modified in memory only.**

## Install 

### add core extension
create or update ${basedir}/.mvn/extensions.xml
``` xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.qoomon</groupId>
        <artifactId>maven-branch-versioning-extension</artifactId>
        <version>LATEST</version>
    </extension>

</extensions>
```

### Configure
Default Version Format: '${branchName}-SNAPSHOT'

For Custom Configuration create ${basedir}/.mvn/maven-branch-versioning-extension.xml
``` xml
<configuration>
    <branches>

        <branch>
            <pattern>^master$</pattern>
            <versionFormat>${version.release}</versionFormat>
        </branch>

    </branches>
</configuration>
```
#### Branch Pattern
The branch pattern is an arbitrary regex.

#### Version Format
The version format is an arbitrary string.

##### Available Placeholders
- ${version}
  - pom file version
  - e.g. '1.2.3-SNAPSHOT'
- ${version.release}
  - pom file version without '-SNAPSHOT'
  - e.g. '1.2.3'
- ${branch}
  - current branch name
  - '/' characters within branch name will be replaced by '-'
  - e.g. 'master', 'feature-next-big-thing', ...
- ${commit}
  - current commit hash
  - e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'
- ${commit.short}
  - short current commit hash
  - e.g. '0fc2045'

### Options
- Provide Branch Name in Detached Head State
  - create a branch before maven execution
    - e.g. ```git checkout -b $BRANCH_NAME```
  - as maven property ```project.branch```
    - e.g. ```mvn -Dproject.branch=$BRANCH_NAME ...```
  - as environment variable ```MAVEN_PROJECT_BRANCH```
    - e.g. ```export MAVEN_PROJECT_BRANCH=$BRANCH_NAME```
- Disable Branch Versioning 
  - as maven property ```project.branch=disable```
    - e.g. ```mvn -Dproject.branch=_ ...```
 

### Provided Properties
- project.branch
- project.commit
