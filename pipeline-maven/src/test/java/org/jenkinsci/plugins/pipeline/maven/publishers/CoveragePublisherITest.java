package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.JDK;
import hudson.model.Result;
import hudson.tasks.junit.TestResultAction;
import hudson.tools.ToolLocationNodeProperty;
import io.jenkins.plugins.coverage.metrics.steps.CoverageBuildAction;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.pipeline.maven.TestUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public class CoveragePublisherITest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_jacoco_succeeds() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");

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

        List<CoverageBuildAction> coverageActions = build.getActions(CoverageBuildAction.class);
        assertThat(coverageActions).hasSize(1);
        CoverageBuildAction coverageAction = coverageActions.get(0);
        assertThat(coverageAction.getProjectActions()).hasSize(1);
        assertThat(coverageAction.getQualityGateResult()).isNotNull();
    }

    @Test
    public void maven_build_jar_with_cobertura_succeeds() throws Exception {
        String jdkName = "jdk8";
        String jdkPath = "/opt/java/jdk8";

        try (GenericContainer<?> javasContainerRule = createContainer("javas")) {
            javasContainerRule.start();

            loadSourceCodeInGitRepository(
                    this.gitRepoRule,
                    "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_cobertura_project/");

            String containerPath = exposeGitRepositoryIntoAgent(javasContainerRule);
            registerAgentForContainer(javasContainerRule);
            ToolLocationNodeProperty.ToolLocation toolLocation =
                    new ToolLocationNodeProperty.ToolLocation(new JDK.DescriptorImpl(), jdkName, jdkPath);
            ToolLocationNodeProperty toolLocationNodeProperty = new ToolLocationNodeProperty(toolLocation);
            Objects.requireNonNull(jenkinsRule.jenkins.getNode(AGENT_NAME))
                    .getNodeProperties()
                    .add(toolLocationNodeProperty);

            jenkinsRule.jenkins.getJDKs().add(new JDK(jdkName, jdkPath));

            // @formatter:off
            String pipelineScript = "node('" + AGENT_NAME + "') {\n" +
                "    git('" + containerPath + "')\n" +
                "    withMaven(jdk: '" + jdkName + "') {\n" +
                "      sh 'mvn package verify'\n" +
                "    }\n" +
                "}";
            // @formatter:on

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-cobertura");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
            assertThat(artifactsFileNames)
                    .contains("jar-with-cobertura-0.1-SNAPSHOT.pom", "jar-with-cobertura-0.1-SNAPSHOT.jar");

            verifyFileIsFingerPrinted(
                    pipeline,
                    build,
                    "jenkins/mvn/test/jar-with-cobertura/0.1-SNAPSHOT/jar-with-cobertura-0.1-SNAPSHOT.jar");
            verifyFileIsFingerPrinted(
                    pipeline,
                    build,
                    "jenkins/mvn/test/jar-with-cobertura/0.1-SNAPSHOT/jar-with-cobertura-0.1-SNAPSHOT.pom");

            List<TestResultAction> testResultActions = build.getActions(TestResultAction.class);
            assertThat(testResultActions).hasSize(1);
            TestResultAction testResultAction = testResultActions.get(0);
            assertThat(testResultAction.getTotalCount()).isEqualTo(2);
            assertThat(testResultAction.getFailCount()).isEqualTo(0);

            List<CoverageBuildAction> coverageActions = build.getActions(CoverageBuildAction.class);
            assertThat(coverageActions).hasSize(1);
            CoverageBuildAction coverageAction = coverageActions.get(0);
            assertThat(coverageAction.getProjectActions()).hasSize(1);
            assertThat(coverageAction.getQualityGateResult()).isNotNull();
        }
    }
}
