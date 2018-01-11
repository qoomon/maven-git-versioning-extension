# Maven Git Versioning Extension

[![Maven Central](https://img.shields.io/maven-central/v/com.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22) [![Build Status](https://travis-ci.org/qoomon/maven-git-versioning-extension.svg?branch=master)](https://travis-ci.org/qoomon/maven-git-versioning-extension)

This extension will generate project versions, based on current **GIT branch** or **GIT tag**.

ℹ **The pom files will not be modified, versions are modified in memory only.**

![Example](doc/MavenGitVersioningExtension.png)

## Install

### add core extension

create or update ${basedir}/.mvn/extensions.xml

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.qoomon</groupId>
        <artifactId>maven-git-versioning-extension</artifactId>
        <version>LATEST</version>
    </extension>

</extensions>
```

### Configure

Default Branch Version Format: `${branch}-SNAPSHOT`

For Custom Configuration create `${project.basedir}/.mvn/maven-git-versioning-extension.xml`.

- `<configuration>`

  - `<branches>` Branch specific configurations.

    - `<branch>`

      - `<pattern>` An arbitrary regex to match branch names
      - `<prefix>` Remove prefix from `${branch}` placeholder
      - `<versionFormat>` An arbitrary string, see [Version Format Placeholders](#version-format-placeholders)

  - `<tags>` Tag specific configurations

    - `<tag>`

      - `<pattern>` An arbitrary regex to match tag names
      - `<prefix>` Remove prefix from `${tag}` placeholder
      - `<versionFormat>` An arbitrary string, see [Version Format Placeholders](#version-format-placeholders)

#### Config Example `maven-git-versioning-extension.xml`

```xml
<configuration>
    <branches>
        <branch>
            <pattern>master</pattern>
            <versionFormat>${version.release}</versionFormat>
        </branch>
        <branch>
             <pattern>release/.*</pattern>
             <prefix>release/</prefix>
             <versionFormat>${branch}-SNAPSHOT</versionFormat>
         </branch>
    </branches>
    <tags>
        <tag>
            <pattern>version/.*</pattern>
            <prefix>version/</prefix>
            <versionFormat>${tag}</versionFormat>
        </tag>
    </tags>
</configuration>
```

#### Version Format Placeholders

- `${branch}` (only available in within branch configuration)

  - current branch name
  - '/' characters within branch name will be replaced by '-'
  - e.g. 'master', 'feature-next-big-thing', ...

- `${tag}` (only available in within tag configuration)

  - current tag name, if multiple tags point to current commit tag names are sorted by `org.apache.maven.artifact.versioning.DefaultArtifactVersion` and the last one is selected
  - '/' characters within tag name will be replaced by '-'
  - e.g. 'version/1.0.1', 'version-1.0.0', ...

- `${version}`

  - pom file version
  - e.g. '1.2.3-SNAPSHOT'

- `${version.release}`

  - pom file version without '-SNAPSHOT'
  - e.g. '1.2.3'

- `${commit}`

  - current commit hash
  - e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'

- `${commit.short}`

  - short current commit hash
  - e.g. '0fc2045'

- `${PATTERN_GROUP_NAME or PATTERN_GROUP_INDEX}`

  - Contents of group in the regex pattern can be addressed by group name or group index

    - Group Index
      ```
      <pattern>(feature)/(.*)]]></pattern>
      <versionFormat>${1}-${2}</versionFormat>
      ```
    - Named Group 
      ```
      <pattern><![CDATA[(?<type>[^/]*)/(?<name>.*)]]></pattern>
      <versionformat>${type}-${name}</versionformat>
      ```



### Options

- provide or overwrite branch name

  - `mvn -Dproject.branch=$CUSTOM_BRANCH_NAME ...`
  - `export MAVEN_PROJECT_BRANCH=$CUSTOM_BRANCH_NAME`

- provide or overwrite tag name

  - `mvn -Dproject.tag=$CUSTOM_TAG_NAME ...`
  - `export MAVEN_PROJECT_TAG=$CUSTOM_TAG_NAME`

- disable plugin

  - `mvn -DgitVersioning=false ...`

### Provided Project Properties

- project.branch
- project.tag
- project.commit

### Git Detached Head State

- create a branch before maven execution `git checkout -b $CUSTOM_BRANCH_NAME`
- see [provide branch name](#options)
