package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

public class JacocoReportPublisherITest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_implicit_jacoco_success() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");

        // @formatter:off
        String pipelineScript = "node() {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    writeFile(encoding: 'UTF-8', file: '.skip-publish-coverage-results', text: '')\n" +
                "    withMaven() {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn package verify'\n" +
                "        } else {\n" +
                "            bat 'mvn package verify'\n" +
                "        }\n" +
                "    }\n" +
                "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-jacoco");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains(
                "[withMaven] JacocoPublisher is no longer implicitly used with `withMaven` step.", build);
    }

    @Test
    public void maven_build_jar_with_explicit_jacoco_failure() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(options: [jacocoPublisher()], publisherStrategy: 'EXPLICIT') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-jacoco");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains(
                "The jacocoPublisher is deprecated as is the Jacoco plugin and you should not use it", build);
    }
}
