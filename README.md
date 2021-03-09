# Maven Git Versioning Extension [![Sparkline](https://stars.medv.io/qoomon/maven-git-versioning-extension.svg?)](https://stars.medv.io/qoomon/maven-git-versioning-extension)

[![Maven Central](https://img.shields.io/maven-central/v/me.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22me.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22)
[![Changelog](https://badgen.net/badge/changelog/%E2%98%85/blue)](CHANGELOG.md)

[![Build Workflow](https://github.com/qoomon/maven-git-versioning-extension/workflows/Build/badge.svg)](https://github.com/qoomon/maven-git-versioning-extension/actions?query=workflow%3ABuild)
[![LGTM Grade](https://img.shields.io/lgtm/grade/java/github/qoomon/maven-git-versioning-extension)](https://lgtm.com/projects/g/qoomon/maven-git-versioning-extension)


**ℹ Also available as [Gradle Plugin](https://github.com/qoomon/gradle-git-versioning-plugin)**


This extension can virtually set project version and properties, based on current **Git status**.

ℹ **No POM files will be modified, version and properties are modified in memory only.**
* Get rid of...
    * editing `pom.xml`
    * managing project versions with within files and Git tags.
    * Git merge conflicts.
* Highly customizable configuration, see example below.
![Example](doc/MavenGitVersioningExtension.png)

## Usage

⚠️ minimal required maven version is `3.6.3`

### Add Extension to Maven Project

create or update `${rootProjectDir}/.mvn/extensions.xml` file

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>me.qoomon</groupId>
        <artifactId>maven-git-versioning-extension</artifactId>
        <version>6.2.0</version>
    </extension>

</extensions>
```

ℹ Consider [CI/CD](#cicd-setup) section when running this extension in a CI/CD environment 

## Configure Extension

You can configure the final version format for specific branches and tags separately.

Create `${rootProjectDir}/.mvn/maven-git-versioning-extension.xml`.

**Example:** `maven-git-versioning-extension.xml`

```xml
<gitVersioning>
    <branch>
        <pattern>main</pattern>
        <versionFormat>${version}</versionFormat>
    </branch>
    <branch>
         <pattern>feature/(.+)></pattern>
         <versionFormat>${1}-SNAPSHOT</versionFormat>
     </branch>
    <tag>
        <pattern>v([0-9].*)></pattern>
        <versionFormat>${1}</versionFormat>
    </tag>
</gitVersioning>
```

- *optional* `<disable>` global disable(`true`)/enable(`false`) extension.
    - Can be overridden by command option, see (Parameters & Environment Variables)[#parameters-&-environment-variables].

- *optional* `<updatePom>` global enable(`true`)/disable(`false`) version and properties update in original pom file.
    - Can be overridden by command option, see (Parameters & Environment Variables)[#parameters-&-environment-variables].

- *optional* `<preferTags>` global enable(`true`)/disable(`false`) prefer tag rules over branch rules if both match.

- `<branch>` specific version format definition.
    - `<pattern>` An arbitrary regex to match branch names (has to be a **full match pattern** e.g. `feature/.+` )
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - `<property>` A property definition to update the value of a property
        - `<name>` The property name
        - `<valueFormat>` The new value format of the property, see [Version Format & Placeholders](#version-format--placeholders)
    - *optional* `<updatePom>` Enable(`true`) or disable(`false`) version and properties update in original pom fill (will override global `<updatePom>` value)
    - ⚠ **considered if...**
        * HEAD attached to a branch `git checkout <BRANCH>`<br>
        * Or branch name is provided by environment variable or command line parameter

- `<tag>` specific version format definition.
    - `<pattern>` An arbitrary regex to match tag names (has to be a **full match pattern** e.g. `v[0-9].*` )
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - `<property>` A property definition to update the value of a property
        - `<name>` The property name
        - `<valueFormat>` The new value format of the property, see [Version Format & Placeholders](#version-format--placeholders)
    - *optional* `<updatePom>` Enable(`true`) or disable(`false`) version update in original pom fill (will override global `<updatePom>` value)
    - ⚠ **considered if...**
        * HEAD is detached `git checkout <TAG>`<br>
        * Or tag name is provided by environment variable or command line parameter
  
- `<commit>` specific version format definition.
    - `<versionFormat>` An arbitrary string, see [Version Format & Placeholders](#version-format--placeholders)
    - `<property>` A property definition to update the value of a property
        - `<name>` The property name
        - `<valueFormat>` The new value format of the property, see [Version Format & Placeholders](#version-format--placeholders)
    - ⚠ **considered if...**
        * HEAD is detached `git checkout <COMMIT>` and no matching version tag is pointing to HEAD<br>

#### Format Placeholders

ℹ whole `versionFormat` will be slugified automatically, that means all `/` characters replaced by `-`

ℹ define placeholder default value (placeholder is not defined) like this `${name:-default_value}`<br>
  e.g `${buildNumber:-0}` or `${buildNumber:-local}` 

ℹ define placeholder overwrite value (placeholder is defined) like this `${name:+overwrite_value}`<br>
  e.g `${dirty:-SNAPSHOT}` resolves to `-SNAPSHOT` instead of `-DIRTY`
 
- `${ref}`
    - current ref name (branch name, tag name or commit hash)
- `${ref.slug}`
    - like `${ref}` with all `/` replaced by `-`

- `${branch}` (only available within branch configuration)
    - The branch name of `HEAD`
    - e.g. 'master', 'feature/next-big-thing', ...
- `${branch.slug}`
    - like `${branch}` with all `/` replaced by `-`    
 
- `${tag}` (only available within tag configuration)
    - The tag name that points at `HEAD`, if multiple tags point at `HEAD` latest version is selected
    - e.g. 'version/1.0.1', 'v1.2.3', ...
- `${tag.slug}`
    - like `${tag}` with all `/` replaced by `-`    
    
- `${commit}`
    - The `HEAD` commit hash
    - e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'

- `${commit.short}`
    - The short `HEAD` commit hash (7 characters)
    - e.g. '0fc2045'

- `${commit.timestamp}`
    - The `HEAD` commit timestamp (epoch seconds)
    - e.g. '1560694278'
- `${commit.timestamp.year}`
    - The `HEAD` commit year
    - e.g. '2021'
- `${commit.timestamp.month}`
    - The `HEAD` commit month of year
    - e.g. '01'
- `${commit.timestamp.day}`
    - The `HEAD` commit day of month
    - e.g. '01'
- `${commit.timestamp.hour}`
    - The `HEAD` commit hour of day (24h)
    - e.g. '01'
- `${commit.timestamp.minutes}`
    - The `HEAD` commit minutes of hour
    - e.g. '01'
- `${commit.timestamp.seconds}`
    - The `HEAD` commit seconds of minute
    - e.g. '01'
- `${commit.timestamp.datetime}`
    - The `HEAD` commit timestamp formatted as `yyyyMMdd.HHmmss`
    - e.g. '20190616.161442'

- `Pattern Groups`
    - Contents of group in the regex pattern can be addressed `${GROUP_NAME}` or `${GROUP_INDEX}`
    - `${GROUP_NAME.slug}` or `${GROUP_INDEX.slug}`
        - like `${GROUP_NAME}` or `${GROUP_INDEX}` with all `/` replaced by `-`  
    - Examples
        - Named Group
            ```xml
            <branch>
                <pattern><![CDATA[feature/(?<feature>.+)]]></pattern>
                <versionFormat>${feature}-SNAPSHOT</versionFormat>
            </branch>
            ```
        - Group Index
            ```xml
            <tag>
                <pattern>v([0-9].*)'</pattern>
                <versionFormat>${1}</versionFormat>
            </tag>
            ```
        
- `${version}`
    - `version` set in `pom.xml`
    - e.g. '1.0.0-SNAPSHOT'
- `${version.release}`
    - like `${version}` without `-SNAPSHOT` postfix
    - e.g. '1.0.0'
    
- `${dirty}`
    - if repository has untracked files or uncommited changes this placeholder will resolve to `-DIRTY`, otherwise it will resolve to an empty string.  
- `${dirty.snapshot}`
    - if repository has untracked files or uncommited changes this placeholder will resolve to `-SNAPSHOT`, otherwise it will resolve to an empty string.

- `${value}` - Only available within property format
    - value of matching property
      
### Parameters & Environment Variables

- Disable Extension
    - **Environment Variables**
        - `export VERSIONING_DISABLE=true`
    - **Command Line Parameters**
        - `mvn ... -Dversioning.disable=true`
            
- Provide **branch** or **tag** name
    - **Environment Variables**
        - `export VERSIONING_GIT_BRANCH=$PROVIDED_BRANCH_NAME`
        - `export VERSIONING_GIT_TAG=$PROVIDED_TAG_NAME`
    - **Command Line Parameters**
        - `mvn ... -Dgit.branch=$PROVIDED_BRANCH_NAME`
        - `mvn ... -Dgit.tag=$PROVIDED_TAG_NAME`
        
  ℹ Especially useful for **CI builds** see [Miscellaneous Hints](#miscellaneous-hints)

- Update `pom.xml`
    - **Environment Variables**
        - `export VERSIONING_UPDATE_POM=true`
    - **Command Line Parameters**
        - `mvn ... -Dversioning.updatePom=true`

- **Prefer Tags** for Versioning instead of Branches
    - **Environment Variables**
        - `export VERSIONING_PREFER_TAGS=true`
    - **Command Line Parameters**
        - `mvn ... -Dversioning.preferTags=true`

## Provided Project Properties

- `git.commit` e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'
- `git.ref` value of branch or tag name or commit hash
    - `git.ref.slug` like `git.ref` with all `/` replaced by `-`
- `git.branch` e.g. 'feature/next-big-thing', only set for branch versioning
    - `git.branch.slug` like `git.branch` with all `/` replaced by `-`
- `git.tag` e.g. 'v1.2.3', only set for tag versioning
    - `git.tag.slug` like `git.tag` with all `/` replaced by `-`
- `git.commit.timestamp` e.g. '1560694278'
- `git.commit.timestamp.datetime` e.g. '2019-11-16T14:37:10Z'
- `git.dirty` repository's dirty state indicator `true` or `false`

# Miscellaneous Hints

### Commandline To Print Project Version
`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`

### Reproducible builds ###
The reproducible builds feature (https://maven.apache.org/guides/mini/guide-reproducible-builds.html) newly introduced in maven can be easily supported with this extension by using the latest commit timestamp as build timestamps.
```xml
<project.build.outputTimestamp>${git.commit.timestamp.datetime}</project.build.outputTimestamp>
```
### IntelliJ Setup
For a flawless experience you need to disable this extension during project import.
Disable it by adding `-Dversioning.disable=true` to Maven Importer VM options (Preferences > Build, Execution, Deployment > Build Tools > Maven > Importing > VM options for importer).

### CI/CD Setup
Most CI/CD systems do checkouts in a detached HEAD state so no branch information is available, however they provide environment variables with this information. You can provide those, by using [Parameters & Environment Variables](#parameters--environment-variables). Below you'll find some setup example for common CI/CD systems.

#### GitHub Actions Setup
execute this snippet before running your `mvn` command
```shell
if  [[ "$GITHUB_REF" = refs/tags/* ]]; then
    export VERSIONING_GIT_TAG=${GITHUB_REF#refs/tags/};
elif [[ "$GITHUB_REF" = refs/heads/* ]]; then
    export VERSIONING_GIT_BRANCH=${GITHUB_REF#refs/heads/};
elif [[ "$GITHUB_REF" = refs/pull/*/merge ]]; then
    export VERSIONING_GIT_BRANCH=${GITHUB_REF#refs/pull/};
fi
```

#### GitLab CI Setup
execute this snippet before running your `mvn` command
```shell
before_script:
  - export VERSIONING_GIT_TAG=$CI_COMMIT_TAG;
    export VERSIONING_GIT_BRANCH=$CI_COMMIT_BRANCH;
```

#### Jenkins Setup
execute this snippet before running your `mvn` command
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
