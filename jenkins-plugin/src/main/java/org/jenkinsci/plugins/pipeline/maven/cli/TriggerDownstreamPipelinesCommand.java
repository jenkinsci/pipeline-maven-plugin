package org.jenkinsci.plugins.pipeline.maven.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyCliCause;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.jenkinsci.plugins.pipeline.maven.service.ServiceLoggerImpl;
import org.kohsuke.args4j.Option;

import java.util.Collection;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class TriggerDownstreamPipelinesCommand extends CLICommand {
    @Option(name = "--groupId", aliases = "-g", usage = "Group ID", required = true)
    public String groupId;
    @Option(name = "--artifactId", aliases = "-a", usage = "Artifact ID", required = true)
    public String artifactId;
    @Option(name = "--version", aliases = "-v", usage = "Artifact version (e.g. '1.0-SNAPSHOT' is just built locally or '1.0-20100529-1213' when a SNAPSHOT artifact is deployed to a Maven repository or '1.0' for a released version", required = true)
    public String version;
    @Option(name = "--base-version", aliases = "-bv", usage = "Artifact base version (e.g. '1.0-SNAPSHOT'). The base version is different from the '--version' that provides the timestamped version number when uploading snapshots to Maven repository")
    public String baseVersion;
    @Option(name = "--type", aliases = "-t", usage = "Artifact type", required = true)
    public String type;

    @Override
    public String getShortDescription() {
        return "Triggers the downstream pipelines of the given Maven artifact based on their Maven dependencies";
    }


    @Override
    protected int run() throws Exception {
        /*
         * @Inject does NOT work to inject GlobalPipelineMavenConfig in the TriggerDownstreamPipelinesCommand instance, use static code :-(
         */
        PipelineTriggerService pipelineTriggerService = GlobalPipelineMavenConfig.get().getPipelineTriggerService();

        MavenDependencyCliCause cause = new MavenDependencyCliCause(Jenkins.getAuthentication().getName());
        Collection<String> triggeredPipelines = pipelineTriggerService.triggerDownstreamPipelines(groupId, artifactId, baseVersion, version, type, cause, new ServiceLoggerImpl(this.stdout, this.stderr, null));
        stdout.println(triggeredPipelines);
        return 0;
    }
}
