package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
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

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class PipelineMavenPluginRunListener extends RunListener<WorkflowRun> {

    private final static Logger LOGGER = Logger.getLogger(PipelineMavenPluginRunListener.class.getName());

    @Override
    public void onCompleted(WorkflowRun upstreamBuild, @Nonnull TaskListener listener) {
        LOGGER.log(Level.FINE, "onCompleted({0})", new Object[]{upstreamBuild});

        if (!GlobalPipelineMavenConfig.getTriggerDownstreamBuildsCriteria().contains(upstreamBuild.getResult())) {
            LOGGER.log(Level.FINE,"Ignore non successful build {0}", upstreamBuild);
        }

        WorkflowJob upstreamPipeline = upstreamBuild.getParent();
        List<String> downstreamPipelines = GlobalPipelineMavenConfig.getDao().listDownstreamJobs(upstreamPipeline.getFullName(), upstreamBuild.getNumber());

        for (String downstreamPipelineFullName : downstreamPipelines) {
            WorkflowJob downstreamPipeline = Jenkins.getInstance().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
            if (downstreamPipeline == null) {
                LOGGER.log(Level.FINE, "Downstream pipeline {0} not found from upstream build {1}", new Object[]{downstreamPipelineFullName, upstreamBuild});
                // job not found
                continue;
            } else if (downstreamPipeline.equals(upstreamPipeline)) {
                // don't trigger myself
                continue;
            }

            WorkflowJobDependencyTrigger downstreamPipelineTrigger = getWorkflowJobDependencyTrigger(downstreamPipeline);
            if (downstreamPipelineTrigger == null) {
                LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0} linked to upstream build {1}: dependency trigger not configured", new Object[]{downstreamPipeline, upstreamBuild});
                continue;
            }

            if (isDownstreamPipelineVisibleByUpstreamPipeline(upstreamPipeline, downstreamPipeline, listener)) {
                listener.getLogger().println("[withMaven] Trigger downstream pipeline " + downstreamPipeline.getFullDisplayName());
            } else {
                // downstream job not visible from upstream job, don't display message
            }
            LOGGER.log(Level.FINE, "Triggering downstream pipeline {0} from upstream build {1}", new Object[]{downstreamPipeline, upstreamBuild});
            // downstreamPipelineTrigger.start(downstreamPipeline, false); DOES NOT WORK
            // FIXME trigger downstream build
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

    @Override
    public void onDeleted(WorkflowRun run) {
        GlobalPipelineMavenConfig.getDao().deleteBuild(run.getParent().getFullName(), run.getNumber());
    }
}
