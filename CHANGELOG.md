# Maven Git Versioning Extension Changelog

[![Maven Central](https://img.shields.io/maven-central/v/me.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22me.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22)

# Changelog

## 6.0.0
* Major refactoring
* **New Feature**
  * Project `<Dependency>` and `<Plugin>` versions will be updated accordingly to git versions
  * Add config option `<disable>true</disable>` to disable extension by default.
  * Add format placeholder:
    * `${commit.timestamp.year}`
    * `${commit.timestamp.month}`
    * `${commit.timestamp.day}`
    * `${commit.timestamp.hour}`
    * `${commit.timestamp.minute}`
    * `${commit.timestamp.second}`
    * Maven CLI properties e.g. `mvn ... -Dfoo=bar` will result in a `${foo}` placeholder
    

* **BREAKING CHANGES**
  * default version format on a branch changed to `${branch}-SNAPSHOT` was `${commit}`
  * Removed support for project property `versioning.disable` to disable extension by default, use config option `<disable>` instead.
  * Replace property regex pattern match with simple name match
    * old regex pattern config `<branch|tag|commit> <property> <pattern>` 
    * new property name config `<branch|tag|commi> <property> <name>`
  * Remove property value pattern `<branch|tag|commit> <property> <valuePattern>`
  * Remove format placeholder `${property.name}`
  * Rename format placeholder `${property.value}` to just `${value}`


## 5.3.0
* Add feature to disable extension by default and enable on demand

## 5.2.1
* ⚠️ minimal required maven version is now `3.6.3`
* remove workaround for maven `3.6.2` compatibility

## 5.2.0
* **BREAKING** minimal required maven version set to `3.6.3`
* new version format placeholder `${ref.slug}` alike `${ref}` with all `/` replaced by `-`
* new property `git.ref.slug`  alike `git.ref` with all `/` replaced by `-`

## 5.1.0
* ⚠️ accidentally bump minimal required maven version to `3.6.3`
* prevent maven from failing, if project is not part of a git repository. Instead a warning is logged.

## 5.0.2
* fix incompatibility with maven version `3.6.2`

## 5.0.0
#### Features
* simplify `<property>` replacement configuration
#### Fixes
* add missing dependency vor maven version 3.3
#### Breaking Changes
* simplify `<property>` replacement configuration
    
    new config
    ```xml
    <gitVersioning>
        <branch>
            <pattern>master</pattern>
            <versionFormat>${version}</versionFormat>
            <property>
                <pattern>revision</pattern>
                <valueFormat>${branch-SNAPSHOT}</valueFormat>
            </property>
        </branch>
    </gitVersioning>
    ```
    old config
    ```xml
    <gitVersioning>
        <branch>
            <pattern>master</pattern>
            <versionFormat>${version}</versionFormat>
            <property>
                <pattern>revision</pattern>
                <value>
                    <format>${branch-SNAPSHOT}</format>
                </value>
            </property>
        </branch>
    </gitVersioning>
    ```

## 4.10.2
* fix verbose logging when disabling extension by flag
* restrict project versioning to root- and sub-projects

## 4.10.0
* provide `${git.dirty}` project property
   
## 4.8.0
* set execution phase to INITIALIZE
  * Fix IntelliJ multi-modules project handling.

## 4.7.0
* New Provided properties, see [Provided Project Properties](#provided-roject-roperties)
  * `git.commit.timestamp`
  * `git.commit.timestamp.datetime`

## 4.5.0
* Add parameters and environment variable to disable extension. see [Parameters & Environment Variables](#parameters--environment-variables)

## 4.1.0
* Add config option(`<update>`) to update version in original pom file. see [Configure Extension](#configure-extension)

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
* **Removed Maven Parameters**
  * `-DgitVersioning` - disable the extension by a parameter is no longer supported
* **Renamed Provided Project Properties**
  * `project.branch` -> `git.branch`
  * `project.tag` -> `git.tag`
  * `project.commit` -> `git.commit`

