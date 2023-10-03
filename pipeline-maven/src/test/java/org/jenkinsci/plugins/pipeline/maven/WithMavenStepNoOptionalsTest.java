package org.jenkinsci.plugins.pipeline.maven;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.tasks.Maven;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

// Migrate to full JUnit5 impossible because of RealJenkinsRule
public class WithMavenStepNoOptionalsTest {

    @ClassRule
    public static InboundAgentRule agentRule = new InboundAgentRule();

    @Rule
    public GitSampleRepoRule gitRepoRule = new GitSampleRepoRule();

    @Rule
    public RealJenkinsRule jenkinsRule = new RealJenkinsRule()
            .omitPlugins(
                    "commons-lang3-api",
                    "mysql-api",
                    "postgresql-api",
                    "maven-plugin",
                    "flaky-test-handler",
                    "htmlpublisher",
                    "jacoco",
                    "jgiven",
                    "junit",
                    "junit-attachments",
                    "matrix-project",
                    "maven-invoker-plugin",
                    "pipeline-build-step",
                    "findbugs",
                    "tasks");

    @Test
    public void maven_build_jar_project_on_master_succeeds() throws Throwable {
        loadMavenJarProjectInGitRepo(gitRepoRule);
        jenkinsRule
                .extraEnv(
                        "MAVEN_ZIP_PATH",
                        Paths.get("target", "apache-maven-3.6.3-bin.zip")
                                .toAbsolutePath()
                                .toString())
                .extraEnv("MAVEN_VERSION", "3.6.3")
                .then(WithMavenStepNoOptionalsTest::setup, new Build(gitRepoRule.toString()));
    }

    private static class Build implements RealJenkinsRule.Step {

        private final String repoUrl;

        Build(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            // @formatter:off
            String pipelineScript = "node('mock') {\n" + "    git($/"
                    + repoUrl + "/$)\n" + "    withMaven() {\n"
                    + "        if (isUnix()) {\n"
                    + "            sh 'mvn verify -Dmaven.test.failure.ignore=true'\n"
                    + "        } else {\n"
                    + "            bat 'mvn verify -Dmaven.test.failure.ignore=true'\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            // @formatter:on

            WorkflowJob pipeline = r.createProject(WorkflowJob.class, "build-on-master");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = r.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            r.assertLogContains("BUILD SUCCESS", build);
        }
    }

    private static void setup(final JenkinsRule r) throws Throwable {
        Slave agent = agentRule.createAgent(r, "mock");
        r.waitOnline(agent);

        String mavenVersion = "3.6.3";
        FilePath buildDirectory =
                agent.getRootPath(); // No need of MasterToSlaveCallable because agent is a dumb, thus sharing file
        // system with controller
        FilePath mvnHome = buildDirectory.child("apache-maven-" + mavenVersion);
        FilePath mvn = buildDirectory.createTempFile("maven", "zip");
        mvn.copyFrom(new URL("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/" + mavenVersion
                + "/apache-maven-" + mavenVersion + "-bin.tar.gz"));
        mvn.untar(buildDirectory, FilePath.TarCompression.GZIP);
        Maven.MavenInstallation mavenInstallation =
                new Maven.MavenInstallation("default", mvnHome.getRemote(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);

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

    private void loadMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        gitRepo.init();
        Path mavenProjectRoot = Paths.get(WithMavenStepOnMasterTest.class
                .getResource("/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_project/")
                .toURI());
        if (!Files.exists(mavenProjectRoot)) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot + "' not found");
        }
        GitSampleRepoRuleUtils.addFilesAndCommit(mavenProjectRoot, gitRepo);
    }
}
