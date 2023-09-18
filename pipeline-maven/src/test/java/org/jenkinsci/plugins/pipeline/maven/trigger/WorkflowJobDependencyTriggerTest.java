package org.jenkinsci.plugins.pipeline.maven.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.triggers.Trigger;

@WithJenkins
@TestMethodOrder(OrderAnnotation.class)
public class WorkflowJobDependencyTriggerTest {

    private static File home;

    private JenkinsRule rule;

    @BeforeAll
    public static void allocateHome() throws IOException {
        home = new TemporaryDirectoryAllocator().allocate();
    }

    @BeforeEach
    public void configureJenkins(JenkinsRule r) throws Throwable {
        r.after();
        r.with(() -> home);
        r.before();
        rule = r;
    }

    @Test
    @Order(1)
    public void jobConfiguration() throws Exception {
        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "p");
        //@formatter:off
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
                "semaphore 'config'\n" +
                "properties([ pipelineTriggers([ snapshotDependencies() ]) ])\n" +
            "}", true));
        //@formatter:on
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("config/1", b);
    }

    @Test
    @Order(2)
    public void jobPersisted() throws Exception {
        WorkflowJob p = rule.jenkins.getItemByFullName("p", WorkflowJob.class);
        assertThat(p).isNotNull();
        WorkflowRun b = p.getBuildByNumber(1);
        assertThat(b).isNotNull();
        SemaphoreStep.success("config/1", null);
        rule.waitForCompletion(b);
        rule.assertBuildStatusSuccess(b);
        final List<Trigger<?>> triggers = p.getProperty(PipelineTriggersJobProperty.class).getTriggers();
        assertThat(triggers).hasSize(1);
        assertThat(triggers.get(0)).isExactlyInstanceOf(WorkflowJobDependencyTrigger.class);
    }
}
