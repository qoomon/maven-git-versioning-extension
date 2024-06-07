# maven-git-version-extension example project

This project demonstrates the extension setup in a maven project.

## Extension Configuration

* maven extension configuration: [.mvn/extensions.xml](.mvn/extensions.xml)
* maven-git-versioning-extension configuration: [.mvn/maven-git-versioning-extension.xml](.mvn/maven-git-versioning-extension.xml)

Version Setup:

* branches: &lt;branchName&gt;-SNAPSHOT
* tags: vX.Y.Z -&gt; X.Y.Z

## Writing the version to a file

* for usage in other tools
* using the maven help plugin, see [pom.xml](pom.xml#L41)
* writing the file to: target/project.version 
* Example usage as docker tag:

      $ PROJECT_VERSION=$(cat target/project.version)
      $ docker build -t my-docker-image:${PROJECT_VERSION} .

## Limiting the version length

* branch names could be very long, so the generated will be too
* but for example [k8s label names](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set) are limited to 63 characters
* we use the [pattern regex](.mvn/maven-git-versioning-extension.xml#L8) to cut down the version to this limit
* the version will still end with -SNAPSHOT, but the branch name will be cut down to 53 characters
