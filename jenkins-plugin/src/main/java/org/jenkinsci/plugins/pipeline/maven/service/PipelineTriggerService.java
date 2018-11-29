package org.jenkinsci.plugins.pipeline.maven.service;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyCause;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyCauseHelper;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyUpstreamCause;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineTriggerService {

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(PipelineTriggerService.class.getName());

    private final GlobalPipelineMavenConfig globalPipelineMavenConfig;

    public PipelineTriggerService(@Nonnull GlobalPipelineMavenConfig globalPipelineMavenConfig) {
        this.globalPipelineMavenConfig = globalPipelineMavenConfig;
    }

    public Collection<String> triggerDownstreamPipelines(@Nonnull String groupId, @Nonnull String artifactId, @Nullable String baseVersion, @Nonnull String version, @Nonnull String type, @Nonnull MavenDependencyCause cause, @Nonnull ServiceLogger logger) {
        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.setGroupId(groupId);
        mavenArtifact.setArtifactId(artifactId);
        mavenArtifact.setBaseVersion(baseVersion);
        mavenArtifact.setVersion(version);
        mavenArtifact.setType(type);

        return triggerDownstreamPipelines(Collections.singleton(mavenArtifact), cause, logger);
    }

    public Collection<String> triggerDownstreamPipelines(@Nonnull Collection<MavenArtifact> upstreamArtifacts, @Nonnull MavenDependencyCause cause, @Nonnull ServiceLogger logger) {

        if (!(cause instanceof Cause)) {
            throw new IllegalArgumentException("Given cause must extend hudson.model.Cause: " + cause);
        }

        long startTimeInNanos = System.nanoTime();

        Map<MavenArtifact, SortedSet<String>> downstreamPipelinesByArtifact = new HashMap<>();
        for(MavenArtifact mavenArtifact: upstreamArtifacts) {
            PipelineMavenPluginDao dao = globalPipelineMavenConfig.getDao();
            // FIXME use classifier in search query
            SortedSet<String> downstreamPipelines = dao.listDownstreamJobs(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion(), mavenArtifact.getBaseVersion(), mavenArtifact.getType());
            downstreamPipelinesByArtifact.put(mavenArtifact, downstreamPipelines);
        }

        Map<String, Set<MavenArtifact>> jobsToTrigger = new TreeMap<>();

        // build the list of pipelines to trigger
        for(Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamPipelinesByArtifact.entrySet()) {

            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> downstreamPipelines = entry.getValue();

            downstreamPipelinesLoop:
            for (String downstreamPipelineFullName : downstreamPipelines) {

                if (jobsToTrigger.containsKey(downstreamPipelineFullName)) {
                    // downstream pipeline has already been added to the list of pipelines to trigger,
                    // we have already verified that it's meeting requirements (not an infinite loop, authorized by security, not excessive triggering, buildable...)
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Skip eligibility check of pipeline " + downstreamPipelineFullName + " for artifact " + mavenArtifact.getShortDescription() + ", eligibility already confirmed");
                    }
                    Set<MavenArtifact> mavenArtifacts = jobsToTrigger.get(downstreamPipelineFullName);
                    if (mavenArtifacts == null) {
                        logger.log(Level.INFO, "Invalid state, no artifacts found for pipeline '" + downstreamPipelineFullName + "' while evaluating " + mavenArtifact.getShortDescription());
                    } else {
                        mavenArtifacts.add(mavenArtifact);
                    }
                    continue;
                }

                final WorkflowJob downstreamPipeline = Jenkins.getInstance().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
                if (downstreamPipeline == null || downstreamPipeline.getLastBuild() == null) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Downstream pipeline " + downstreamPipelineFullName + " or downstream pipeline last build not found. Database synchronization issue or security restriction?");
                    }
                    continue;
                }

                int downstreamBuildNumber = downstreamPipeline.getLastBuild().getNumber();

                // Avoid excessive triggering
                // See #46313
                Map<String, Integer> transitiveUpstreamPipelines = globalPipelineMavenConfig.getDao().listTransitiveUpstreamJobs(downstreamPipelineFullName, downstreamBuildNumber);
                for (String transitiveUpstreamPipelineName : transitiveUpstreamPipelines.keySet()) {
                    // Skip if one of the downstream's upstream is already building or in queue
                    // Then it will get triggered anyway by that upstream, we don't need to trigger it again
                    WorkflowJob transitiveUpstreamPipeline = Jenkins.getInstance().getItemByFullName(transitiveUpstreamPipelineName, WorkflowJob.class);

                    if (transitiveUpstreamPipeline == null) {
                        // security: not allowed to view this transitive upstream pipeline, continue to loop
                        continue;
                    } else if (transitiveUpstreamPipeline.isBuilding()) {
                        logger.log(Level.INFO, "Not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency already building: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    } else if (transitiveUpstreamPipeline.isInQueue()) {
                        logger.log(Level.INFO, "Not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency already building or in queue: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    } else if (downstreamPipelines.contains(transitiveUpstreamPipelineName)) {
                        // Skip if this downstream pipeline will be triggered by another one of our downstream pipelines
                        // That's the case when one of the downstream's transitive upstream is our own downstream
                        logger.log(Level.INFO, "Not triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency on a pipeline that will be triggered by this build: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    }
                }

                if (!downstreamPipeline.isBuildable()) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Skip triggering of non buildable (disabled: " + downstreamPipeline.isDisabled() +
                                ", isHoldOffBuildUntilSave: " + downstreamPipeline.isHoldOffBuildUntilSave() +
                                ") downstream pipeline " + downstreamPipeline.getFullName());
                    }
                    continue;
                }

                WorkflowJobDependencyTrigger downstreamPipelineTrigger = this.globalPipelineMavenConfig.getPipelineTriggerService().getWorkflowJobDependencyTrigger(downstreamPipeline);
                if (downstreamPipelineTrigger == null) {
                    LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0}: dependency trigger not configured", new Object[]{downstreamPipeline.getFullName()});
                    continue;
                }

                boolean downstreamVisibleByUpstreamBuildAuth = this.globalPipelineMavenConfig.getPipelineTriggerService().isDownstreamVisibleByUpstreamBuildAuth(downstreamPipeline);

                if (downstreamVisibleByUpstreamBuildAuth) {
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
                    LOGGER.log(Level.FINE, "Skip triggering of {0} by {1}", new Object[]{downstreamPipeline.getFullName(), cause});
                }
            }
        }

        List<String> triggeredPipelines = new ArrayList<>();
        // trigger the pipelines
        for (Map.Entry<String, Set<MavenArtifact>> entry: jobsToTrigger.entrySet()) {
            String downstreamJobFullName = entry.getKey();
            Job downstreamJob = Jenkins.getInstance().getItemByFullName(downstreamJobFullName, Job.class);
            if (downstreamJob == null) {
                logger.log(Level.INFO, "Illegal state: " + downstreamJobFullName + " not resolved");
                continue;
            }

            // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
            Run downstreamJobLastBuild = downstreamJob.getLastBuild();
            if (downstreamJobLastBuild == null) {
                // should never happen, we need at least one build to know the dependencies
            } else {
                List<MavenArtifact> matchingMavenDependencies = MavenDependencyCauseHelper.isSameCause(cause, downstreamJobLastBuild.getCauses());
                if (matchingMavenDependencies.size() > 0) {
                    downstreamJobLastBuild.addAction(new CauseAction((Cause) cause));
                    logger.log(Level.INFO, "Skip scheduling downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + " as it was already triggered for Maven dependencies: " +
                            matchingMavenDependencies.stream().map(mavenDependency -> mavenDependency == null ? null : mavenDependency.getShortDescription()).collect(Collectors.joining(", ")));
                    try {
                        downstreamJobLastBuild.save();
                    } catch (IOException e) {
                        logger.log(Level.INFO, "Failure to update build " + downstreamJobLastBuild.getFullDisplayName() + ": " + e.toString());
                    }
                    continue;
                } else {
                    // trigger build
                }
            }

            Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(downstreamJob, -1, new CauseAction((Cause) cause));

            String dependenciesMessage = cause.getMavenArtifactsDescription();
            if (queuedItem == null) {
                triggeredPipelines.add(downstreamJobFullName);
                logger.log(Level.INFO, "Skip triggering downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + " due to dependencies on " +
                        dependenciesMessage + ", invocation rejected.");
            } else {
                logger.log(Level.FINE, "Triggering downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + "#" + downstreamJob.getNextBuildNumber() + " due to dependency on " +
                        dependenciesMessage + " ...");
            }

        }
        long durationInMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        if (durationInMillis > TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS) || logger.isLoggable(Level.FINE)) {
            logger.log(Level.INFO, "triggerDownstreamPipelines completed in " + durationInMillis + " ms");
        }
        return triggeredPipelines;
    }

    /**
     * Check NO infinite loop of job triggers caused by {@link hudson.model.Cause.UpstreamCause}.
     *
     * @param initialBuild
     * @throws IllegalStateException if an infinite loop is detected
     */
    public void checkNoInfiniteLoopOfUpstreamCause(@Nonnull Run initialBuild) throws IllegalStateException {
        java.util.Queue<Run> builds = new LinkedList<>(Collections.singleton(initialBuild));
        Run currentBuild;
        while ((currentBuild = builds.poll()) != null) {
            for (Cause cause : ((List<Cause>) currentBuild.getCauses())) {
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
    public WorkflowJobDependencyTrigger getWorkflowJobDependencyTrigger(@Nonnull ParameterizedJobMixIn.ParameterizedJob parameterizedJob) {
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


    public boolean isUpstreamBuildVisibleByDownstreamBuildAuth(@Nonnull WorkflowJob upstreamPipeline, @Nonnull Queue.Task downstreamPipeline) {
        Authentication auth = Tasks.getAuthenticationOf(downstreamPipeline);
        Authentication downstreamPipelineAuth;
        if (auth.equals(ACL.SYSTEM) && !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty()) {
            downstreamPipelineAuth = Jenkins.ANONYMOUS; // cf. BuildTrigger
        } else {
            downstreamPipelineAuth = auth;
        }

        try (ACLContext ignored = ACL.as(downstreamPipelineAuth)) {
            WorkflowJob upstreamPipelineObtainedAsImpersonated = getItemByFullName(upstreamPipeline.getFullName(), WorkflowJob.class);
            boolean result = upstreamPipelineObtainedAsImpersonated != null;
            LOGGER.log(Level.FINE, "isUpstreamBuildVisibleByDownstreamBuildAuth({0}, {1}): taskAuth: {2}, downstreamPipelineAuth: {3}, upstreamPipelineObtainedAsImpersonated:{4}, result: {5}",
                    new Object[]{upstreamPipeline, downstreamPipeline, auth, downstreamPipelineAuth, upstreamPipelineObtainedAsImpersonated, result});
            return result;
        }
    }

    public boolean isDownstreamVisibleByUpstreamBuildAuth(@Nonnull Item downstreamPipeline) {
        boolean result = getItemByFullName(downstreamPipeline.getFullName(), Job.class) != null;
        LOGGER.log(Level.FINE, "isDownstreamVisibleByUpstreamBuildAuth({0}, auth: {1}): {2}",
                new Object[]{downstreamPipeline, Jenkins.getAuthentication(), result});

        return result;
    }

    @CheckForNull
    <T extends Item> T getItemByFullName(String fullName, Class<T> type) throws AccessDeniedException {
        return Jenkins.getInstance().getItemByFullName(fullName, type);
    }
}
