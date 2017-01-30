[Maven Spy](http://maven.apache.org/components/ref/3.3.9/maven-core/apidocs/org/apache/maven/eventspy/EventSpy.html) 
injected byt the Jenkins `withMaven(){...}` pipeline step to capture the execution details of the maven builds.

Maven Spy can be tested using the `-Dmaven.ext.class.path` parameter:

```
mvn -Dmaven.ext.class.path=path/to/pipeline-maven-spy-ccc.jar clean package
```

