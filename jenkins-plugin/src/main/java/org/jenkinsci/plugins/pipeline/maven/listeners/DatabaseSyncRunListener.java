package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class DatabaseSyncRunListener extends RunListener<WorkflowRun> {

    @Override
    public void onDeleted(WorkflowRun run) {
        GlobalPipelineMavenConfig.getDao().deleteBuild(run.getParent().getFullName(), run.getNumber());
    }
}
