
# Testing notes

```
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyAbstractCause;
import org.jenkinsci.plugins.pipeline.maven.service.ServiceLoggerImpl;

     GlobalPipelineMavenConfig.get().getPipelineTriggerService().triggerDownstreamPipelines("com.example", "my-jar",
                null, "0.7-SNAPSHOT", "jar", new MavenDependencyAbstractCause() {
                    @Override
                    public String getShortDescription() {
                        return "MavenDependencyAbstractCause[" + getMavenArtifactsDescription() + "]";
                    }
                }, new ServiceLoggerImpl(System.out, System.err, "withMaven - "));
```
