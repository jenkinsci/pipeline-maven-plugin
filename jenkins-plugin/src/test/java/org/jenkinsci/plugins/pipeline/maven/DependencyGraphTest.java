package org.jenkinsci.plugins.pipeline.maven;

import hudson.model.Cause;
import hudson.model.Fingerprint;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;

import java.util.Hashtable;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyGraphTest extends AbstractIntegrationTest {

    @Rule
    public GitSampleRepoRule mavenWarRepoRule = new GitSampleRepoRule();


    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_pipeline_trigger() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("mavenWarRepoRule: " + mavenWarRepoRule);
        loadMavenWarProjectInGitRepo(this.mavenWarRepoRule);

        String mavenJarPipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";
        String mavenWarPipelineScript = "node('master') {\n" +
                "    git($/" + mavenWarRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";


        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenJarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded


        WorkflowJob mavenWarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-war");
        mavenWarPipeline.setDefinition(new CpsFlowDefinition(mavenWarPipelineScript, true));
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenWarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenWarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the jar project is recorded
        System.out.println("mavenWarPipelineFirstRun: " + mavenWarPipelineFirstRun);

        WorkflowRun mavenJarPipelineSecondRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));

        for (int i = 0; i < 100; i++) {
            if (jenkinsRule.jenkins.getQueue().isEmpty()|| mavenWarPipeline.isBuilding()) {
                break;
            } else {
                System.out.println("Sleep waiting for Jenkins queue to be empty (isEmpty=" + jenkinsRule.jenkins.getQueue().isEmpty() + ") " +
                        " or for " + mavenWarPipeline + " to finish to build (isBuilding=" + mavenWarPipeline.isBuilding());
                Thread.sleep(200);
            }
        }

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by " + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber(), is(mavenWarPipelineFirstRun.getNumber() + 1));
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause, notNullValue());


    }
}
