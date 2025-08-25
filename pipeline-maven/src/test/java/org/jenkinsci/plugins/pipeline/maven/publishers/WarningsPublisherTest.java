package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.Result;
import io.jenkins.plugins.analysis.core.model.ResultAction;
import java.util.List;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

public class WarningsPublisherTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_maven_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "tasks");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing Maven console warnings", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(4);
        ResultAction resultAction = resultActions.get(0);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(2);
    }

    @Test
    public void maven_build_jar_with_java_javadoc_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "tasks");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing Java and JavaDoc warnings", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(4);
        ResultAction resultAction = resultActions.get(1);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
        resultAction = resultActions.get(2);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
    }

    @Test
    public void maven_build_jar_with_tasks_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "tasks");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing Open tasks warnings", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(4);
        ResultAction resultAction = resultActions.get(3);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalHighPrioritySize()).isEqualTo(0);
        assertThat(resultAction.getResult().getTotalNormalPrioritySize()).isEqualTo(1);
    }

    // TODO: test findbugs/spotbugs
}
