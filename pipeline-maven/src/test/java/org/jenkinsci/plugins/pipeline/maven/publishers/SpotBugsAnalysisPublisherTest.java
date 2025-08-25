package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

public class SpotBugsAnalysisPublisherTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_implicit_spotbugs_success() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn package verify'\n" +
                "        } else {\n" +
                "            bat 'mvn package verify'\n" +
                "        }\n" +
                "    }\n" +
                "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-spotbugs");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains(
                "[withMaven] SpotBugs Publisher is no longer implicitly used with `withMaven` step.", build);
    }

    @Test
    public void maven_build_jar_with_explicit_spotbugs_failure() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(options: [spotbugsPublisher()], publisherStrategy: 'EXPLICIT') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-spotbugs");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains(
                "The spotbugsPublisher is deprecated as is the findbugs plugin and you should not use it", build);
    }
}
