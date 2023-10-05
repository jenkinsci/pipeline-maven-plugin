package org.jenkinsci.plugins.pipeline.maven.listeners;

import static java.util.Optional.ofNullable;
import static java.util.stream.StreamSupport.stream;
import static org.jenkinsci.plugins.pipeline.maven.WithMavenStep.DescriptorImpl.FUNCTION_NAME;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

public abstract class AbstractWorkflowRunListener extends RunListener<Run<?, ?>> {

    protected boolean shouldRun(Run<?, ?> run, TaskListener listener) {
        if (!(run instanceof FlowExecutionOwner.Executable)) {
            return false;
        }

        return ofNullable(((FlowExecutionOwner.Executable) run).asFlowExecutionOwner())
                .map(owner -> {
                    try {
                        return owner.get();
                    } catch (Exception ex) {
                        listener.getLogger()
                                .println(
                                        "[withMaven] downstreamPipelineTriggerRunListener - Failure to introspect build steps: "
                                                + ex.toString());
                        return null;
                    }
                })
                .map(execution -> {
                    DepthFirstScanner scanner = new DepthFirstScanner();
                    return scanner.setup(execution.getCurrentHeads()) ? scanner : null;
                })
                .map(scanner -> scanner.spliterator())
                .map(iterator -> stream(iterator, false))
                .flatMap(stream -> stream.filter(n -> FUNCTION_NAME.equals(n.getDisplayFunctionName()))
                        .findAny())
                .isPresent();
    }
}
