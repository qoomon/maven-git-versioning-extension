# Maven Git Versioning Extension [![Sparkline](https://stars.medv.io/qoomon/maven-git-versioning-extension.svg?)](https://stars.medv.io/qoomon/maven-git-versioning-extension)

[![Maven Central](https://img.shields.io/maven-central/v/me.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22me.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22)
[![Changelog](https://badgen.net/badge/changelog/%E2%98%85/blue)](CHANGELOG.md)

[![Build Workflow](https://github.com/qoomon/maven-git-versioning-extension/workflows/Build/badge.svg)](https://github.com/qoomon/maven-git-versioning-extension/actions?query=workflow%3ABuild)
[![LGTM Grade](https://img.shields.io/lgtm/grade/java/github/qoomon/maven-git-versioning-extension)](https://lgtm.com/projects/g/qoomon/maven-git-versioning-extension)

**ℹ Also available as [Gradle Plugin](https://github.com/qoomon/gradle-git-versioning-plugin)**

This extension can virtually set project version and properties, based on current **Git status**.

ℹ **No POM files will be modified, version and properties are modified in memory only.**

* Get rid of…
    * editing `pom.xml`
    * managing project versions with within files and Git tags.
    * Git merge conflicts.
* Highly customizable configuration, see example below.
  ![Example](docs/MavenGitVersioningExtension.png)

## Usage

⚠️ minimal required maven version is `3.6.3`

### Add Extension to Maven Project

create or update `${rootProjectDir}/.mvn/extensions.xml` file

```xml

<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>me.qoomon</groupId>
        <artifactId>maven-git-versioning-extension</artifactId>
        <version>7.0.0</version>
    </extension>

</extensions>
```

ℹ Consider [CI/CD](#cicd-setup) section when running this extension in a CI/CD environment

## Configure Extension

You can configure the final version format for specific branches and tags separately.

Create `${rootProjectDir}/.mvn/maven-git-versioning-extension.xml`.

**Example:** `maven-git-versioning-extension.xml`

```xml

<configuration xmlns="https://github.com/qoomon/maven-git-versioning-extension" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="https://github.com/qoomon/maven-git-versioning-extension https://qoomon.github.io/maven-git-versioning-extension/configuration-7.0.0.xsd">

    <disable>false</disable>

    <describeTagPattern><![CDATA[v(?<version>.*)]]></describeTagPattern>
    <updatePom>false</updatePom>

    <rev>
        <version>${describe.tag.version}-${describe.distance}-${commit.short}</version>
    </rev>

    <refs>
        <ref type="tag">
            <pattern><![CDATA[v(?<version>.*)]]></pattern>
            <version>${ref.version}</version>
        </ref>
        <ref type="branch">
            <pattern>main</pattern>
            <version>${ref}-${commit.short}</version>
        </ref>
        <ref type="branch">
            <pattern>feature/.+</pattern>
            <version>${ref}-SNAPSHOT</version>
            <properties>
                <foo>${value}-${ref}</foo>
            </properties>
        </ref>
    </refs>

</configuration>
```

- `<disable>` global disable(`true`)/enable(`false`) extension.
    - Can be overridden by command option, see (Parameters & Environment Variables)[#parameters-&-environment-variables]
      .

- `<describeTagPattern>` An arbitrary regex to match tag names for git describe command (has to be a **full match
  pattern** e.g. `v.+`)
- `<updatePom>` Enable(`true`)/disable(`false`) version and properties update in original pom file.
    - Can be overridden by command option, see (Parameters & Environment Variables)[#parameters-&-environment-variables]
      .

- `<refs considerTagsOnlyIfHeadIsDetached="BOOLEAN">` List of ref configs, ordered by priority. First matching config
  will be used.
    - `considerTagsOnlyIfHeadIsDetached` By default, tags pointing to HEAD are always considered
        - ⚠️ This behaviour can lead to performance issue on projects with a lot of tags, you can change this behaviour
          by setting `<refs considerTagsOnlyIfHeadIsDetached="true">`
          <br><br>

    - `<ref type="TYPE">` specific version format definition.
        - *required* `type` Ref type indicates which ref is matched against `pattern`, can be `branch` or `tag`
        - `<pattern>` An arbitrary regex to match ref names
            - has to be a **full match pattern** e.g. `main` or `feature/.+`
              <br><br>

        - `<describeTagPattern>` An arbitrary regex to match tag names for git describe command
            - has to be a **full match pattern** e.g. `v.+`)
            - will override global `<describeTagPattern>` value
        - `<updatePom>` Enable(`true`) or disable(`false`) version and properties update in original pom fill
            - will override global `<updatePom>` value
              <br><br>

        - `<version>` An arbitrary string, see [Format Placeholders](#format-placeholders)
        - `<properties>`
            - `<key>value</key>` A property definition to update the value of a property.
                - `key` The property name
                - `value` The new value format of the property, see [Format Placeholders](#format-placeholders)

- `<rev>` Rev config is used if no ref config is matching current git situation.
    - same as `<ref>` config, except `type` attribute and `<pattern>` element.

### Format Placeholders

ℹ `….slug` placeholders means all `/` characters are replaced by `-`.

ℹ Final `version` will be slugified automatically, so no need to use `${….slug}` placeholders in `<version>` format.

ℹ define placeholder default value (placeholder is not defined) like this `${name:-DEFAULT_VALUE}`<br>
e.g `${env.BUILD_NUMBER:-0}` or `${env.BUILD_NUMBER:-local}`

ℹ define placeholder overwrite value (placeholder is defined) like this `${name:+OVERWRITE_VALUE}`<br>
e.g `${dirty:-SNAPSHOT}` resolves to `-SNAPSHOT` instead of `-DIRTY`

###### Placeholders

- `${env.VARIABLE}`
    - Value of environment variable `VARIABLE`
      <br><br>

- `${version}`
    - `<version>` set in `pom.xml`
    - e.g. '1.0.0-SNAPSHOT'
- `${version.release}`
    - like `${version}` without `-SNAPSHOT` postfix
    - e.g. '1.0.0'
      <br><br>

- `${ref}` `${ref.slug}`
    - HEAD ref name (branch or tag name or commit hash)
- `Ref Pattern Groups`
    - Content of regex groups in `<ref><pattern>` can be addressed like this:
    - `${ref.GROUP_NAME}` `${ref.GROUP_NAME.slug}`
      `${ref.GROUP_INDEX}` `${ref.GROUP_INDEX.slug}`
    - Named Group Example
        ```xml
        <branch>
            <pattern><![CDATA[feature/(?<feature>.+)]]></pattern>
            <versionFormat>${ref.feature}-SNAPSHOT</versionFormat>
        </branch>
        ```
        <br>

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
- `${commit.timestamp.minute}`
    - The `HEAD` commit minute of hour
    - e.g. '01'
- `${commit.timestamp.second}`
    - The `HEAD` commit second of minute
    - e.g. '01'
- `${commit.timestamp.datetime}`
    - The `HEAD` commit timestamp formatted as `yyyyMMdd.HHmmss`
    - e.g. '20190616.161442'
      <br><br>

- `${describe}`
    - Will resolve to `git describe` output
    - ⚠️ Can lead to performance issue on projects with a lot of tags
- `${describe.distance}`
    - The distance count to last matching tag
- `${describe.tag}`
    - The matching tag of `git describe`
    - Describe Tag Pattern Groups
        - Content of regex groups in `<describeTagPattern>` can be addressed like this:
        - `${describe.tag.GROUP_NAME}` `${describe.tag.GROUP_NAME.slug}`
          `${describe.tag.GROUP_INDEX}` `${describe.tag.GROUP_INDEX.slug}`
        - Named Group Example
            ```xml
            <ref type="branch">
                <pattern>main</pattern>
                <describeTagPattern><![CDATA[v(?<version>.*)]]></describeTagPattern>
                <versionFormat>${describe.tag.version}-SNAPSHOT</versionFormat>
            </ref>
            ```
            <br> 

- `${dirty}`
    - If repository has untracked files or uncommitted changes this placeholder will resolve to `-DIRTY`, otherwise it
      will resolve to an empty string.
    - ⚠️ Can lead to performance issue on very large projects
- `${dirty.snapshot}`
    - Like `${dirty}`, but will resolve to `-SNAPSHOT`
      <br><br>

- `${value}` - Only available within property format
    - Original value of matching property

### Parameters & Environment Variables

- Disable Extension
    - **Environment Variables**
        - `export VERSIONING_DISABLE=true`
    - **Command Line Parameters**
        - `mvn … -Dversioning.disable`

- Provide **branch** or **tag** name
    - **Environment Variables**
        - `export VERSIONING_GIT_REF=$PROVIDED_REF` e.g. `refs/tags/v1.0.0` or `refs/heads/main`
        - `export VERSIONING_GIT_BRANCH=$PROVIDED_BRANCH_NAME` e.g. `main` or `refs/heads/main`
        - `export VERSIONING_GIT_TAG=$PROVIDED_TAG_NAME` e.g. `v1.0.0` or `refs/tags/v1.0.0`
    - **Command Line Parameters**
        - `mvn … -Dgit.ref=$PROVIDED_REF`
        - `mvn … -Dgit.branch=$PROVIDED_BRANCH_NAME`
        - `mvn … -Dgit.tag=$PROVIDED_TAG_NAME`

  ℹ Especially useful for **CI builds** see [Miscellaneous Hints](#miscellaneous-hints)

- Update `pom.xml`
    - **Environment Variables**
        - `export VERSIONING_UPDATE_POM=true`
    - **Command Line Parameters**
        - `mvn … -Dversioning.updatePom`

## Provided Project Properties

- `git.commit` e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'
- `git.commit.short` e.g. '0fc2045'
- `git.commit.timestamp` e.g. '1560694278'
- `git.commit.timestamp.datetime` e.g. '2019-11-16T14:37:10Z'

- `git.ref` `git.ref.slug` HEAD ref name (branch or tag name or commit hash)

# Miscellaneous Hints

### Enable Debug/Trace Logging for extension

`mvn … -Dorg.slf4j.simpleLogger.log.me.qoomon.maven.gitversioning=debug`

### Commandline To Print Project Version

`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`

### Reproducible builds ###

The reproducible builds feature (https://maven.apache.org/guides/mini/guide-reproducible-builds.html) newly introduced
in maven can be easily supported with this extension by using the latest commit timestamp as build timestamps.

```xml

<project.build.outputTimestamp>${git.commit.timestamp.datetime}</project.build.outputTimestamp>
```

### IntelliJ Setup

For a flawless experience you need to disable this extension during project import. Disable it by
adding `-Dversioning.disable=true` to Maven Importer VM options (Preferences > Build, Execution, Deployment > Build
Tools > Maven > Importing > VM options for importer).

### CI/CD Setup

Most CI/CD systems do checkouts in a detached HEAD state so no branch information is available, however they provide
environment variables with this information. You can provide those, by
using [Parameters & Environment Variables](#parameters--environment-variables). Below you'll find some setup example for
common CI/CD systems.

#### GitHub Actions Setup

execute this snippet before running your `mvn` command

```shell
export VERSIONING_GIT_REF=$GITHUB_REF;
```

#### GitLab CI Setup

Global Setuo
```sell
variables:
  VERSIONING_GIT_TAG: ${CI_COMMIT_TAG}
  VERSIONING_GIT_BRANCH: ${CI_COMMIT_BRANCH}
```
orexecute this snippet before running your `mvn` command
```shell
export VERSIONING_GIT_TAG=$CI_COMMIT_TAG;
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

#### Debug

`mvn help:evaluate -Dexpression=project.version -Dorg.slf4j.simpleLogger.log.me.qoomon.maven.gitversioning=debug`
