package org.jenkinsci.plugins.pipeline.maven;

import hudson.model.Result;
import hudson.model.Slave;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.MavenUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

@WithGitSampleRepo
class WithMavenStepNoOptionalsTest {

    private static final String STATIC_AGENT_NAME = "mock";

    @RegisterExtension
    private final InboundAgentExtension agentRule = new InboundAgentExtension();

    @RegisterExtension
    private final RealJenkinsExtension jenkins = new RealJenkinsExtension()
            .omitPlugins(
                    "coverage",
                    "findbugs",
                    "flaky-test-handler",
                    "htmlpublisher",
                    "jacoco",
                    "jgiven",
                    "junit",
                    "junit-attachments",
                    "matrix-project",
                    "maven-invoker-plugin",
                    "maven-plugin",
                    "mysql-api",
                    "pipeline-build-step",
                    "postgresql-api",
                    "tasks");

    @Test
    void maven_build_jar_project_on_master_succeeds(GitSampleRepoRule gitRepoRule) throws Throwable {
        try {
            loadMavenJarProjectInGitRepo(gitRepoRule);
            jenkins.extraEnv(
                            "MAVEN_ZIP_PATH",
                            Paths.get("target", "apache-maven-" + MavenUtil.MAVEN_VERSION + "-bin.zip")
                                    .toAbsolutePath()
                                    .toString())
                    .extraEnv("MAVEN_VERSION", MavenUtil.MAVEN_VERSION)
                    .startJenkins();
            InboundAgentExtension.Options.Builder options =
                    InboundAgentExtension.Options.newBuilder().name(STATIC_AGENT_NAME);
            agentRule.createAgent(jenkins, options.build());
            jenkins.runRemotely(new Setup(), new Build(gitRepoRule.toString()));
        } finally {
            agentRule.stop(jenkins, STATIC_AGENT_NAME);
        }
    }

    private static class Setup implements RealJenkinsExtension.Step {

        private static final long serialVersionUID = -1267837785062116155L;

        @Override
        public void run(JenkinsRule r) throws Throwable {
            Slave agent = (Slave) r.jenkins.getNode(STATIC_AGENT_NAME);
            r.waitOnline(agent);
            MavenUtil.configureDefaultMaven(agent.getRootPath());

            GlobalMavenConfig globalMavenConfig = r.get(GlobalMavenConfig.class);
            globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
            globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());

            List<MavenPublisher> options = new ArrayList<>();
            PipelineGraphPublisher graphPublisher = new PipelineGraphPublisher();
            graphPublisher.setLifecycleThreshold("package");
            options.add(graphPublisher);
            GlobalPipelineMavenConfig pipelineConfig = r.get(GlobalPipelineMavenConfig.class);
            pipelineConfig.setPublisherOptions(options);
        }
    }

    private static class Build implements RealJenkinsExtension.Step {

        private static final long serialVersionUID = 8694503255595143205L;

        private final String repoUrl;

        Build(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            // @formatter:off
            String pipelineScript = "node('mock') {\n" +
                "    git($/" + repoUrl + "/$)\n" +
                "    withMaven() {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn verify -Dmaven.test.failure.ignore=true'\n" +
                "        } else {\n" +
                "            bat 'mvn verify -Dmaven.test.failure.ignore=true'\n" +
                "        }\n" +
                "    }\n" +
                "}";
            // @formatter:on

            WorkflowJob pipeline = r.createProject(WorkflowJob.class, "build-on-master");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = r.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            r.assertLogContains("BUILD SUCCESS", build);
        }
    }

    private void loadMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        gitRepo.init();
        Path mavenProjectRoot = Paths.get(WithMavenStepNoOptionalsTest.class
                .getResource("/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_project/")
                .toURI());
        if (!Files.exists(mavenProjectRoot)) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot + "' not found");
        }
        GitSampleRepoRuleUtils.addFilesAndCommit(mavenProjectRoot, gitRepo);
    }
}
