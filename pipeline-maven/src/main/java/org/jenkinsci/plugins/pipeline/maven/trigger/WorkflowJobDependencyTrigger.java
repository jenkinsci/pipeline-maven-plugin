package org.jenkinsci.plugins.pipeline.maven.trigger;

import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.workflow.flow.BlockableResume;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WorkflowJobDependencyTrigger extends Trigger<Item> {

    @DataBoundConstructor
    public WorkflowJobDependencyTrigger(){

    }

    @Symbol("snapshotDependencies")
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        @Override
        public boolean isApplicable(Item item) {
            return item instanceof BlockableResume || item instanceof MultiBranchProject || item instanceof OrganizationFolder;
        }

        public String getDisplayName() {
            return Messages.trigger_workflow_job_dependency_description();
        }

    }
}
