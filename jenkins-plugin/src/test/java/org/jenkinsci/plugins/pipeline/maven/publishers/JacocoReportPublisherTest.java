package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collection;
import java.util.List;

import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.pipeline.maven.TestUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;

import hudson.model.Result;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.tasks.junit.TestResultAction;

public class JacocoReportPublisherTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_jacoco_succeeds() throws Exception {
        loadSourceCodeInGitRepository(this.gitRepoRule, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");

        String pipelineScript = "node() {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package verify'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-jacoco");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

       Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("jar-with-jacoco-0.1-SNAPSHOT.pom", "jar-with-jacoco-0.1-SNAPSHOT.jar"));

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.pom");

        List<TestResultAction> testResultActions = build.getActions(TestResultAction.class);
        assertThat(testResultActions.size(), is(1));
        TestResultAction testResultAction = testResultActions.get(0);
        assertThat(testResultAction.getTotalCount(), is(2));
        assertThat(testResultAction.getFailCount(), is(0));

        List<JacocoBuildAction> jacocoBuildActions = build.getActions(JacocoBuildAction.class);
        assertThat(jacocoBuildActions.size(), is(1));
        JacocoBuildAction jacocoBuildAction = jacocoBuildActions.get(0);
        assertThat(jacocoBuildAction.getProjectActions().size(), is(1));
    }

}
