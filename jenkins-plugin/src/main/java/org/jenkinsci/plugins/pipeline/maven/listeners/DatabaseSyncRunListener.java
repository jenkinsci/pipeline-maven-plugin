package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class DatabaseSyncRunListener extends RunListener<WorkflowRun> {

    @Inject
    public GlobalPipelineMavenConfig globalPipelineMavenConfig;

    @Override
    public void onDeleted(WorkflowRun run) {
        globalPipelineMavenConfig.getDao().deleteBuild(run.getParent().getFullName(), run.getNumber());
    }

    @Override
    public void onInitialize(WorkflowRun run) {
        super.onInitialize(run);

        for (Cause cause: run.getCauses()) {
            if (cause instanceof Cause.UpstreamCause) {
                Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;

                String upstreamJobName = upstreamCause.getUpstreamProject();
                int upstreamBuildNumber = upstreamCause.getUpstreamBuild();
                globalPipelineMavenConfig.getDao().recordBuildUpstreamCause(upstreamJobName, upstreamBuildNumber, run.getParent().getFullName(), run.getNumber());
            }
        }

    }

    /*
     * TODO ensure that this code executes before DownstreamPipelineTriggerRunListener#onCompleted
     */
    @Override
    public void onCompleted(WorkflowRun workflowRun, @Nonnull TaskListener listener) {
        super.onCompleted(workflowRun, listener);

        // Note: run.duration is zero in onCompleted(), do the substraction in this listener
        Result result = workflowRun.getResult();
        if (result == null) {
            result = Result.SUCCESS; // FIXME more elegant handling
        }
        globalPipelineMavenConfig.getDao().updateBuildOnCompletion(
                workflowRun.getParent().getFullName(),
                workflowRun.getNumber(),
                result.ordinal,
                workflowRun.getStartTimeInMillis(),
                 Math.max(System.currentTimeMillis() - workflowRun.getStartTimeInMillis(), 0)); // @see HUDSON-5844
    }
}
