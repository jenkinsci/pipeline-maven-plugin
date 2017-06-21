package org.jenkinsci.plugins.pipeline.maven.trigger;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WorkflowJobDependencyTrigger extends Trigger<WorkflowJob> {


    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        @Override
        public boolean isApplicable(Item item) {
            return item instanceof WorkflowJob;
        }

        public String getDisplayName() {
            return "Pipeline Dependency Trigger";
        }

    }
}
