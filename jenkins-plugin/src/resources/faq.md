

# How to use the Pipeline Maven Plugin with Docker? (since version 3.0.3)

To use `withMaven(){...}` step running within a container you MUST since the Docker Pipeline Plugin version 1.14:

* Either use [Takari's Maven Wrapper](https://github.com/takari/maven-wrapper) (e.g. `sh './mvnw clean deploy'`");
* Or prepend the `MVN_CMD_DIR` environment variable to the `PATH` environment variable in every `sh` step that invokes `mvn` (e.g. `sh 'export PATH=$MVN_CMD_DIR:$PATH && mvn clean deploy'`). ");
* Or use `MVN_CMD` instead of invoking `mvn` (e.g. `sh '$MVN_CMD clean deploy'`).

If omitted, the Maven settings file and Mvaen global settings file will not be injected in the Maven execution.


## Using "withMaven" with "docker.image(...).inside{...}" and a Jenkins Scripted Pipeline 

Prepending `MVN_CMD_DIR` to `PATH`

```groovy
node("linux-agent-running-docker") { // Linux agent with the Docker daemon
    docker.image('maven').inside { // Docker image with Maven installed
        withMaven(...) {
            git "https://github.com/cyrille-leclerc/my-jar.git"
            sh "export PATH=$MVN_CMD_DIR:$PATH && mvn clean deploy" // 'mvn' command: need to add the $MVN_CMD_DIR to $PATH
        }
    }
}
```

## Using Takari's Maven Wrapper mvnw


```groovy
node("linux-agent-running-docker") { // Linux agent with the Docker daemon
    docker.image('openjdk:8-jdk').inside { // Docker image with Java installed
        withMaven(...) {
            git "https://github.com/cyrille-leclerc/my-jar.git"
            sh "./mvnw clean deploy" // 'mvnw' command (e.g. "./mvnw deploy")
        }
    }
}
```
