package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.recipes.WithPlugin;

public class SpotBugsAnalysisPublisherTest extends AbstractIntegrationTest {

    @Test
    @WithPlugin({
        "findbugs.jpi",
        "analysis-core.jpi",
        "antisamy-markup-formatter.hpi",
        "apache-httpcomponents-client-4-api.hpi",
        "asm-api.hpi",
        "bootstrap5-api.hpi",
        "caffeine-api.hpi",
        "checks-api.hpi",
        "commons-lang3-api.hpi",
        "commons-text-api.hpi",
        "credentials.hpi",
        "display-url-api.hpi",
        "durable-task.hpi",
        "echarts-api.hpi",
        "font-awesome-api.hpi",
        "ionicons-api.hpi",
        "jackson2-api.hpi",
        "jakarta-activation-api.hpi",
        "jakarta-mail-api.hpi",
        "javadoc.hpi",
        "jquery3-api.hpi",
        "jsch.hpi",
        "json-api.hpi",
        "jsoup.hpi",
        "junit.hpi",
        "mailer.hpi",
        "matrix-project.hpi",
        "maven-plugin.hpi",
        "oss-symbols-api.hpi",
        "plugin-util-api.hpi",
        "scm-api.hpi",
        "script-security.hpi",
        "snakeyaml-api.hpi",
        "ssh-credentials.hpi",
        "structs.hpi",
        "variant.hpi",
        "workflow-api.hpi",
        "workflow-step-api.hpi",
        "workflow-support.hpi"
    })
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
    @WithPlugin({
        "findbugs.jpi",
        "analysis-core.jpi",
        "antisamy-markup-formatter.hpi",
        "apache-httpcomponents-client-4-api.hpi",
        "asm-api.hpi",
        "bootstrap5-api.hpi",
        "caffeine-api.hpi",
        "checks-api.hpi",
        "commons-lang3-api.hpi",
        "commons-text-api.hpi",
        "credentials.hpi",
        "display-url-api.hpi",
        "durable-task.hpi",
        "echarts-api.hpi",
        "font-awesome-api.hpi",
        "ionicons-api.hpi",
        "jackson2-api.hpi",
        "jakarta-activation-api.hpi",
        "jakarta-mail-api.hpi",
        "javadoc.hpi",
        "jquery3-api.hpi",
        "jsch.hpi",
        "json-api.hpi",
        "jsoup.hpi",
        "junit.hpi",
        "mailer.hpi",
        "matrix-project.hpi",
        "maven-plugin.hpi",
        "oss-symbols-api.hpi",
        "plugin-util-api.hpi",
        "scm-api.hpi",
        "script-security.hpi",
        "snakeyaml-api.hpi",
        "ssh-credentials.hpi",
        "structs.hpi",
        "variant.hpi",
        "workflow-api.hpi",
        "workflow-step-api.hpi",
        "workflow-support.hpi"
    })
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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-spotbugs-explicit");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains(
                "The spotbugsPublisher is deprecated as is the findbugs plugin and you should not use it", build);
    }

    @Test
    public void maven_build_jar_without_spotbugs_success() throws Exception {
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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-without-spotbugs");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogNotContains(
                "[withMaven] SpotBugs Publisher is no longer implicitly used with `withMaven` step.", build);
    }
}
