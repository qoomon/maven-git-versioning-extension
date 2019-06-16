# Maven Git Versioning Extension


[![Maven Central](https://img.shields.io/maven-central/v/me.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22me.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22)

[![Build Status](https://travis-ci.org/qoomon/maven-git-versioning-extension.svg?branch=master)](https://travis-ci.com/qoomon/maven-git-versioning-extension/branches)
[![Actions Status](https://wdp9fww0r9.execute-api.us-west-2.amazonaws.com/production/badge/qoomon/maven-git-versioning-extension?logo=none&label=actions)](https://github.com/qoomon/maven-git-versioning-extension/actions)

[![Changelog](https://badgen.net/badge/changelog/%E2%98%85/blue)](#changelog)

ℹ Also available as [Gradle Plugin](https://github.com/qoomon/gradle-git-versioning-plugin)


This extension will virtually set project versions, based on current **Git branch** or **Git tag**.

ℹ **The pom files will not be modified, versions are modified in memory only.**
* Get rid of...
    * editing `pom.xml`
    * managing version by git and within files
    * Git merge conflicts

![Example](doc/MavenGitVersioningExtension.png)

## Install

### Add Extension

create or update `${basedir}/.mvn/extensions.xml` file

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>me.qoomon</groupId>
        <artifactId>maven-git-versioning-extension</artifactId>
        <version>LATEST</version>
    </extension>

</extensions>
```

ℹ Consider [CI/CD](#cicd) section when running this extension in a CI/CD environment 

## Configure Extension

You can configure the final version format for specific branches and tags separately.

Create `${basedir}/.mvn/maven-git-versioning-extension.xml`.

**Example:** `maven-git-versioning-extension.xml`

```xml
<gitVersioning>
    <branch>
        <pattern>master</pattern>
        <versionFormat>${version}</versionFormat>
    </branch>
    <branch>
         <pattern><![CDATA[feature/(?<feature>.+)]]></pattern>
         <versionFormat>${feature}-SNAPSHOT</versionFormat>
     </branch>
    <tag>
        <pattern><![CDATA[v(?<tagVersion>[0-9].*)]]></pattern>
        <versionFormat>${tagVersion}</versionFormat>
    </tag>
    <commit>
        <versionFormat>${commit.short}</versionFormat>
    </commit>
</gitVersioning>
```

- *optional* `<updatePom>` global enable(`true`)/disable(`false`) version update in original pom file.

- `<branch>` specific version format definition.
    - `<pattern>` An arbitrary regex to match branch names (has to be a **full match pattern** e.g. `feature/.+` )
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - *optional* `<updatePom>` Enable(`true`) or disable(`false`) version update in original pom fill (will override global `<updatePom>` value)
    - ⚠ **considered if...**
        * HEAD attached to a branch `git checkout <BRANCH>`<br>
        * Or branch name is provided by environment variable or command line parameter

- `<tag>` specific version format definition.
    - `<pattern>` An arbitrary regex to match tag names (has to be a **full match pattern** e.g. `v[0-9].*` )
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - *optional* `<updatePom>` Enable(`true`) or disable(`false`) version update in original pom fill (will override global `<updatePom>` value)
    - ⚠ **considered if...**
        * HEAD is detached `git checkout <TAG>`<br>
        * Or tag name is provided by environment variable or command line parameter
  
- `<commit>` specific version format definition.
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - ⚠ **considered if...**
        * HEAD is detached `git checkout <COMMIT>` and no matching version tag is pointing to HEAD<br>

#### Version Format & Placeholders

ℹ `/` characters within final version will be replaced by `-`**

- `${ref}`
    - current ref name (branch name, tag name or commit hash)

- `${branch}` (only available within branch configuration)
    - The branch name of `HEAD`
    - e.g. 'master', 'feature/next-big-thing', ...

- `${tag}` (only available within tag configuration)
    - The tag name that points at `HEAD`, if multiple tags point at `HEAD` latest version is selected
    - e.g. 'version/1.0.1', 'v1.2.3', ...

- `${commit}`
    - The `HEAD` commit hash
    - e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'

- `${commit.short}`
    - The short `HEAD` commit hash (7 characters)
    - e.g. '0fc2045'

- `${commit.timestamp}`
    - The `HEAD` commit timestamp (epoch seconds)
    - e.g. '1560694278'
    
- `${commit.timestamp.datetime}`
    - The `HEAD` commit timestamp formatted as `yyyyMMdd.HHmmss`
    - e.g. '20190616.161442'

- `Pattern Groups`
    - Contents of group in the regex pattern can be addressed by `group name` or `group index` e.g.
    - Named Group Example
        ```groovy
        pattern = 'feature/(?<feature>.+)'
        versionFormat = '${feature}-SNAPSHOT'    
        ```
    - Group Index Example
        ```groovy
        pattern = 'v([0-9].*)'
        versionFormat = '${1}'
        ```
        
- `${version}`
    - `version` set in `pom.xml`
    - e.g. '1.0.0-SNAPSHOT'
    
- `${version.release}`
    - `version` set in `pom.xml` without `-SNAPSHOT` postfix
    - e.g. '1.0.0'
      
### Parameters & Environment Variables

- Provide **branch** or **tag** name
    - **Environment Variables**
        - `export VERSIONING_GIT_BRANCH=$PROVIDED_BRANCH_NAME`
        - `export VERSIONING_GIT_TAG=$PROVIDED_TAG_NAME`
    - **Command Line Parameters**
        - `maven ... -Dgit.branch=$PROVIDED_BRANCH_NAME`
        - `maven ... -Dgit.tag=$PROVIDED_TAG_NAME`
  
  ℹ Especially useful for **CI builds** see [Miscellaneous Hints](#miscellaneous-hints)

## Provided Project Properties

- `git.ref` value of branch of tag name, always set
- `git.branch` e.g. 'feature/next-big-thing', only set for branch versioning
- `git.tag` e.g. 'v1.2.3', only set for tag versioning
- `git.commit` e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'
- `git.ref.<PATTERN_GROUP>`


## Miscellaneous Hints

### Commandline To Print Project Version
`mvn --non-recursive exec:exec -Dexec.executable='echo' -Dexec.args='${project.version}' -q`

### CI/CD
Most CI/CD systems do checkouts in a detached HEAD state so no branch information is available, however they provide environment variables with this information. You can provide those, by using [Parameters & Environment Variables](#parameters--environment-variables). Below you'll find some setup example for common CI/CD systems.

#### GitLab CI Setup
execute this snippet before running your `maven` command
```shell
before_script:
  - if [ -n "$CI_COMMIT_TAG" ]; then
       export VERSIONING_GIT_TAG=$CI_COMMIT_TAG;
    else
       export VERSIONING_GIT_BRANCH=$CI_COMMIT_REF_NAME;
    fi
```

#### Jenkins Setup
execute this snippet before running your `maven` command
```shell
if [[ "$GIT_BRANCH" = origin/tags/* ]]; then
    export VERSIONING_GIT_TAG=${GIT_BRANCH#origin/tags/};
else 
    export VERSIONING_GIT_BRANCH=${GIT_BRANCH#origin/};
fi
```

## Build
```shell
  - mvn install
  # run integration tests after install, 
  # integration tests will run with LATEST version of extension installed
  - mvn failsafe:integration-test
```

# Changelog

## 4.1.0
* Add config option(`<update>`) to update version in original pom file.  see [Configure Extension](#configure-extension)

## 4.0.0
* Major Refactoring, Simplification
* Also available as [Gradle Plugin](https://github.com/qoomon/gradle-git-versioning-plugin) 
* **New Provided Project Properties**
  * `git.ref` value of branch of tag name, always set

### Breaking Changes
* **Restructured XML Config**
  * renamed root tag `<configuration>` -> `<gitVersioning>`
  * removed nested structure
  * see [Configure Extension](#configure-extension)
* **Renamed Environment Variables**
  * `MAVEN_PROJECT_BRANCH` ->  `VERSIONING_GIT_BRANCH`
  * `MAVEN_PROJECT_TAG` -> `VERSIONING_GIT_TAG`
* **Renamed Maven Parameters**
  * `-Dproject.branch` -> `-Dgit.branch`
  * `-Dproject.tag` -> `-Dgit.tag`
* **Removed Mave Parameters**
  * `-DgitVersioning` - disable the extension by a parameter is no longer supported
* **Renamed Provided Project Properties**
  * `project.branch` -> `git.branch`
  * `project.tag` -> `git.tag`
  * `project.commit` -> `git.commit`

