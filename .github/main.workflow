workflow "New workflow" {
  on = "push"
  resolves = ["GitHub Action for Maven"]
}

action "GitHub Action for Maven" {
  uses = "docker://maven"
  args = "mvn clean install -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
}
