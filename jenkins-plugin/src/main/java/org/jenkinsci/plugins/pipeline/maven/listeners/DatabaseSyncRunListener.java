package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

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
}
