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
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
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

        if (!(cause instanceof Cause)) {
            throw new IllegalArgumentException("Given cause must extend hudson.model.Cause: " + cause);
        }

        long startTimeInNanos = System.nanoTime();

        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.setGroupId(groupId);
        mavenArtifact.setArtifactId(artifactId);
        mavenArtifact.setBaseVersion(baseVersion);
        mavenArtifact.setVersion(version);
        mavenArtifact.setType(type);

        SortedSet<String> downstreamPipelines = globalPipelineMavenConfig.getDao().listDownstreamJobs(groupId, artifactId, version, baseVersion, type);

        SortedSet<String> jobsToTrigger = new TreeSet<>();

        downstreamPipelinesLoop:
        for (String downstreamPipelineFullName : downstreamPipelines) {

            final WorkflowJob downstreamPipeline = getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
            if (downstreamPipeline == null || downstreamPipeline.getLastBuild() == null) {
                logger.log(Level.FINE, "Downstream pipeline " + downstreamPipelineFullName + " or downstream pipeline last build not found. Database synchronization issue or security restriction?");
                continue;
            }

            int downstreamBuildNumber = downstreamPipeline.getLastBuild().getNumber();

            // Avoid excessive triggering
            // See #46313
            Map<String, Integer> transitiveUpstreamPipelines = globalPipelineMavenConfig.getDao().listTransitiveUpstreamJobs(downstreamPipelineFullName, downstreamBuildNumber);
            for (String transitiveUpstreamPipelineName : transitiveUpstreamPipelines.keySet()) {
                // Skip if one of the downstream's upstream is already building or in queue
                // Then it will get triggered anyway by that upstream, we don't need to trigger it again
                WorkflowJob transitiveUpstreamPipeline = getItemByFullName(transitiveUpstreamPipelineName, WorkflowJob.class);

                if (transitiveUpstreamPipeline == null) {
                    // security: not allowed to view this transitive upstream pipeline, skip
                    continue;
                }
                if (transitiveUpstreamPipeline.isBuilding() || transitiveUpstreamPipeline.isInQueue()) {
                    logger.log(Level.INFO, "Not triggering " + logger.modelHyperlinkNoteEncodeTo(downstreamPipeline) +
                            " because it has a dependency already building or in queue: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                    continue downstreamPipelinesLoop;
                }

                // Skip if this downstream pipeline will be triggered by another one of our downstream pipelines
                // That's the case when one of the downstream's transitive upstream is our own downstream
                if (downstreamPipelines.contains(transitiveUpstreamPipelineName)) {
                    logger.log(Level.INFO, "Not triggering " + logger.modelHyperlinkNoteEncodeTo(downstreamPipeline) +
                            " because it has a dependency on a pipeline that will be triggered by this build: " +  logger.modelHyperlinkNoteEncodeTo(transitiveUpstreamPipeline));
                    continue downstreamPipelinesLoop;
                }
            }

            if (!downstreamPipeline.isBuildable()) {
                logger.log(Level.FINE, "Skip triggering of non buildable (disabled: " +
                        downstreamPipeline.isDisabled() + ", isHoldOffBuildUntilSave: " +
                        downstreamPipeline.isHoldOffBuildUntilSave() + ") downstream pipeline " + downstreamPipeline.getFullName());
                continue;
            }

            WorkflowJobDependencyTrigger downstreamPipelineTrigger = getWorkflowJobDependencyTrigger(downstreamPipeline);
            if (downstreamPipelineTrigger == null) {
                if (logger.isLoggable(Level.FINE))
                    logger.log(Level.FINE, "Skip triggering of downstream pipeline " + downstreamPipeline.getFullName() + ": dependency trigger not configured");
                continue;
            }

            boolean downstreamVisibleByUpstreamBuildAuth = isDownstreamVisibleByUpstreamBuildAuth(downstreamPipeline);

            if (downstreamVisibleByUpstreamBuildAuth) {

                jobsToTrigger.add(downstreamPipelineFullName);

            } else {
                LOGGER.log(Level.FINE, "Skip triggering of {0} by CLI , not allowed to see downstream", new Object[]{downstreamPipeline.getFullName()});
            }
        }

        for (String downstreamJobFullName : jobsToTrigger) {
            Job downstreamJob = getItemByFullName(downstreamJobFullName, Job.class);
            if (downstreamJob == null) {
                logger.log(Level.WARNING, "Illegal state: downstream job " + downstreamJobFullName + " not resolved");
                continue;
            }

            // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
            cause.setMavenArtifacts(Collections.singletonList(mavenArtifact));

            Run downstreamJobLastBuild = downstreamJob.getLastBuild();
            if (downstreamJobLastBuild == null) {
                // should never happen, we need at least one build to know the dependencies
            } else {
                List<MavenArtifact> matchingMavenDependencies = MavenDependencyCauseHelper.isSameCause(cause, downstreamJobLastBuild.getCauses());
                if (matchingMavenDependencies.size() > 0) {
                    downstreamJobLastBuild.addAction(new CauseAction((Cause) cause));
                    logger.log(Level.INFO,
                            "Skip scheduling downstream pipeline " + logger.modelHyperlinkNoteEncodeTo(downstreamJob) + " as it was already triggered for Maven dependencies: " +
                            matchingMavenDependencies.stream().map(mavenDependency -> mavenDependency == null ? null : mavenDependency.getShortDescription()).collect(Collectors.joining(", ")));
                    try {
                        downstreamJobLastBuild.save();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Failure to update build " + downstreamJobLastBuild.getFullDisplayName() + ": " + e.toString());
                    }
                    continue;
                }
            }

            Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(downstreamJob, -1, new CauseAction((Cause) cause));

            String dependenciesMessage = cause.getMavenArtifactsDescription();
            if (queuedItem == null) {
                logger.log(Level.INFO, "Skip scheduling downstream pipeline " + logger.modelHyperlinkNoteEncodeTo(downstreamJob) + " due to dependencies on " +
                        dependenciesMessage + ", invocation rejected.");
            } else {
                logger.log(Level.INFO, "Scheduling downstream pipeline " +  logger.modelHyperlinkNoteEncodeTo(downstreamJob) + "#" + downstreamJob.getNextBuildNumber() + " due to dependency on " +
                        dependenciesMessage + " ...");
            }
        }

        long durationInMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        if (durationInMillis > TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS) || logger.isLoggable(Level.FINE)) {
            logger.log(Level.INFO, "triggerDownstreamPipelines completed in " + durationInMillis + " ms");
        }

        return jobsToTrigger;
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
