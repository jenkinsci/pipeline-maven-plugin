package org.jenkinsci.plugins.pipeline.maven.listeners;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.*;
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
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyUpstreamCause;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Trigger downstream pipelines.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class DownstreamPipelineTriggerRunListener extends RunListener<WorkflowRun> {

    private final static Logger LOGGER = Logger.getLogger(DownstreamPipelineTriggerRunListener.class.getName());
    
    @Inject
    public GlobalPipelineMavenConfig globalPipelineMavenConfig;

    @Override
    public void onCompleted(WorkflowRun upstreamBuild, @Nonnull TaskListener listener) {
        LOGGER.log(Level.FINER, "onCompleted({0})", new Object[]{upstreamBuild});
        long startTimeInNanos = System.nanoTime();
        if(LOGGER.isLoggable(Level.FINER)) {
            listener.getLogger().println("[withMaven] pipelineGraphPublisher - triggerDownstreamPipelines");
        }

        if (!globalPipelineMavenConfig.getTriggerDownstreamBuildsResultsCriteria().contains(upstreamBuild.getResult())) {
            if (LOGGER.isLoggable(Level.FINER)) {
                listener.getLogger().println("[withMaven] Skip downstream job triggering for upstream build with ignored result status " + upstreamBuild + ": " + upstreamBuild.getResult());
            }
            return;
        }

        try {
            checkNoInfiniteLoopOfUpstreamCause(upstreamBuild);
        } catch (IllegalStateException e) {
            listener.getLogger().println("[withMaven] WARNING abort infinite build trigger loop. Please consider opening a Jira issue: " + e.getMessage());
            return;
        }

        WorkflowJob upstreamPipeline = upstreamBuild.getParent();

        String upstreamPipelineFullName = upstreamPipeline.getFullName();
        int upstreamBuildNumber = upstreamBuild.getNumber();
        Map<MavenArtifact, SortedSet<String>> downstreamPipelinesByArtifact = globalPipelineMavenConfig.getDao().listDownstreamJobsByArtifact(upstreamPipelineFullName, upstreamBuildNumber);

        Map<String, Set<MavenArtifact>> jobsToTrigger = new TreeMap<>();

        for(Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamPipelinesByArtifact.entrySet()) {

            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> downstreamPipelines = entry.getValue();

            downstreamPipelinesLoop:
            for (String downstreamPipelineFullName : downstreamPipelines) {

                if (jobsToTrigger.containsKey(downstreamPipelineFullName)) {
                    // downstream pipeline has already been added to the list of pipelines to trigger,
                    // it's meeting requirements (not an infinite loop, authorized by security, not excessive triggering, buildable...)
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        listener.getLogger().println("[withMaven - DownstreamPipelineTriggerRunListener] Skip eligibility check of pipeline " + downstreamPipelineFullName + " for artifact " + mavenArtifact.getShortDescription() + ", eligibility already confirmed");
                    }
                    Set<MavenArtifact> mavenArtifacts = jobsToTrigger.get(downstreamPipelineFullName);
                    if (mavenArtifacts == null) {
                        listener.getLogger().println("[withMaven - DownstreamPipelineTriggerRunListener] Invalid state, no artifacts found for pipeline '" + downstreamPipelineFullName + "' while evaluating " + mavenArtifact.getShortDescription());
                    } else {
                        mavenArtifacts.add(mavenArtifact);
                    }
                    continue;
                }

                if (Objects.equals(downstreamPipelineFullName, upstreamPipelineFullName)) {
                    // Don't trigger myself
                    continue;
                }

                final WorkflowJob downstreamPipeline = Jenkins.getInstance().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
                if (downstreamPipeline == null || downstreamPipeline.getLastBuild() == null) {
                    LOGGER.log(Level.FINE, "Downstream pipeline {0} or downstream pipeline last build not found from upstream build {1}. Database synchronization issue or security restriction?",
                            new Object[]{downstreamPipelineFullName, upstreamBuild.getFullDisplayName(), Jenkins.getAuthentication()});
                    continue;
                }

                int downstreamBuildNumber = downstreamPipeline.getLastBuild().getNumber();

                Map<MavenArtifact, SortedSet<String>> downstreamDownstreamPipelinesByArtifact = globalPipelineMavenConfig.getDao().listDownstreamJobsByArtifact(downstreamPipelineFullName, downstreamBuildNumber);
                for (Map.Entry<MavenArtifact, SortedSet<String>> entry2 : downstreamDownstreamPipelinesByArtifact.entrySet()) {
                    SortedSet<String> downstreamDownstreamPipelines = entry2.getValue();
                    if (downstreamDownstreamPipelines.contains(upstreamPipelineFullName)) {
                            listener.getLogger().println("[withMaven] Infinite loop detected: not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + " " +
                                    " (dependency: " + mavenArtifact.getId() + ") because it is itself triggering this pipeline " +
                                    ModelHyperlinkNote.encodeTo(upstreamPipeline) + " (dependency: " + entry2.getKey() + ")");
                        // prevent infinite loop
                        continue downstreamPipelinesLoop;
                    }
                }

                // Avoid excessive triggering
                // See #46313
                Map<String, Integer> transitiveUpstreamPipelines = globalPipelineMavenConfig.getDao().listTransitiveUpstreamJobs(downstreamPipelineFullName, downstreamBuildNumber);
                for (String transitiveUpstreamPipelineName : transitiveUpstreamPipelines.keySet()) {
                    // Skip if one of the downstream's upstream is already building or in queue
                    // Then it will get triggered anyway by that upstream, we don't need to trigger it again
                    WorkflowJob transitiveUpstreamPipeline = Jenkins.getInstance().getItemByFullName(transitiveUpstreamPipelineName, WorkflowJob.class);

                    if (transitiveUpstreamPipeline == null) {
                        // security: not allowed to view this transitive upstream pipeline, skip
                        continue;
                    }
                    if (!transitiveUpstreamPipeline.equals(upstreamPipeline) && (transitiveUpstreamPipeline.isBuilding() || transitiveUpstreamPipeline.isInQueue())) {
                        listener.getLogger().println("[withMaven] Not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency already building or in queue: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    }

                    // Skip if this downstream pipeline will be triggered by another one of our downstream pipelines
                    // That's the case when one of the downstream's transitive upstream is our own downstream
                    if (downstreamPipelines.contains(transitiveUpstreamPipelineName)) {
                        listener.getLogger().println("[withMaven] Not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency on a pipeline that will be triggered by this build: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    }
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
                            "upstreamPipeline (" + upstreamPipelineFullName + ", visibleByDownstreamBuildAuth: " + upstreamVisibleByDownstreamBuildAuth + "), " +
                                    " downstreamPipeline (" + downstreamPipeline.getFullName() + ", visibleByUpstreamBuildAuth: " + downstreamVisibleByUpstreamBuildAuth + "), " +
                                    "upstreamBuildAuth: " + Jenkins.getAuthentication());
                }
                if (downstreamVisibleByUpstreamBuildAuth && upstreamVisibleByDownstreamBuildAuth) {
                    Set<MavenArtifact> mavenArtifacts = jobsToTrigger.get(downstreamPipelineFullName);
                    if (mavenArtifacts == null) {
                        mavenArtifacts = new TreeSet<>();
                        jobsToTrigger.put(downstreamPipelineFullName, mavenArtifacts);
                    }
                    if(mavenArtifacts.contains(mavenArtifact)) {
                        // TODO display warning
                    } else {
                        mavenArtifacts.add(mavenArtifact);
                    }
                } else {
                    LOGGER.log(Level.FINE, "Skip triggering of {0} by {1}: downstreamVisibleByUpstreamBuildAuth: {2}, upstreamVisibleByDownstreamBuildAuth: {3}",
                            new Object[]{downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName(), downstreamVisibleByUpstreamBuildAuth, upstreamVisibleByDownstreamBuildAuth});
                }
            }
        }

        for (Map.Entry<String, Set<MavenArtifact>> entry: jobsToTrigger.entrySet()) {
            String downstreamJobFullName = entry.getKey();
            Job downstreamJob = Jenkins.getInstance().getItemByFullName(downstreamJobFullName, Job.class);
            Set<MavenArtifact> mavenArtifacts = entry.getValue();

            // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
            MavenDependencyUpstreamCause cause = new MavenDependencyUpstreamCause(upstreamBuild, mavenArtifacts);
            Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(downstreamJob, -1, new CauseAction(cause));

            String dependenciesMessage = cause.getMavenArtifactsDescription();
            if (queuedItem == null) {
                listener.getLogger().println("[withMaven] Skip scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + " due to dependencies on " +
                        dependenciesMessage + ", invocation rejected.");
            } else {
                listener.getLogger().println("[withMaven] Scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + "#" + downstreamJob.getNextBuildNumber() + " due to dependency on " +
                        dependenciesMessage + " ...");
            }

        }
        long durationInMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        if (durationInMillis > TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS) || LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] triggerDownstreamPipelines completed in " + durationInMillis + " ms");
        }
    }

    /**
     * Check NO infinite loop of job triggers caused by {@link hudson.model.Cause.UpstreamCause}.
     *
     * @param initialBuild
     * @throws IllegalStateException if an infinite loop is detected
     */
    protected void checkNoInfiniteLoopOfUpstreamCause(@Nonnull Run initialBuild) throws IllegalStateException {
        java.util.Queue<Run> builds = new LinkedList<>(Collections.singleton(initialBuild));
        Run currentBuild;
        while ((currentBuild = builds.poll()) != null) {
            for(Cause cause: ((List<Cause>) currentBuild.getCauses())) {
                if (cause instanceof Cause.UpstreamCause) {
                    Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
                    Run<?, ?> upstreamBuild = upstreamCause.getUpstreamRun();
                    if (upstreamBuild == null) {
                        // Can be Authorization, build deleted on the file system...
                    } else if (Objects.equals(upstreamBuild.getParent().getFullName(), initialBuild.getParent().getFullName())) {
                        throw new IllegalStateException("Infinite loop of job triggers ");
                    } else {
                        builds.add(upstreamBuild);
                    }
                }
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
