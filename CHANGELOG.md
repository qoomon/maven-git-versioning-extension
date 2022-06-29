# Maven Git Versioning Extension Changelog

[![Maven Central](https://img.shields.io/maven-central/v/me.qoomon/maven-git-versioning-extension.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22me.qoomon%22%20AND%20a%3A%22maven-git-versioning-extension%22)

# Changelog

## 9.1.0

##### Features
- add config option for `<projectVersionPattern>` to use special parts of the project version as placeholders e.g. `${version.environment}`
- add placeholder `${version.core}` the core version component of `${version}` e.g. '1.2.3'
  - `${version.release}` is marked as deprecated


## 9.0.1

##### Fixes
- handle lightweight tags
- set `${version.minor.next}` placeholders properly


## 9.0.0 (accidentally increased major version, D'OH )
##### Fixes
* fix git describe tag selection, if multiple tags point to head
* add missing `${version.label.prefixed}` placeholder


## 8.0.1
##### Fixes
* handle multiline config elements in `maven-git-versioning-extension.xml`


## 8.0.0
##### Features
- migrate to java 11
- placeholder
  - new
    - `${version.major.next}`
    - `${version.minor.next}`
    - `${version.patch.next}`
    - `${describe.tag.version}`
    - `${describe.tag.version.major}`
    - `${describe.tag.version.major.next}`
    - `${describe.tag.version.minor}`
    - `${describe.tag.version.minor.next}`
    - `${describe.tag.version.path}`
    - `${describe.tag.version.patch.next}`
  - removed
    - `${version.minor.prefixed}`
    - `${version.patch.prefixed}`

##### BREAKING CHANGES
- drop support for java 8

## 7.4.0

##### Features
* add additional version component placeholders (#182)
- `${version.minor.prefixed}` like `${version.minor}` with version component separator e.g. '.2'
- `${version.patch.prefixed}` like `${version.patch}`  with version component separator e.g. '.3'
- `${version.label}` the version label of `${version}` e.g. 'SNAPSHOT'
  - `${version.label.prefixed}` like `${version.label}` with label separator e.g. '-SNAPSHOT'

##### BREAKING CHANGES
- `${version.release}` will remove all version labels instead of just the `-SNAPSHOT` label

## 7.3.0

##### Features
* add additional version component placeholders (#165 @pdkyas)

## 7.2.3

##### Fixes
* fix worktree handling

## 7.2.0

##### Features
* Add `<relatedProjects>` config option

## 7.1.3

##### Fixes
* fix `rootDirectory` determination for sub working trees

## 7.1.2

##### Fixes
* proper handle of concurrent module builds

## 7.1.1

##### Fixes
* if a tag is provided (and no branch) the extension behaves like in detached head state
* if a branch is provided (and no tag) the extension behaves like in attached head state with no tags pointing to head

## 7.1.0

##### Features
* New Placeholder `${commit.timestamp.year.2digit}`


## 7.0.0

##### Features
* Add GitHub Actions, GitLab CI and Jenkins environment variable support
    * GitHub Actions: if `$GITHUB_ACTIONS == true`, `GITHUB_REF` is considered
    * GitLab CI: if `$GITLAB_CI == true`, `CI_COMMIT_BRANCH` and `CI_COMMIT_TAG` are considered
    * Circle CI: if `$CIRCLECI == true`, `CIRCLE_BRANCH` and `CIRCLE_TAG` are considered
    * Jenkins: if `JENKINS_HOME` is set, `BRANCH_NAME` and `TAG_NAME` are considered
* Simplify xml configuration (also see BREAKING CHANGES)

    **Example:** `maven-git-versioning-extension.xml`
    ```xml
    <configuration xmlns="https://github.com/qoomon/maven-git-versioning-extension" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://github.com/qoomon/maven-git-versioning-extension https://qoomon.github.io/maven-git-versioning-extension/configuration-7.0.0.xsd">
    
        <refs>
            <ref type="branch">
                <pattern>.+</pattern>
                <version>${ref}-SNAPSHOT</version>
                <properties>
                    <foo>${ref}</foo>
                </properties>
            </ref>
    
            <ref type="tag">
                <pattern><![CDATA[v(?<version>.*)]]></pattern>
                <version>${ref.version}</version>
            </ref>
        </refs>
    
        <!-- optional fallback configuration in case of no matching ref configuration-->
        <rev>
            <version>${commit}</version>
        </rev>
    
    </configuration>
    ```
* New option to consider tag configs on branches (attached HEAD), enabled by `<refs considerTagsOnBranches="true">`
    * If enabled, first matching branch or tag config will be used for versioning
* prevent unnecessary updates of `pom.xml` to prevent unwanted rebuilds (#129 kudos to @ls-urs-keller)
 
##### BREAKING CHANGES
* There is no default config anymore, if no `<ref>` configuration is matching current git situation and no `<rev>` configuration has been
  defined a warning message will be logged and extension will be skipped.
* Placeholder Changes (old -> new)
    * `${branch}` -> `${ref}`
    * `${tag}` -> `${ref}`
    * `${REF_PATTERN_GROUP}` -> `${ref.REF_PATTERN_GROUP}`
    * `${describe.TAG_PATTERN_GROUP}` -> `${describe.tag.TAG_PATTERN_GROUP}`
* `preferTags` option was removed
    * use `<refs considerTagsOnBranches="true">` instead


## 6.5.0

##### Features
* add git describe version placeholders
    * new placeholders
        * `${describe}`
        * `${describe.tag}`
            * `${describe.<TAG_PATTERN_GROUP_NAME or TAG_PATTERN_GROUP_INDEX>}` e.g. pattern `v(?<version>.*)` will
              create placeholder `${describe.version}`
        * `${describe.distance}`

##### BREAKING CHANGES
* no longer provide project property `git.dirty` due to performance issues on larger projects, version format
  placeholder `${dirty}` is still available


## 6.4.6

##### Fixes
* Fix parent project version handling


## 6.4.1

##### Fixes
* Handle xsi:schemaLocation property in configuration file


## 6.4.0

##### Features
* Improved Logging


## 6.3.0

##### Features
* Add support for environment variables in version formats e.g. `${env.BUILD_NUMBER}`


## 6.2.0

##### Features
* add ability to define default or overwrite values for version and property format.
    * default value if parameter value is not set `${paramter:-<DEFAULT_VALUE>}` e.g. `${buildNumber:-0}`
    * overwrite value if parameter has a value `${paramter:+<OVERWRITE_VALUE>}` e.g. `${dirty:+-SNAPSHOT}`


## 6.1.1

##### Fixes
* fixed wrong dependency management version updates.


## 6.1.0

##### Features
* add `${dirty.snapshot}` placeholder that resolves to `-SNAPSHOT` if repository is in a dirty state.
    * e.g. `<versionFormat>${tag}${dirty.snapshot}</versionFormat>`


## 6.0.6

##### Fixes
* fixed `NullPointerException` when no `<commit>` config tag exists.


## 6.0.5

##### Fixes
* fixed `NullPointerException` when no `versionFormat` is set.


## 6.0.4

##### Fixes
* fixed wrong property replacement.


## 6.0.3

##### Fixes
* fixed `NullPointerException` when no `<plugin><groupId>` is undefined.


## 6.0.2

##### Fixes
* fixed `NullPointerException` caused by accessing wrong element within versioning of `pom.xml` profile plugin
  section


## 6.0.1

##### Fixes
* add padding for `timestamp` related placeholder values.


## 6.0.0

* Major refactoring
##### Features
* Project `<Dependency>` and `<Plugin>` versions will be updated accordingly to git versions
* Add config option `<disable>true</disable>` to disable extension by default.
* Add format placeholder:
    * `${dirty.snapshot}`
    * `${commit.timestamp.year}`
    * `${commit.timestamp.month}`
    * `${commit.timestamp.day}`
    * `${commit.timestamp.hour}`
    * `${commit.timestamp.minute}`
    * `${commit.timestamp.second}`
    * Maven CLI properties e.g. `mvn ... -Dfoo=bar` will be accessible by `${foo}` placeholder
    
##### BREAKING CHANGES
* default version format on a branch changed to `${branch}-SNAPSHOT` was `${commit}`
* Removed support for project property `versioning.disable` to disable extension by default, use config
  option `<disable>` instead.
* Replace property regex pattern match with simple name match
    * old regex pattern config `<branch|tag|commit> <property> <pattern>`
    * new property name config `<branch|tag|commit> <property> <name>`
* Remove property value pattern `<branch|tag|commit> <property> <valuePattern>`
* Remove format placeholder `${property.name}`
* Rename format placeholder `${property.value}` to just `${value}`
* Move temporary git versioned `pom.xml` from build directory next to original `pom.xml` (`.git-versioned-pom.xml`)


## 5.3.0

##### Features
* Add feature to disable extension by default and enable on demand


## 5.2.1

* ⚠️ minimal required maven version is now `3.6.3`
* remove workaround for maven `3.6.2` compatibility


## 5.2.0

##### Features
* new version format placeholder `${ref.slug}` alike `${ref}` with all `/` replaced by `-`
* new property `git.ref.slug`  alike `git.ref` with all `/` replaced by `-`

##### BREAKING CHANGES
* minimal required maven version set to `3.6.3`


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

* Add parameters and environment variable to disable extension.
  see [Parameters & Environment Variables](#parameters--environment-variables)


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

