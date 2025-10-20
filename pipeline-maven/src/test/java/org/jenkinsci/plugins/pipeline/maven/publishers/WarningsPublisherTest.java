package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.JDK;
import hudson.model.Result;
import hudson.tools.ToolLocationNodeProperty;
import io.jenkins.plugins.analysis.core.model.ResultAction;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public class WarningsPublisherTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_with_maven_succeeds() throws Exception {
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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "maven_console");
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
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "java_javadoc");
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
            "    withMaven() {\n" +
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
        jenkinsRule.assertLogContains("Scanning all 3 files for open tasks", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(4);
        ResultAction resultAction = resultActions.get(3);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalHighPrioritySize()).isEqualTo(0);
        assertThat(resultAction.getResult().getTotalNormalPrioritySize()).isEqualTo(1);
    }

    @Test
    public void maven_build_jar_with_spotbugs_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify com.github.spotbugs:spotbugs-maven-plugin:check'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify com.github.spotbugs:spotbugs-maven-plugin:check'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "spotbugs");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing spotbugs warnings", build);
        jenkinsRule.assertLogContains("Successfully processed file 'target/spotbugsXml.xml'", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(5);
        ResultAction resultAction = resultActions.get(4);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
    }

    @Test
    public void maven_build_jar_with_findbugs_succeeds() throws Exception {
        String jdkName = "jdk8";
        String jdkPath = "/opt/java/jdk8";

        try (GenericContainer<?> javasContainerRule = createContainer("javas")) {
            javasContainerRule.start();

            loadMavenJarProjectInGitRepo(this.gitRepoRule);

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
                "      sh 'mvn package verify org.codehaus.mojo:findbugs-maven-plugin:check'\n" +
                "    }\n" +
                "}";
            // @formatter:on

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "findbugs");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing findbugs warnings", build);
            jenkinsRule.assertLogContains("Successfully processed file 'target/findbugsXml.xml'", build);
            List<ResultAction> resultActions = build.getActions(ResultAction.class);
            assertThat(resultActions).hasSize(5);
            ResultAction resultAction = resultActions.get(4);
            assertThat(resultAction.getProjectActions()).hasSize(1);
            assertThat(resultAction.getQualityGateResult()).isNotNull();
            assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
        }
    }

    @Test
    public void maven_build_jar_with_pmd_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify pmd:pmd'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify pmd:pmd'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "pmd");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing pmd warnings", build);
        jenkinsRule.assertLogContains("Successfully processed file 'target/pmd.xml'", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(5);
        ResultAction resultAction = resultActions.get(4);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
    }

    @Test
    public void maven_build_jar_with_cpd_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify pmd:cpd'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify pmd:cpd'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "cpd");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing cpd warnings", build);
        jenkinsRule.assertLogContains("Successfully processed file 'target/cpd.xml'", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(5);
        ResultAction resultAction = resultActions.get(4);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(0);
    }

    @Test
    public void maven_build_jar_with_checkstyle_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify checkstyle:checkstyle'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify checkstyle:checkstyle'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "checkstyle");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] warningsPublisher - Processing checkstyle warnings", build);
        jenkinsRule.assertLogContains("Successfully processed file 'target/checkstyle-result.xml'", build);
        List<ResultAction> resultActions = build.getActions(ResultAction.class);
        assertThat(resultActions).hasSize(5);
        ResultAction resultAction = resultActions.get(4);
        assertThat(resultAction.getProjectActions()).hasSize(1);
        assertThat(resultAction.getQualityGateResult()).isNotNull();
        assertThat(resultAction.getResult().getTotalSize()).isEqualTo(5);
    }
}
