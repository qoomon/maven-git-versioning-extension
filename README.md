# jgitver-maven-plugin 

[![Build Status](https://travis-ci.org/jgitver/jgitver-maven-plugin.svg?branch=master)](https://travis-ci.org/jgitver/jgitver-maven-plugin)
[![CircleCI Build Status](https://circleci.com/gh/jgitver/jgitver-maven-plugin.svg?style=shield&circle-token=9c75a2389afd0bd18f508ce30bceb3e5728b4ce8)](https://circleci.com/gh/jgitver/jgitver-maven-plugin)


> *__IntelliJ IDEA users__*: due to [IDEA-155733](https://youtrack.jetbrains.com/issue/IDEA-155733) update your IDE to version >= `2016.1.3` and use jgtiver-maven-plugin >= `0.1.1`  
>  
> If you cannot update due to old perpetual licenses in use for example, as a workaround, you can move the plugin into a profile as described [here](#maven-dependency-management-is-broken-under-intellij-idea).

This plugin allows to define the pom version of your project using the information from your git history. 
It calculates the version, a little bit like `git describe` would do but in a more efficient way for maven projects:

- new commits have upper version than previous commit (in the way maven/semver interpret versions)
- version calculation is based on git tags
- git lightweight tags allow for intermediate version controlling between releases
    - allow to define what is the _next_ version pattern to use
- minimal setup via maven extension

Here is an illustration of the capabilities of the plugin

![Example](src/doc/images/jgitver-maven-like.gif?raw=true "Example")

## Usage

### Activation by maven core extension

Since version `0.3.0` [jgitver-maven-plugin](#jgitver-maven-plugin) needs to be run as a maven core extension

1. Create a directory `.mvn` under the root directory of your project.
1. Create file `.mvn/extensions.xml`
1. Put the following content to `.mvn/extensions.xml` (adapt the version).

    ```
    <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
      <extension>
        <groupId>fr.brouillard.oss</groupId>
        <artifactId>jgitver-maven-plugin</artifactId>
        <version>0.3.0-alpha2</version>
      </extension>
    </extensions>
    ```
1. Remove the old declarations/usages of [jgitver-maven-plugin](#jgitver-maven-plugin) from the project pom

### Configuration

In order to control [jgitver-maven-plugin](#jgitver-maven-plugin) behavior, you can provide a configuration 
file under `$rootProjectDir/.mvn/jgitver.config.xml` having the following format:

```
<configuration>
    <mavenLike>true/false</mavenLike>
    <autoIncrementPatch>true/false</autoIncrementPatch>
    <useCommitDistance>true/false</useCommitDistance>
    <useDirty>true/false</useDirty>
    <useGitCommitId>true/false</useGitCommitId>
    <gitCommitIdLength>integer</gitCommitIdLength>  <!-- between [8,40] -->
    <nonQualifierBranches>master</nonQualifierBranches> <!-- comma separated, example "master,integration" -->
</configuration>
```

Please consult [jgitver](https://github.com/jgitver/jgitver#configuration-modes--strategies) documentation to fully understand what the parameters do.

**Known issues**
1. Due to a Jenkins/Maven incompatibility feature might not work correctly due to Jenkins issue <a href="https://issues.jenkins-ci.org/browse/JENKINS-30058?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20maven-plugin%20AND%20text%20~%20%22extensions%22">JENKINS-30058</a>

### Available properties

Since `0.2.0`, the plugin exposes git calculated properties available during the maven build.
Those are available under the following properties name: "jgitver.meta" where `meta` is one of [Metadatas](https://github.com/jgitver/jgitver/blob/0.2.0-alpha1/src/main/java/fr/brouillard/oss/jgitver/metadata/Metadatas.java#L25) name in lowercase.

You can then use them as standard maven properties in your build:

```
<plugin>
    <artifactId>maven-antrun-plugin</artifactId>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>run</goal>
            </goals>
            <configuration>
                <tasks>
                    <echo>dirty: ${jgitver.dirty}</echo>
                    <echo>head_committer_name: ${jgitver.head_committer_name}</echo>
                    <echo>head_commiter_email: ${jgitver.head_commiter_email}</echo>
                    <echo>head_commit_datetime: ${jgitver.head_commit_datetime}</echo>
                    <echo>git_sha1_full: ${jgitver.git_sha1_full}</echo>
                    <echo>git_sha1_8: ${jgitver.git_sha1_8}</echo>
                    <echo>branch_name: ${jgitver.branch_name}</echo>
                    <echo>head_tags: ${jgitver.head_tags}</echo>
                    <echo>head_annotated_tags: ${jgitver.head_annotated_tags}</echo>
                    <echo>head_lightweight_tags: ${jgitver.head_lightweight_tags}</echo>
                    <echo>base_tag: ${jgitver.base_tag}</echo>
                    <echo>all_tags: ${jgitver.all_tags}</echo>
                    <echo>all_annotated_tags: ${jgitver.all_annotated_tags}</echo>
                    <echo>all_lightweight_tags: ${jgitver.all_lightweight_tags}</echo>
                    <echo>all_version_tags: ${jgitver.all_version_tags}</echo>
                    <echo>all_version_annotated_tags: ${jgitver.all_version_annotated_tags}</echo>
                    <echo>all_version_lightweight_tags: ${jgitver.all_version_lightweight_tags}</echo>
                </tasks>
            </configuration>
        </execution>
    </executions>
</plugin>
```

resulted in my case 

```
[INFO] Executing tasks
     [echo] dirty: true
     [echo] head_committer_name: Matthieu Brouillard
     [echo] head_commiter_email: matthieu@brouillard.fr
     [echo] head_commit_datetime: Thu Jun 30 14:06:14 2016 +0200
     [echo] git_sha1_full: fadd88e04b25c794cea876b03d8234df5bf4e37b
     [echo] git_sha1_8: fadd88e0
     [echo] branch_name: master
     [echo] head_tags:
     [echo] head_annotated_tags:
     [echo] head_lightweight_tags:
     [echo] base_tag: v0.2.0
     [echo] all_tags: v0.2.0,0.1.1,0.1.0,0.0.3,0.0.2,0.0.1
     [echo] all_annotated_tags: 0.1.1,0.1.0,0.0.3,0.0.2,0.0.1
     [echo] all_lightweight_tags: v0.2.0
     [echo] all_version_tags: v0.2.0,0.1.1,0.1.0,0.0.3,0.0.2,0.0.1
     [echo] all_version_annotated_tags: 0.1.1,0.1.0,0.0.3,0.0.2,0.0.1
     [echo] all_version_lightweight_tags: v0.2.0
[INFO] Executed tasks
```

## Example

If you want to give it a try you can use the following script that will setup a demo project. 

Then play around with it doing:

- `mvn validate`
- `mvn install`
- `git checkout XXXX`

```
cd /d
rm -rf /d/demo-jgitver-maven-plugin
mkdir -p /d/demo-jgitver-maven-plugin
cd /d/demo-jgitver-maven-plugin
git init
mkdir .mvn
cat > .mvn/extensions.xml << EOF
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>fr.brouillard.oss</groupId>
    <artifactId>jgitver-maven-plugin</artifactId>
    <version>[0.3.0-SNAPSHOT,)</version>
  </extension>
</extensions>
EOF
echo A > content
git add pom.xml
git add content
git commit -m "initial commit"
echo B > content && git add -u && git commit -m "added B data"
git tag 1.0 -m "release 1.0"
echo C > content && git add -u && git commit -m "added C data"
git checkout -b cool-feature
echo D > content && git add -u && git commit -m "added D data"
git checkout master
echo E > content && git add -u && git commit -m "added E data"
mvn validate
```

## Requirements

### Maven requirements

[jgitver-maven-plugin](#jgitver-maven-plugin) requires at least maven-3.3.x to work correctly.

Think to modify your IDE settings regarding maven version ; if required do not use  the embedded maven version of your IDE but an external one that fulfill the maven minimal requirements.  

### Supported IDEs

- Eclipse: tested with Eclipse Mars.2 Release 4.5.2
- Netbeans: tested with NetBeans IDE 8.1 Build 201510222201
- Intellij IDEA: tested with 2016.1.3, see [known issues](#known-issues) for lower versions

## Build & release

### Normal build

- `mvn -Prun-its clean install`

or using docker

- `docker run -v $(pwd):/root/sources -w /root/sources maven:3.3.9-jdk-8 mvn -Prun-its clean verify` 


### Release

- `mvn -Poss clean install`: this will simulate a full build for oss delivery (javadoc, source attachement, GPG signature, ...)
- `git tag -a -s -m "release X.Y.Z, additionnal reason" X.Y.Z`: tag the current HEAD with the given tag name. The tag is signed by the author of the release. Adapt with gpg key of maintainer.
    - Matthieu Brouillard command:  `git tag -a -s -u 2AB5F258 -m "release X.Y.Z, additionnal reason" X.Y.Z`
    - Matthieu Brouillard [public key](https://sks-keyservers.net/pks/lookup?op=get&search=0x8139E8632AB5F258)
- `mvn -Poss,release -DskipTests deploy`
- `git push --follow-tags origin master`

> DISCLAIMER  
> This plugin has been highly inspired by the work of [Brian Demers](https://github.com/bdemers) in his [maven-external-version](https://github.com/bdemers/maven-external-version/) plugin.  
> I rewrote such a plugin mainly to simplify usage compared to a [maven-external-version](https://github.com/bdemers/maven-external-version/) extension (which I wrote also as [maven-external-version-jgitver](https://github.com/jgitver/maven-external-version-jgitver)).  
> Such a simplification leads to:
> - usage as pure extension without configuration 
> - benefit from a direct configuration on the plugin allowing for example IDE completion & -D property usage 

## Known issues

### Maven dependency management is broken under Intellij IDEA

> TODO verify with jgitver-maven-plugin >= 0.3.0

due to [IDEA-155733](https://youtrack.jetbrains.com/issue/IDEA-155733), Intellij IDEA versions lower than `2016.1.3` _(this includes of course all 14.X & 15.X versions)_ do not handle correctly the plugin. This results in having the IDEA-maven integration being broken:
- no update of dependencies
- other issues, ...
 
If you can, upgrade to Intellij IDEA >= `2016.1.3`, [IDEA-155733](https://youtrack.jetbrains.com/issue/IDEA-155733) has been resolved in this version.

If you cannot upgrade, the solution is to disable the plugin usage in Intellij by moving it inside a maven profile. Then you have the choice to either deactivate the profile in Intellij or have the profile deactivated by default (thus not working in Intellij) and manually activating it on your CI/build system.

Here is an example configuration that deactivates the plugin:
```
...
<profiles>
    ...
    <profile>
        <id>jgitver</id>
        <activation>
            <activeByDefault>false</activeByDefault>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>fr.brouillard.oss</groupId>
                    <artifactId>jgitver-maven-plugin</artifactId>
                    <version>0.1.1</version>
                    <extensions>true</extensions>
                </plugin>
            </plugins>
        </build>
    </profile>
    ...
</profiles>
```

Then on your build system, you would just have to activate the profile to have the magic happen again:  
`mvn install -Pjgitver`
