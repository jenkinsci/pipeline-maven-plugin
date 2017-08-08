package org.jenkinsci.plugins.pipeline.maven.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import hudson.triggers.Trigger;

public class WorkflowJobDependencyTriggerTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void jobConfiguration() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                //@formatter:off
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "semaphore 'config'\n" +
                                "properties([ pipelineTriggers([ snapshotDependencies() ]) ])\n" +
                        "}", true));
                //@formatter:on
                final WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("config/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                final WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                SemaphoreStep.success("config/1", null);
                story.j.waitForCompletion(b);
                story.j.assertBuildStatusSuccess(b);
                final List<Trigger<?>> triggers = p.getProperty(
                        PipelineTriggersJobProperty.class).getTriggers();
                assertEquals(1, triggers.size());
                assertEquals(WorkflowJobDependencyTrigger.class, triggers.get(0).getClass());
            }
        });
    }
}
