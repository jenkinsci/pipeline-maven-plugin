package org.jenkinsci.plugins.pipeline.maven.listeners;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

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
        LOGGER.log(Level.FINER, "onCompleted({0})", new Object[]{upstreamBuild});
        if(LOGGER.isLoggable(Level.FINER)) {
            listener.getLogger().println("[withMaven] pipelineGraphPublisher - triggerDownstreamPipelines");
        }

        if (!GlobalPipelineMavenConfig.getTriggerDownstreamBuildsCriteria().contains(upstreamBuild.getResult())) {
            if (LOGGER.isLoggable(Level.FINER)) {
                listener.getLogger().println("[withMaven] Skip downstream job triggering for upstream build with ignored result status " + upstreamBuild + ": " + upstreamBuild.getResult());
            }
            return;
        }

        WorkflowJob upstreamPipeline = upstreamBuild.getParent();
        List<String> downstreamPipelines = GlobalPipelineMavenConfig.getDao().listDownstreamJobs(upstreamPipeline.getFullName(), upstreamBuild.getNumber());

        for (String downstreamPipelineFullName : downstreamPipelines) {
            final WorkflowJob downstreamPipeline = Jenkins.getInstance().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
            if (downstreamPipeline == null) {
                LOGGER.log(Level.FINE, "Downstream pipeline {0} not found from upstream build {1} with authentication {2}. Database synchronization issue?",
                        new Object[]{downstreamPipelineFullName, upstreamBuild.getFullDisplayName(), Jenkins.getAuthentication()});
                // job not found, the database was probably out of sync
                continue;
            }
            if (downstreamPipeline.equals(upstreamPipeline)) {
                // don't trigger myself
                continue;
            }

            if (!downstreamPipeline.isBuildable()) {
                LOGGER.log(Level.FINE, "Skip triggering of non buildable (disabled: {0}, isHoldOffBuildUntilSave: {1}) downstream pipeline {2} from upstream build {3}",
                        new Object[]{downstreamPipeline.isDisabled(), downstreamPipeline.isHoldOffBuildUntilSave(), downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName()});
                continue;
            }

            WorkflowJobDependencyTrigger downstreamPipelineTrigger = getWorkflowJobDependencyTrigger(downstreamPipeline);
            if (downstreamPipelineTrigger == null) {
                LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0} from upstream build {1}: dependency trigger not configured", new Object[]{downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName()});
                continue;
            }

            boolean downstreamVisibleByUpstreamBuildAuth = isDownstreamVisibleByUpstreamBuildAuth(downstreamPipeline);
            boolean upstreamVisibleByDownstreamBuildAuth = isUpstreamBuildVisibleByDownstreamBuildAuth(upstreamPipeline, downstreamPipeline);

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.log(Level.FINER,
                        "upstreamPipeline (" + upstreamPipeline.getFullName() + ", visibleByDownstreamBuildAuth: " + upstreamVisibleByDownstreamBuildAuth + "), " +
                                " downstreamPipeline (" + downstreamPipeline.getFullName() + ", visibleByUpstreamBuildAuth: " + downstreamVisibleByUpstreamBuildAuth + "), " +
                                "upstreamBuildAuth: " + Jenkins.getAuthentication());
            }
            if (downstreamVisibleByUpstreamBuildAuth && upstreamVisibleByDownstreamBuildAuth) {
                // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
                Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(downstreamPipeline, -1, new CauseAction(new Cause.UpstreamCause(upstreamBuild)));
                if (queuedItem == null) {
                    listener.getLogger().println("[withMaven] Skip scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + ", it is already in the queue.");
                } else {
                    listener.getLogger().println("[withMaven] Scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + "#" + downstreamPipeline.getNextBuildNumber() + "...");
                }
            } else {
                LOGGER.log(Level.FINE, "Skip triggering of {0} by {1}: downstreamVisibleByUpstreamBuildAuth: {2}, upstreamVisibleByDownstreamBuildAuth: {3}",
                        new Object[]{downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName(), downstreamVisibleByUpstreamBuildAuth, upstreamVisibleByDownstreamBuildAuth});
            }
        }

    }

    @Nullable
    protected WorkflowJobDependencyTrigger getWorkflowJobDependencyTrigger(@Nonnull ParameterizedJobMixIn.ParameterizedJob parameterizedJob) {
        Map<TriggerDescriptor, Trigger<?>> triggers = parameterizedJob.getTriggers();
        for (Trigger trigger : triggers.values()) {
            if (trigger instanceof WorkflowJobDependencyTrigger) {
                return (WorkflowJobDependencyTrigger) trigger;
            }
        }

        if (parameterizedJob.getParent() instanceof ComputedFolder) {
            // search for the triggers of MultiBranch pipelines
            ComputedFolder<?> multiBranchProject = (ComputedFolder) parameterizedJob.getParent();
            for (Trigger trigger : multiBranchProject.getTriggers().values()) {
                if (trigger instanceof WorkflowJobDependencyTrigger) {
                    return (WorkflowJobDependencyTrigger) trigger;
                }
            }

            if (multiBranchProject.getParent() instanceof ComputedFolder) {
                // search for the triggers of GitHubOrg folders / Bitbucket folders
                ComputedFolder<?> grandParent = (ComputedFolder) multiBranchProject.getParent();
                Map<TriggerDescriptor, Trigger<?>> grandParentTriggers = grandParent.getTriggers();
                for (Trigger trigger : grandParentTriggers.values()) {
                    if (trigger instanceof WorkflowJobDependencyTrigger) {
                        return (WorkflowJobDependencyTrigger) trigger;
                    }
                }
            }

        }
        return null;
    }


    protected boolean isUpstreamBuildVisibleByDownstreamBuildAuth(@Nonnull WorkflowJob upstreamPipeline, @Nonnull Queue.Task downstreamPipeline) {
        Authentication auth = Tasks.getAuthenticationOf(downstreamPipeline);
        Authentication downstreamPipelineAuth;
        if (auth.equals(ACL.SYSTEM) && !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
            downstreamPipelineAuth = Jenkins.ANONYMOUS; // cf. BuildTrigger
        } else {
            downstreamPipelineAuth = auth;
        }

        try (ACLContext _ = ACL.as(downstreamPipelineAuth)) {
            WorkflowJob upstreamPipelineObtainedAsImpersonated = Jenkins.getInstance().getItemByFullName(upstreamPipeline.getFullName(), WorkflowJob.class);
            boolean result = upstreamPipelineObtainedAsImpersonated != null;
            LOGGER.log(Level.FINE, "isUpstreamBuildVisibleByDownstreamBuildAuth({0}, {1}): taskAuth: {2}, downstreamPipelineAuth: {3}, upstreamPipelineObtainedAsImpersonated:{4}, result: {5}",
                    new Object[]{upstreamPipeline, downstreamPipeline, auth, downstreamPipelineAuth, upstreamPipelineObtainedAsImpersonated, result});
            return result;
        }
    }

    protected boolean isDownstreamVisibleByUpstreamBuildAuth(@Nonnull Item downstreamPipeline) {
        boolean result = Jenkins.getInstance().getItemByFullName(downstreamPipeline.getFullName()) != null;
        LOGGER.log(Level.FINE, "isDownstreamVisibleByUpstreamBuildAuth({0}, auth: {1}): {2}",
                new Object[]{downstreamPipeline, Jenkins.getAuthentication(), result});

        return result;
    }

}
