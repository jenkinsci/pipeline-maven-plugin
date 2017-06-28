package org.jenkinsci.plugins.pipeline.maven.listeners;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.triggers.Messages;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Trigger downstream pipelines.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class DownstreamPipelineTriggerRunListener extends RunListener<WorkflowRun> {

    private final static Logger LOGGER = Logger.getLogger(DownstreamPipelineTriggerRunListener.class.getName());

    @Override
    public void onCompleted(WorkflowRun upstreamBuild, @Nonnull TaskListener listener) {
        LOGGER.log(Level.FINE, "onCompleted({0})", new Object[]{upstreamBuild});

        if (!GlobalPipelineMavenConfig.getTriggerDownstreamBuildsCriteria().contains(upstreamBuild.getResult())) {
            LOGGER.log(Level.FINE, "Skip downstream job triggering for upstream build with ignored result status {0}: {1}",
                    new Object[]{upstreamBuild, upstreamBuild.getResult()});
            return;
        }

        WorkflowJob upstreamPipeline = upstreamBuild.getParent();
        List<String> downstreamPipelines = GlobalPipelineMavenConfig.getDao().listDownstreamJobs(upstreamPipeline.getFullName(), upstreamBuild.getNumber());

        for (String downstreamPipelineFullName : downstreamPipelines) {
            final WorkflowJob downstreamPipeline = Jenkins.getInstance().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
            if (downstreamPipeline == null) {
                LOGGER.log(Level.FINE, "Downstream pipeline {0} not found from upstream build {1}", new Object[]{downstreamPipelineFullName, upstreamBuild});
                // job not found
                continue;
            }
            if (downstreamPipeline.equals(upstreamPipeline)) {
                // don't trigger myself
                continue;
            }

            if (isParameterizedPipeline(downstreamPipeline)) {
                LOGGER.log(Level.FINE, "Skip triggering of parameterized downstream pipeline {0} from upstream build (1}", new Object[]{downstreamPipeline, upstreamBuild});
                continue;
            }

            WorkflowJobDependencyTrigger downstreamPipelineTrigger = getWorkflowJobDependencyTrigger(downstreamPipeline);
            if (downstreamPipelineTrigger == null) {
                LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0} from upstream build {1}: dependency trigger not configured", new Object[]{downstreamPipeline, upstreamBuild});
                continue;
            }

            LOGGER.log(Level.FINE, "Triggering downstream pipeline {0} from upstream build {1}", new Object[]{downstreamPipeline, upstreamBuild});
            if (isDownstreamPipelineVisibleByUpstreamPipeline(upstreamPipeline, downstreamPipeline, listener)) {
                listener.getLogger().println("[withMaven] Scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamPipeline));
            } else {
                // downstream job not visible from upstream job, don't display message
            }

            // see https://github.com/jenkinsci/pipeline-build-step-plugin/blob/master/src/main/java/org/jenkinsci/plugins/workflow/support/steps/build/BuildTriggerStepExecution.java#L60
            // FIXME don't display upstream pipeline as the cause if permissions don't match
            downstreamPipeline.scheduleBuild(new Cause.UpstreamCause(upstreamBuild));
        }

    }

    protected boolean isParameterizedPipeline(@Nonnull WorkflowJob job) {
        ParametersDefinitionProperty pdp = job.getProperty(ParametersDefinitionProperty.class);
        if (pdp == null) {
            return false;
        } else if (pdp.getParameterDefinitionNames().isEmpty()) {
            return false;
        }
        return true;
    }

    @Nullable
    protected WorkflowJobDependencyTrigger getWorkflowJobDependencyTrigger(@Nonnull ParameterizedJobMixIn.ParameterizedJob parameterizedJob) {
        Map<TriggerDescriptor, Trigger<?>> triggers = parameterizedJob.getTriggers();
        for (Trigger trigger : triggers.values()) {
            if (trigger instanceof WorkflowJobDependencyTrigger) {
                return (WorkflowJobDependencyTrigger) trigger;
            }
        }

        if (parameterizedJob.getParent() instanceof WorkflowMultiBranchProject) {
            // search for the triggers of MultiBranch pipelines
            WorkflowMultiBranchProject multiBranchProject = (WorkflowMultiBranchProject) parameterizedJob.getParent();
            for (Trigger trigger : multiBranchProject.getTriggers().values()) {
                if (trigger instanceof WorkflowJobDependencyTrigger) {
                    return (WorkflowJobDependencyTrigger) trigger;
                }
            }

            if (multiBranchProject.getParent() instanceof ComputedFolder) {
                // search for the triggers of GitHubOrg folders / Bitbucket folders
                ComputedFolder grandParent = (ComputedFolder) multiBranchProject.getParent();
                Map<TriggerDescriptor,Trigger<?>> grandParentTriggers = grandParent.getTriggers();
                for(Trigger trigger : grandParentTriggers.values()){
                    if (trigger instanceof WorkflowJobDependencyTrigger) {
                        return (WorkflowJobDependencyTrigger) trigger;
                    }
                }
            }

        }
        return null;
    }

    protected boolean isDownstreamPipelineVisibleByUpstreamPipeline(@Nonnull WorkflowJob upstreamPipeline, @Nonnull Item downstreamPipeline, TaskListener listener) {
        // TODO see jenkins.triggers.ReverseBuildTrigger.shouldTrigger()

        Jenkins jenkins = Jenkins.getInstance();

        // This checks Item.READ also on parent folders; note we are checking as the upstream auth currently:
        boolean downstreamVisible = jenkins.getItemByFullName(downstreamPipeline.getFullName()) == downstreamPipeline;
        Authentication originalAuth = Jenkins.getAuthentication();
        Authentication auth = Tasks.getAuthenticationOf((Queue.Task) downstreamPipeline);
        if (auth.equals(ACL.SYSTEM) && !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
            auth = Jenkins.ANONYMOUS; // cf. BuildTrigger
        }
        SecurityContext orig = ACL.impersonate(auth);
        try {
            WorkflowJob upstreamPipelineObtainedAsImpersonated = jenkins.getItemByFullName(upstreamPipeline.getFullName(), WorkflowJob.class);

            if (upstreamPipelineObtainedAsImpersonated != upstreamPipeline) { // shouldn't it be a check on upstreamPipelineObtainedAsImpersonated == null
                if (downstreamVisible) {
                    // TODO ModelHyperlink
                    listener.getLogger().println(Messages.ReverseBuildTrigger_running_as_cannot_even_see_for_trigger_f(auth.getName(), upstreamPipeline.getFullName(), downstreamPipeline.getFullName()));
                } else {
                    LOGGER.log(Level.WARNING, "Running as {0} cannot even see {1} for trigger from {2} (but cannot tell {3} that)", new Object[]{auth.getName(), upstreamPipeline, downstreamPipeline, originalAuth.getName()});
                }
                return false;
            }

            // No need to check Item.BUILD on downstream, because the downstream project’s configurer has asked for this.
            return true;
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

}
