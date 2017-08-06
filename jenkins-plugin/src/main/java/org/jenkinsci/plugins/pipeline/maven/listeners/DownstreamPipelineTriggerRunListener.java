package org.jenkinsci.plugins.pipeline.maven.listeners;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.ACLContext;
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
import java.util.Objects;
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

            if (!downstreamPipeline.isBuildable()) {
                LOGGER.log(Level.FINE, "Skip triggering of disabled downstream pipeline {0} from upstream build (1}", new Object[]{downstreamPipeline, upstreamBuild});
                continue;
            }

            WorkflowJobDependencyTrigger downstreamPipelineTrigger = getWorkflowJobDependencyTrigger(downstreamPipeline);
            if (downstreamPipelineTrigger == null) {
                LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0} from upstream build {1}: dependency trigger not configured", new Object[]{downstreamPipeline, upstreamBuild});
                continue;
            }

            LOGGER.log(Level.FINE, "Triggering downstream pipeline {0} from upstream build {1}", new Object[]{downstreamPipeline, upstreamBuild});

            boolean downstreamVisibleByUpstreamBuildAuth = isDownstreamVisibleByUpstreamBuildAuth(downstreamPipeline);
            boolean upstreamVisibleByDownstreamBuildAuth = isUpstreamBuildVisibleByDownstreamBuildAuth(upstreamPipeline, downstreamPipeline);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                        "upstreamPipeline (" + upstreamPipeline + ", visibleByDownstreamBuildAuth: " + upstreamVisibleByDownstreamBuildAuth + "), " +
                                " downstreamPipeline (" + downstreamPipeline + ", visibleByUpstreamBuildAuth: " + downstreamVisibleByUpstreamBuildAuth + "), " +
                                "upstreamBuildAuth: " + Jenkins.getAuthentication());
            }
            if (downstreamVisibleByUpstreamBuildAuth && upstreamVisibleByDownstreamBuildAuth) {
                // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
                String name = ModelHyperlinkNote.encodeTo(downstreamPipeline) + " #" + downstreamPipeline.getNextBuildNumber();
                if (ParameterizedJobMixIn.scheduleBuild2(downstreamPipeline, -1, new CauseAction(new Cause.UpstreamCause(upstreamBuild))) != null) {
                    listener.getLogger().println("[withMaven] Scheduling downstream pipeline " + name + "...");
                } else {
                    listener.getLogger().println("[withMaven] Not scheduling downstream pipeline " + name + ", it is already in the queue.");
                }
            } else {
                LOGGER.log(Level.FINE, "Skip triggering of {0} by {1}: downstreamVisibleByUpstreamBuildAuth:{2}, upstreamVisibleByDownstreamBuildAuth:{3}",
                        new Object[]{downstreamPipeline, upstreamBuild, downstreamVisibleByUpstreamBuildAuth, upstreamVisibleByDownstreamBuildAuth});
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
            LOGGER.log(Level.FINE, "isUpstreamBuildVisibleByDownstreamBuildAuth({0}, {1}): taskAuth: {2}, downstreamPipelineAuth: {3}, upstreamPipelineObtainedAsImpersonated:{4}",
                    new Object[]{upstreamPipeline, downstreamPipeline, auth, downstreamPipelineAuth, upstreamPipelineObtainedAsImpersonated});
            return (upstreamPipelineObtainedAsImpersonated != null);
        }
    }

    protected boolean isDownstreamVisibleByUpstreamBuildAuth(@Nonnull Item downstreamPipeline) {
        return Jenkins.getInstance().getItemByFullName(downstreamPipeline.getFullName()) != null;
    }

}
