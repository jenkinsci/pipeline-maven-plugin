package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyAbstractCause;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyCause;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyCauseHelper;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyUpstreamCause;
import org.jenkinsci.plugins.pipeline.maven.cause.OtherMavenDependencyCause;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import java.util.stream.Collectors;

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
            Map<String, List<MavenDependencyCause>> omittedPipelineFullNamesAndCauses =  new HashMap<>();
            for (Cause cause: upstreamBuild.getCauses()) {
                if (cause instanceof MavenDependencyCause) {
                    MavenDependencyCause mavenDependencyCause = (MavenDependencyCause) cause;
                    for (String omittedPipelineFullName: mavenDependencyCause.getOmittedPipelineFullNames()) {
                        omittedPipelineFullNamesAndCauses.computeIfAbsent(omittedPipelineFullName, p-> new ArrayList<>()).add(mavenDependencyCause);
                    }
                }
            }
            if (omittedPipelineFullNamesAndCauses.isEmpty()) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] Skip triggering downstream jobs for upstream build with ignored result status " + upstreamBuild + ": " + upstreamBuild.getResult());
                }
            } else {
                for (Map.Entry<String, List<MavenDependencyCause>> entry: omittedPipelineFullNamesAndCauses.entrySet()) {
                    Job omittedPipeline = Jenkins.get().getItemByFullName(entry.getKey(), Job.class);
                    if (omittedPipeline == null) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Illegal state: " + entry.getKey() + " not resolved");
                        continue;
                    }

                    List<Cause> omittedPipelineTriggerCauses = new ArrayList<>();
                    for (MavenDependencyCause cause: entry.getValue()) {
                        if (cause instanceof MavenDependencyUpstreamCause) {
                            MavenDependencyUpstreamCause mavenDependencyUpstreamCause = (MavenDependencyUpstreamCause) cause;
                            Run<?, ?> upstreamRun = mavenDependencyUpstreamCause.getUpstreamRun() == null ? upstreamBuild:  mavenDependencyUpstreamCause.getUpstreamRun();
                            omittedPipelineTriggerCauses.add(new MavenDependencyUpstreamCause(upstreamRun, mavenDependencyUpstreamCause.getMavenArtifacts(), Collections.emptyList()));
                        } else if (cause instanceof MavenDependencyAbstractCause) {
                            try {
                                MavenDependencyCause mavenDependencyCause = ((MavenDependencyAbstractCause)cause).clone();
                                mavenDependencyCause.setOmittedPipelineFullNames(Collections.emptyList());
                                omittedPipelineTriggerCauses.add((Cause) mavenDependencyCause);
                            } catch (CloneNotSupportedException e) {
                                listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Failure to clone pipeline cause " + cause + " : " + e);
                                omittedPipelineTriggerCauses.add(new OtherMavenDependencyCause(((MavenDependencyAbstractCause) cause).getShortDescription()));
                            }
                        } else {
                            omittedPipelineTriggerCauses.add(new OtherMavenDependencyCause(((MavenDependencyAbstractCause) cause).getShortDescription()));
                        }
                    }
                    // TODO deduplicate pipeline triggers

                    // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
                    Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(omittedPipeline, -1, new CauseAction(omittedPipelineTriggerCauses));
                    if (queuedItem == null) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Failure to trigger omitted pipeline " + ModelHyperlinkNote.encodeTo(omittedPipeline) + " due to causes " +
                                omittedPipelineTriggerCauses + ", invocation rejected.");
                    } else {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Triggering downstream pipeline " +  ModelHyperlinkNote.encodeTo(omittedPipeline) + " despite build result " +
                                upstreamBuild.getResult() + " for the upstream causes: " + omittedPipelineTriggerCauses.stream().map(
                                Cause::getShortDescription).collect(Collectors.joining(", ")));
                    }
                }
            }
            return;
        }

        try {
            this.globalPipelineMavenConfig.getPipelineTriggerService().checkNoInfiniteLoopOfUpstreamCause(upstreamBuild);
        } catch (IllegalStateException e) {
            listener.getLogger().println("[withMaven] WARNING abort infinite build trigger loop. Please consider opening a Jira issue: " + e.getMessage());
            return;
        }

        WorkflowJob upstreamPipeline = upstreamBuild.getParent();

        String upstreamPipelineFullName = upstreamPipeline.getFullName();
        int upstreamBuildNumber = upstreamBuild.getNumber();
        Map<MavenArtifact, SortedSet<String>> downstreamPipelinesByArtifact = globalPipelineMavenConfig.getDao().listDownstreamJobsByArtifact(upstreamPipelineFullName, upstreamBuildNumber);
        LOGGER.log(Level.FINER, "got downstreamPipelinesByArtifact for project {0} and build #{1}: {2}", new Object[]{upstreamPipelineFullName, upstreamBuildNumber, downstreamPipelinesByArtifact});

        Map<String, Set<MavenArtifact>> jobsToTrigger = new TreeMap<>();
        Map<String, Set<String>>  omittedPipelineTriggersByPipelineFullname = new HashMap<>();
        List<String> rejectedPipelines = new ArrayList<>();

        // build the list of pipelines to trigger
        for (Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamPipelinesByArtifact.entrySet()) {

            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> downstreamPipelines = entry.getValue();

            downstreamPipelinesLoop:
            for (String downstreamPipelineFullName : downstreamPipelines) {

                if (jobsToTrigger.containsKey(downstreamPipelineFullName)) {
                    // downstream pipeline has already been added to the list of pipelines to trigger,
                    // we have already verified that it's meeting requirements (not an infinite loop, authorized by security, not excessive triggering, buildable...)
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip eligibility check of pipeline " + downstreamPipelineFullName + " for artifact " + mavenArtifact.getShortDescription() + ", eligibility already confirmed");
                    }
                    Set<MavenArtifact> mavenArtifacts = jobsToTrigger.get(downstreamPipelineFullName);
                    if (mavenArtifacts == null) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Invalid state, no artifacts found for pipeline '" + downstreamPipelineFullName + "' while evaluating " + mavenArtifact.getShortDescription());
                    } else {
                        mavenArtifacts.add(mavenArtifact);
                    }

                    continue;
                }

                if (Objects.equals(downstreamPipelineFullName, upstreamPipelineFullName)) {
                    // Don't trigger myself
                    continue;
                }

                if (rejectedPipelines.contains(downstreamPipelineFullName)) {
                    LOGGER.log(Level.FINE, "Downstream pipeline {0} already checked",
                            new Object[]{downstreamPipelineFullName});
                    continue;
                }

                final WorkflowJob downstreamPipeline = Jenkins.get().getItemByFullName(downstreamPipelineFullName, WorkflowJob.class);
                if (downstreamPipeline == null || downstreamPipeline.getLastBuild() == null) {
                    LOGGER.log(Level.FINE, "Downstream pipeline {0} or downstream pipeline last build not found from upstream build {1}. Database synchronization issue or security restriction?",
                            new Object[]{downstreamPipelineFullName, upstreamBuild.getFullDisplayName(), Jenkins.getAuthentication()});
                    rejectedPipelines.add(downstreamPipelineFullName);
                    continue;
                }

                int downstreamBuildNumber = downstreamPipeline.getLastBuild().getNumber();

                List<MavenArtifact> downstreamPipelineGeneratedArtifacts = globalPipelineMavenConfig.getDao().getGeneratedArtifacts(downstreamPipelineFullName, downstreamBuildNumber);
                if (LOGGER.isLoggable(Level.FINEST)) {
                    listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Pipeline " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + " evaluated for because it has a dependency on " + mavenArtifact + " generates " + downstreamPipelineGeneratedArtifacts);
                } 

                for (MavenArtifact downstreamPipelineGeneratedArtifact : downstreamPipelineGeneratedArtifacts) {
                    if (Objects.equals(mavenArtifact.getGroupId(), downstreamPipelineGeneratedArtifact.getGroupId()) &&
                            Objects.equals(mavenArtifact.getArtifactId(), downstreamPipelineGeneratedArtifact.getArtifactId())) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + " for " + mavenArtifact + " because it generates artifact with same groupId:artifactId " + downstreamPipelineGeneratedArtifact);
                        }
                        continue downstreamPipelinesLoop;
                    }
                }

                Map<MavenArtifact, SortedSet<String>> downstreamDownstreamPipelinesByArtifact = globalPipelineMavenConfig.getDao().listDownstreamJobsByArtifact(downstreamPipelineFullName, downstreamBuildNumber);
                for (Map.Entry<MavenArtifact, SortedSet<String>> entry2 : downstreamDownstreamPipelinesByArtifact.entrySet()) {
                    SortedSet<String> downstreamDownstreamPipelines = entry2.getValue();
                    if (downstreamDownstreamPipelines.contains(upstreamPipelineFullName)) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Infinite loop detected: skip triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) + " " +
                                " (dependency: " + mavenArtifact.getShortDescription() + ") because it is itself triggering this pipeline " +
                                ModelHyperlinkNote.encodeTo(upstreamPipeline) + " (dependency: " + entry2.getKey().getShortDescription() + ")");
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
                    WorkflowJob transitiveUpstreamPipeline = Jenkins.get().getItemByFullName(transitiveUpstreamPipelineName, WorkflowJob.class);

                    if (transitiveUpstreamPipeline == null) {
                        // security: not allowed to view this transitive upstream pipeline, continue to loop
                        continue;
                    } else if (transitiveUpstreamPipeline.getFullName().equals(upstreamPipeline.getFullName())) {
                        // this upstream pipeline of  the current downstreamPipeline is the upstream pipeline itself, continue to loop
                        continue;
                    } else if (transitiveUpstreamPipeline.isBuilding()) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency already building: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    } else if (transitiveUpstreamPipeline.isInQueue()) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency already building or in queue: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        continue downstreamPipelinesLoop;
                    } else if (downstreamPipelines.contains(transitiveUpstreamPipelineName)) {
                         // Skip if this downstream pipeline will be triggered by another one of our downstream pipelines
                         // That's the case when one of the downstream's transitive upstream is our own downstream
                         listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering " + ModelHyperlinkNote.encodeTo(downstreamPipeline) +
                                " because it has a dependency on a pipeline that will be triggered by this build: " + ModelHyperlinkNote.encodeTo(transitiveUpstreamPipeline));
                        omittedPipelineTriggersByPipelineFullname.computeIfAbsent(transitiveUpstreamPipelineName, p -> new TreeSet<>()).add(downstreamPipelineFullName);
                        continue downstreamPipelinesLoop;
                    }
                }

                if (!downstreamPipeline.isBuildable()) {
                    LOGGER.log(Level.FINE, "Skip triggering of non buildable (disabled: {0}, isHoldOffBuildUntilSave: {1}) downstream pipeline {2} from upstream build {3}",
                            new Object[]{downstreamPipeline.isDisabled(), downstreamPipeline.isHoldOffBuildUntilSave(), downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName()});
                    rejectedPipelines.add(downstreamPipelineFullName);
                    continue;
                }

                WorkflowJobDependencyTrigger downstreamPipelineTrigger = this.globalPipelineMavenConfig.getPipelineTriggerService().getWorkflowJobDependencyTrigger(downstreamPipeline);
                if (downstreamPipelineTrigger == null) {
                    LOGGER.log(Level.FINE, "Skip triggering of downstream pipeline {0} from upstream build {1}: dependency trigger not configured", new Object[]{downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName()});
                    rejectedPipelines.add(downstreamPipelineFullName);
                    continue;
                }

                boolean downstreamVisibleByUpstreamBuildAuth = this.globalPipelineMavenConfig.getPipelineTriggerService().isDownstreamVisibleByUpstreamBuildAuth(downstreamPipeline);
                boolean upstreamVisibleByDownstreamBuildAuth = this.globalPipelineMavenConfig.getPipelineTriggerService().isUpstreamBuildVisibleByDownstreamBuildAuth(upstreamPipeline, downstreamPipeline);

                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.log(Level.FINER,
                            "upstreamPipeline (" + upstreamPipelineFullName + ", visibleByDownstreamBuildAuth: " + upstreamVisibleByDownstreamBuildAuth + "), " +
                                    " downstreamPipeline (" + downstreamPipeline.getFullName() + ", visibleByUpstreamBuildAuth: " + downstreamVisibleByUpstreamBuildAuth + "), " +
                                    "upstreamBuildAuth: " + Jenkins.getAuthentication());
                }
                if (downstreamVisibleByUpstreamBuildAuth && upstreamVisibleByDownstreamBuildAuth) {
                    Set<MavenArtifact> mavenArtifactsCausingTheTrigger = jobsToTrigger.computeIfAbsent(downstreamPipelineFullName, k -> new TreeSet<>());
                    if(mavenArtifactsCausingTheTrigger.contains(mavenArtifact)) {
                        // TODO display warning
                    } else {
                        mavenArtifactsCausingTheTrigger.add(mavenArtifact);
                    }
                } else {
                    LOGGER.log(Level.FINE, "Skip triggering of {0} by {1}: downstreamVisibleByUpstreamBuildAuth: {2}, upstreamVisibleByDownstreamBuildAuth: {3}",
                            new Object[]{downstreamPipeline.getFullName(), upstreamBuild.getFullDisplayName(), downstreamVisibleByUpstreamBuildAuth, upstreamVisibleByDownstreamBuildAuth});
                }
            }
        }

        // note: we could verify that the upstreamBuild.getCauses().getOmittedPipelineFullNames are listed in jobsToTrigger

        // trigger the pipelines
        triggerPipelinesLoop:
        for (Map.Entry<String, Set<MavenArtifact>> entry: jobsToTrigger.entrySet()) {
            String downstreamJobFullName = entry.getKey();
            Job downstreamJob = Jenkins.get().getItemByFullName(downstreamJobFullName, Job.class);
            if (downstreamJob == null) {
                listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Illegal state: " + downstreamJobFullName + " not resolved");
                continue;
            }
            Set<MavenArtifact> mavenArtifacts = entry.getValue();

            // See jenkins.triggers.ReverseBuildTrigger.RunListenerImpl.onCompleted(Run, TaskListener)
            MavenDependencyUpstreamCause cause = new MavenDependencyUpstreamCause(upstreamBuild, mavenArtifacts, omittedPipelineTriggersByPipelineFullname.get(downstreamJobFullName));

            Run downstreamJobLastBuild = downstreamJob.getLastBuild();
            if (downstreamJobLastBuild == null) {
                // should never happen, we need at least one build to know the dependencies
                // trigger downstream pipeline anyway
            } else {
                List<MavenArtifact> matchingMavenDependencies = MavenDependencyCauseHelper.isSameCause(cause, downstreamJobLastBuild.getCauses());
                if (matchingMavenDependencies.isEmpty()) {
                    for (Map.Entry<String, Set<String>> omittedPipeline : omittedPipelineTriggersByPipelineFullname.entrySet()) {
                        if (omittedPipeline.getValue().contains(downstreamJobFullName)) {
                            Job transitiveDownstreamJob = Jenkins.get().getItemByFullName(entry.getKey(), Job.class);
                            listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering "
                                    + "downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + "because it will be triggered by transitive downstream " + ModelHyperlinkNote.encodeTo(transitiveDownstreamJob));
                            continue triggerPipelinesLoop; // don't trigger downstream pipeline
                        }
                    }
                    // trigger downstream pipeline
                } else {
                    downstreamJobLastBuild.addAction(new CauseAction(cause));
                    listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + " as it was already triggered for Maven dependencies: " +
                                    matchingMavenDependencies.stream().map(mavenDependency -> mavenDependency == null ? null : mavenDependency.getShortDescription()).collect(Collectors.joining(", ")));
                    try {
                        downstreamJobLastBuild.save();
                    } catch (IOException e) {
                        listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Failure to update build " + downstreamJobLastBuild.getFullDisplayName() + ": " + e.toString());
                    }
                    continue; // don't trigger downstream pipeline
                }
            }

            Queue.Item queuedItem = ParameterizedJobMixIn.scheduleBuild2(downstreamJob, -1, new CauseAction(cause));

            String dependenciesMessage = cause.getMavenArtifactsDescription();
            if (queuedItem == null) {
                listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Skip triggering downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + " due to dependencies on " +
                        dependenciesMessage + ", invocation rejected.");
            } else {
                listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - Triggering downstream pipeline " + ModelHyperlinkNote.encodeTo(downstreamJob) + "#" + downstreamJob.getNextBuildNumber() + " due to dependency on " +
                        dependenciesMessage + " ...");
            }

        }
        long durationInMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        if (durationInMillis > TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS) || LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] downstreamPipelineTriggerRunListener - completed in " + durationInMillis + " ms");
        }
    }


}
