# Branch Versioning Extension
This extension will simulate project versions, based on current git branch.
The pom files will not be modified, versions are modified in memory only.

## Install 

### add core extension
create or update ${basedir}/.mvn/extensions.xml
``` xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
    ...
    <extension>
        <groupId>com.qoomon</groupId>
        <artifactId>maven-branch-versioning-extension</artifactId>
        <version>2.3.0</version>
    </extension>
    ...
</extensions>
```

### configure
create ${basedir}/.mvn/maven-branch-versioning-extension.xml
``` xml
<configuration>
    <branches>
        <branch>
            <pattern>^master$</pattern>
            <versionFormat>${pomReleaseVersion}</versionFormat>
        </branch>
        ...
    </branches>
</configuration>
```
#### branch pattern
The branch pattern is an arbitrary regex.

#### version format
The version format is an arbitrary string.

**Available Placeholders**
- ${commitHash}
 - e.g. '0fc20459a8eceb2c4abb9bf0af45a6e8af17b94b'
- ${branchName}
 - e.g. 'master', 'feature/next-big-thing', ...
- ${pomVersion}
 - e.g. '1.2.3-SNAPSHOT'
- ${pomReleaseVersion}
 - e.g. '1.2.3'
