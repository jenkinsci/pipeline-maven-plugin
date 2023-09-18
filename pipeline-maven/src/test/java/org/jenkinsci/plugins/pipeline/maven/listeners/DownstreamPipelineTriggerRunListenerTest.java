package org.jenkinsci.plugins.pipeline.maven.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.WithMavenStep;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyUpstreamCause;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.UpstreamMemory;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.queue.ScheduleResult;
import jenkins.model.Jenkins;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DownstreamPipelineTriggerRunListenerTest {

    @InjectMocks
    private DownstreamPipelineTriggerRunListener listener;

    @Mock
    private GlobalPipelineMavenConfig config;

    @Mock
    private PipelineTriggerService service;

    @Mock
    private WorkflowJobDependencyTrigger trigger;

    @Mock
    private PipelineMavenPluginDao dao;

    @Mock
    private WorkflowRun build;

    @Mock
    private TaskListener taskListener;

    @Mock
    private PrintStream stream;

    @Mock
    private Jenkins jenkins;

    @Mock
    private Queue queue;

    @Mock
    private ScheduleResult queueResult;

    @Mock
    private Item queuedItem;

    @Mock
    private FlowExecutionOwner flowExecutionOwner;

    @Mock
    private FlowExecution flowExecution;

    @Mock
    private FlowNode flowNode;

    @BeforeEach
    public void configureMocks() throws Exception {
        when(config.getTriggerDownstreamBuildsResultsCriteria()).thenReturn(Collections.singleton(Result.SUCCESS));
        when(config.getPipelineTriggerService()).thenReturn(service);
        when(config.getDao()).thenReturn(dao);
        when(service.getWorkflowJobDependencyTrigger(any())).thenReturn(trigger);
        when(taskListener.getLogger()).thenReturn(stream);
        when(build.asFlowExecutionOwner()).thenReturn(flowExecutionOwner);
        when(flowExecutionOwner.get()).thenReturn(flowExecution);
        when(flowExecution.getCurrentHeads()).thenReturn(Arrays.asList(flowNode));
        when(flowNode.getDisplayFunctionName()).thenReturn(WithMavenStep.DescriptorImpl.FUNCTION_NAME);
    }

    @Test
    public void test_abort_when_step_not_run() {
        when(flowExecution.getCurrentHeads()).thenReturn(Collections.emptyList());

        listener.onCompleted(build, taskListener);

        verifyNoMoreInteractions(dao, service, trigger);
    }

    @Test
    public void test_unwanted_result_without_cause() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            when(build.getResult()).thenReturn(Result.UNSTABLE);
            when(build.getCauses()).thenReturn(Collections.emptyList());

            listener.onCompleted(build, taskListener);

            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_unwanted_result_with_other_cause() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            when(build.getResult()).thenReturn(Result.UNSTABLE);
            when(build.getCauses()).thenReturn(Arrays.asList(mock(Cause.class)));

            listener.onCompleted(build, taskListener);

            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_unwanted_result_with_maven_cause_without_pipeline() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            when(build.getResult()).thenReturn(Result.UNSTABLE);
            when(build.getCauses()).thenReturn(Arrays.asList(cause));
            when(cause.getOmittedPipelineFullNames()).thenReturn(Collections.emptyList());

            listener.onCompleted(build, taskListener);

            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_unwanted_result_with_maven_cause_with_pipeline_without_job() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            when(build.getResult()).thenReturn(Result.UNSTABLE);
            when(build.getCauses()).thenReturn(Arrays.asList(cause));
            when(cause.getOmittedPipelineFullNames()).thenReturn(Arrays.asList("omitted pipeline"));
            when(jenkins.getItemByFullName("omitted pipeline", Job.class)).thenReturn(null);

            listener.onCompleted(build, taskListener);

            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_unwanted_result_with_maven_cause_with_pipeline_with_job() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(build.getResult()).thenReturn(Result.UNSTABLE);
            when(build.getCauses()).thenReturn(Arrays.asList(cause), Arrays.asList(mock(Cause.class)));
            when(build.getParent()).thenReturn(job);
            when(cause.getOmittedPipelineFullNames()).thenReturn(Arrays.asList("omitted pipeline"));
            when(jenkins.getItemByFullName("omitted pipeline", Job.class)).thenReturn(job);
            when(job.getFullDisplayName()).thenReturn("omitted pipeline");

            listener.onCompleted(build, taskListener);

            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_infinite_loop() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            when(build.getResult()).thenReturn(Result.SUCCESS);
            doThrow(IllegalStateException.class).when(service).checkNoInfiniteLoopOfUpstreamCause(build);

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_wanted_result_without_downstream() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(job.getFullName()).thenReturn("pipeline");
            when(build.getResult()).thenReturn(Result.SUCCESS);
            when(build.getParent()).thenReturn(job);
            when(build.getNumber()).thenReturn(42);
            when(dao.listDownstreamJobsByArtifact("pipeline", 42)).thenReturn(Collections.emptyMap());

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verify(dao).listDownstreamJobsByArtifact("pipeline", 42);
            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_wanted_result_with_downstream_without_job() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(job.getFullName()).thenReturn("pipeline");
            when(build.getResult()).thenReturn(Result.SUCCESS);
            when(build.getParent()).thenReturn(job);
            when(build.getNumber()).thenReturn(42);
            when(dao.listDownstreamJobsByArtifact("pipeline", 42)).thenReturn(
                    Collections.singletonMap(new MavenArtifact("groupId:artifactId:jar:version"), new TreeSet<>(Collections.singleton("downstream"))));

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verify(dao).listDownstreamJobsByArtifact("pipeline", 42);
            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_wanted_result_with_downstream_without_artifacts() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(job.getFullName()).thenReturn("pipeline");
            WorkflowRun downstreamBuild = mock(WorkflowRun.class);
            when(downstreamBuild.getNumber()).thenReturn(4242);
            WorkflowJob downstream = mock(WorkflowJob.class);
            when(downstream.getLastBuild()).thenReturn(downstreamBuild);
            when(build.getResult()).thenReturn(Result.SUCCESS);
            when(build.getParent()).thenReturn(job);
            when(build.getNumber()).thenReturn(42);
            when(dao.listDownstreamJobsByArtifact("pipeline", 42)).thenReturn(
                    Collections.singletonMap(new MavenArtifact("groupId:artifactId:jar:version"), new TreeSet<>(Collections.singleton("downstream"))));
            when(jenkins.getItemByFullName("downstream", Job.class)).thenReturn(downstream);

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verify(dao).listDownstreamJobsByArtifact("pipeline", 42);
            verify(dao).getGeneratedArtifacts("downstream", 4242);
            verify(dao).listDownstreamJobsByArtifact("downstream", 4242);
            verify(dao).listTransitiveUpstreamJobs(eq("downstream"), eq(4242), any(UpstreamMemory.class));
            verifyNoMoreInteractions(dao, service, trigger);
        }
    }

    @Test
    public void test_wanted_result_with_downstream() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getQueue()).thenReturn(queue);
            when(queue.schedule2(any(), anyInt(), anyList())).thenReturn(queueResult);
            when(queueResult.getItem()).thenReturn(queuedItem);

            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(job.getFullName()).thenReturn("pipeline");
            WorkflowRun downstreamBuild = mock(WorkflowRun.class);
            when(downstreamBuild.getNumber()).thenReturn(4242);
            WorkflowJob downstream = mock(WorkflowJob.class);
            when(downstream.getLastBuild()).thenReturn(downstreamBuild);
            when(downstream.isBuildable()).thenReturn(true);
            when(downstream.getFullDisplayName()).thenReturn("downstream");
            when(build.getResult()).thenReturn(Result.SUCCESS);
            when(build.getParent()).thenReturn(job);
            when(build.getNumber()).thenReturn(42);
            when(dao.listDownstreamJobsByArtifact("pipeline", 42)).thenReturn(
                    Collections.singletonMap(new MavenArtifact("groupId:upstreamArtifactId:jar:version"), new TreeSet<>(Collections.singleton("downstream"))));
            when(jenkins.getItemByFullName("downstream", Job.class)).thenReturn(downstream);
            when(dao.getGeneratedArtifacts("downstream", 4242))
                    .thenReturn(Collections.singletonList(new MavenArtifact("groupId:downstreamArtifactId:jar:version")));
            when(dao.listDownstreamJobsByArtifact("downstream", 4242))
                    .thenReturn(Collections.singletonMap(new MavenArtifact("groupId:downstreamArtifactId:jar:version"), new TreeSet<>()));
            when(service.isDownstreamVisibleByUpstreamBuildAuth(downstream)).thenReturn(true);
            when(service.isUpstreamBuildVisibleByDownstreamBuildAuth(job, downstream)).thenReturn(true);

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verify(dao).listDownstreamJobsByArtifact("pipeline", 42);
            verify(dao).getGeneratedArtifacts("downstream", 4242);
            verify(dao).listDownstreamJobsByArtifact("downstream", 4242);
            verify(dao).listTransitiveUpstreamJobs(eq("downstream"), eq(4242), any(UpstreamMemory.class));
            verify(service).getWorkflowJobDependencyTrigger(downstream);
            verify(service).isDownstreamVisibleByUpstreamBuildAuth(downstream);
            verify(service).isUpstreamBuildVisibleByDownstreamBuildAuth(job, downstream);
            verify(queue).schedule2(eq(downstream), anyInt(), anyList());
            verify(queue).contains(any(Task.class));
            verifyNoMoreInteractions(dao, service, trigger, queue);
        }
    }

    @Test
    public void test_wanted_result_with_multiple_downstreams() {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class)) {
            j.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getQueue()).thenReturn(queue);
            when(queue.schedule2(any(), anyInt(), anyList())).thenReturn(queueResult);
            when(queueResult.getItem()).thenReturn(queuedItem);

            Map<MavenArtifact, SortedSet<String>> downstreamJobs = new HashMap<>();
            downstreamJobs.put(new MavenArtifact("groupId:upstreamArtifactId1:jar:version"), new TreeSet<>(Collections.singleton("downstream")));
            downstreamJobs.put(new MavenArtifact("groupId:upstreamArtifactId2:jar:version"), new TreeSet<>(Collections.singleton("downstream")));
            MavenDependencyUpstreamCause cause = mock(MavenDependencyUpstreamCause.class);
            WorkflowJob job = mock(WorkflowJob.class);
            when(job.getFullName()).thenReturn("pipeline");
            WorkflowRun downstreamBuild = mock(WorkflowRun.class);
            when(downstreamBuild.getNumber()).thenReturn(4242);
            WorkflowJob downstream = mock(WorkflowJob.class);
            when(downstream.getLastBuild()).thenReturn(downstreamBuild);
            when(downstream.isBuildable()).thenReturn(true);
            when(downstream.getFullDisplayName()).thenReturn("downstream");
            when(build.getResult()).thenReturn(Result.SUCCESS);
            when(build.getParent()).thenReturn(job);
            when(build.getNumber()).thenReturn(42);
            when(dao.listDownstreamJobsByArtifact("pipeline", 42)).thenReturn(downstreamJobs);
            when(jenkins.getItemByFullName("downstream", Job.class)).thenReturn(downstream);
            when(dao.getGeneratedArtifacts("downstream", 4242))
                    .thenReturn(Collections.singletonList(new MavenArtifact("groupId:downstreamArtifactId:jar:version")));
            when(dao.listDownstreamJobsByArtifact("downstream", 4242))
                    .thenReturn(Collections.singletonMap(new MavenArtifact("groupId:downstreamArtifactId:jar:version"), new TreeSet<>()));
            when(service.isDownstreamVisibleByUpstreamBuildAuth(downstream)).thenReturn(true);
            when(service.isUpstreamBuildVisibleByDownstreamBuildAuth(job, downstream)).thenReturn(true);

            listener.onCompleted(build, taskListener);

            verify(service).checkNoInfiniteLoopOfUpstreamCause(build);
            verify(dao).listDownstreamJobsByArtifact("pipeline", 42);
            verify(dao).getGeneratedArtifacts("downstream", 4242);
            verify(dao).listDownstreamJobsByArtifact("downstream", 4242);
            verify(dao).listTransitiveUpstreamJobs(eq("downstream"), eq(4242), any(UpstreamMemory.class));
            verify(service).getWorkflowJobDependencyTrigger(downstream);
            verify(service).isDownstreamVisibleByUpstreamBuildAuth(downstream);
            verify(service).isUpstreamBuildVisibleByDownstreamBuildAuth(job, downstream);
            verify(queue).schedule2(eq(downstream), anyInt(), anyList());
            verify(queue).contains(any(Task.class));
            verifyNoMoreInteractions(dao, service, trigger, queue);
        }
    }

}
