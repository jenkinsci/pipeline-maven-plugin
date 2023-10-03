package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.Result;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.tasks.junit.TestResultAction;
import java.util.Collection;
import java.util.List;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.pipeline.maven.TestUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

public class JacocoReportPublisherTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_jacoco_succeeds() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");

        // @formatter:off
        String pipelineScript = "node() {\n" + "    git($/"
                + gitRepoRule.toString() + "/$)\n" + "    withMaven() {\n"
                + "        if (isUnix()) {\n"
                + "            sh 'mvn package verify'\n"
                + "        } else {\n"
                + "            bat 'mvn package verify'\n"
                + "        }\n"
                + "    }\n"
                + "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-jacoco");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames).contains("jar-with-jacoco-0.1-SNAPSHOT.pom", "jar-with-jacoco-0.1-SNAPSHOT.jar");

        verifyFileIsFingerPrinted(
                pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(
                pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.pom");

        List<TestResultAction> testResultActions = build.getActions(TestResultAction.class);
        assertThat(testResultActions).hasSize(1);
        TestResultAction testResultAction = testResultActions.get(0);
        assertThat(testResultAction.getTotalCount()).isEqualTo(2);
        assertThat(testResultAction.getFailCount()).isEqualTo(0);

        List<JacocoBuildAction> jacocoBuildActions = build.getActions(JacocoBuildAction.class);
        assertThat(jacocoBuildActions).hasSize(1);
        JacocoBuildAction jacocoBuildAction = jacocoBuildActions.get(0);
        assertThat(jacocoBuildAction.getProjectActions()).hasSize(1);
    }
}
