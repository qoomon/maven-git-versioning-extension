# jgitver-maven-plugin

## Example

```
rm -rf /d/demo-jgitver-maven-plugin
mkdir /d/demo-jgitver-maven-plugin
cd /d/demo-jgitver-maven-plugin
git init
cat > pom.xml << EOF
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>fr.brouillard.oss.demo</groupId>
    <artifactId>demo-demo-jgitver-maven-plugin</artifactId>
    <version>0</version>
    <packaging>pom</packaging>
    <build>
        <plugins>
            <plugin>
                <groupId>fr.brouillard.oss</groupId>
                <artifactId>jgitver-maven-plugin</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>
</project>
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
```