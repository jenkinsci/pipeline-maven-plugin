[Maven Spy](http://maven.apache.org/components/ref/3.3.9/maven-core/apidocs/org/apache/maven/eventspy/EventSpy.html) 
injected byt the Jenkins `withMaven(){...}` pipeline step to capture the execution details of the maven builds.


## How to test this Maven Event Spy

Maven Spy can be tested 

* Either using the `-Dmaven.ext.class.path` parameter:

  ````
mvn -Dmaven.ext.class.path=path/to/pipeline-maven-spy-xyz.jar clean package
```

* Or copying `pipeline-maven-spy-xyz.jar ` under `${MAVEN_HOME}/lib/ext

## Flag to disable the Maven Event Spy:

* Environment variable “`JENKINS_MAVEN_AGENT_DISABLED=true`”
* System property “`-Dorg.jenkinsci.plugins.pipeline.maven.eventspy.JenkinsMavenEventSpy.disabled=true`”