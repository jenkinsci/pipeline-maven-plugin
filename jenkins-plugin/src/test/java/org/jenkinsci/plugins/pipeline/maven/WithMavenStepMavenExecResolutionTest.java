/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.DownloadService;
import hudson.model.Result;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tools.InstallSourceProperty;
import org.jenkinsci.plugins.pipeline.maven.docker.JavaGitContainer;
import org.jenkinsci.plugins.pipeline.maven.docker.MavenWithMavenHomeJavaContainer;
import org.jenkinsci.plugins.pipeline.maven.docker.NonMavenJavaContainer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.test.acceptance.docker.fixtures.SshdContainer;
import org.jenkinsci.utils.process.CommandBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@Issue("JENKINS-43651")
public class WithMavenStepMavenExecResolutionTest extends AbstractIntegrationTest {

    private static final String SSH_CREDENTIALS_ID = "test";
    private static final String AGENT_NAME = "remote";
    private static final String MAVEN_GLOBAL_TOOL_NAME = "maven";
    private static final String SLAVE_BASE_PATH = "/home/test/slave";

    @Rule
    public DockerRule<NonMavenJavaContainer> nonMavenContainerRule = new DockerRule<>(NonMavenJavaContainer.class);

    @Rule
    public DockerRule<JavaGitContainer> mavenWithoutMavenHomeContainerRule = new DockerRule<>(JavaGitContainer.class);

    @Rule
    public DockerRule<MavenWithMavenHomeJavaContainer> mavenWithMavenHomeContainerRule = new DockerRule<>(MavenWithMavenHomeJavaContainer.class);

    @Test
    public void testMavenNotInstalledInDockerImage() throws Exception {
        assertThat(nonMavenContainerRule.get().popen(new CommandBuilder("mvn", "--version")).asText(), containsString("sh: 1: mvn: not found"));
    }

    @Test
    public void testMavenGlobalToolRecognizedInScriptedPipeline() throws Exception {
        registerAgentForContainer(nonMavenContainerRule.get());
        String version = registerLatestMavenVersionAsGlobalTool();

        WorkflowRun run = runPipeline("" +
                "node('" + AGENT_NAME + "') {\n" +
                "  def mavenHome = tool '" + MAVEN_GLOBAL_TOOL_NAME + "'\n" +
                "  withEnv([\"MAVEN_HOME=${mavenHome}\"]) {\n" +
                "    withMaven() {\n" +
                "      sh \"mvn --version\"\n" +
                "    }\n" +
                "  }\n" +
                "}");

        jenkinsRule.assertLogContains("Apache Maven " + version, run);
        jenkinsRule.assertLogContains("using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/home/test/slave", run);
    }

    @Test
    public void testMavenGlobalToolRecognizedInDeclarativePipeline() throws Exception {
        registerAgentForContainer(nonMavenContainerRule.get());
        String version = registerLatestMavenVersionAsGlobalTool();

        WorkflowRun run = runPipeline("" +
                "pipeline {\n" +
                "  agent { label '" + AGENT_NAME + "' }\n" +
                "  tools {\n" +
                "    maven '" + MAVEN_GLOBAL_TOOL_NAME + "'\n" +
                "  }\n" +
                "  stages {\n" +
                "    stage('Build') {\n" +
                "      steps {\n" +
                "        withMaven() {\n" +
                "          sh \"mvn --version\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}");

        jenkinsRule.assertLogContains("Apache Maven " + version, run);
        jenkinsRule.assertLogContains("using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/home/test/slave", run);
    }

    @Test
    public void testPreInstalledMavenRecognizedWithoutMavenHome() throws Exception {
        registerAgentForContainer(mavenWithoutMavenHomeContainerRule.get());

        WorkflowRun run = runPipeline("" +
                "node('" + AGENT_NAME + "') {\n" +
                "  withMaven() {\n" +
                "    sh \"mvn --version\"\n" +
                "  }\n" +
                "}");

        jenkinsRule.assertLogContains("Apache Maven 3.6.0", run);
        jenkinsRule.assertLogContains("using Maven installation provided by the build agent with executable /usr/bin/mvn", run);
    }

    @Test
    public void testPreInstalledMavenRecognizedWithMavenHome() throws Exception {
        registerAgentForContainer(mavenWithMavenHomeContainerRule.get());

        WorkflowRun run = runPipeline("" +
                "node('" + AGENT_NAME + "') {\n" +
                "  sh 'echo $MAVEN_HOME'\n" +
                "  withMaven() {\n" +
                "    sh \"mvn --version\"\n" +
                "  }\n" +
                "}");

        jenkinsRule.assertLogContains("Apache Maven 3.6.0", run);
        jenkinsRule.assertLogContains("using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/usr/share/maven", run);
    }

    private WorkflowRun runPipeline(String pipelineScript) throws Exception {
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "project");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        return jenkinsRule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
    }

    private void registerAgentForContainer(SshdContainer slaveContainer) throws Exception {
        addTestSshCredentials();
        registerAgentForSlaveContainer(slaveContainer);
    }

    private void addTestSshCredentials() {
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, SSH_CREDENTIALS_ID, null, "test", "test");

        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }

    private void registerAgentForSlaveContainer(SshdContainer slaveContainer) throws Exception {
        SSHLauncher sshLauncher = new SSHLauncher(slaveContainer.ipBound(22), slaveContainer.port(22), SSH_CREDENTIALS_ID);

        DumbSlave agent = new DumbSlave(AGENT_NAME, SLAVE_BASE_PATH, sshLauncher);
        agent.setNumExecutors(1);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);

        jenkinsRule.jenkins.addNode(agent);
    }

    private String registerLatestMavenVersionAsGlobalTool() throws Exception {
        updateAvailableMavenVersions();
        String latestMavenVersion = getLatestMavenVersion();

        registerMavenVersionAsGlobalTool(latestMavenVersion);

        return latestMavenVersion;
    }

    private void updateAvailableMavenVersions() throws Exception {
        getMavenDownloadable().updateNow();
    }

    private String getLatestMavenVersion() throws Exception {
        return getMavenDownloadable().getData().getJSONArray("list").getJSONObject(0).getString("id");
    }

    private DownloadService.Downloadable getMavenDownloadable() {
        return DownloadService.Downloadable.get(Maven.MavenInstaller.class);
    }

    private void registerMavenVersionAsGlobalTool(String version) throws Exception {
        String mavenHome = "maven-" + version.replace(".", "-");

        InstallSourceProperty installSourceProperty = new InstallSourceProperty(Collections.singletonList(new Maven.MavenInstaller(version)));
        MavenInstallation mavenInstallation = new MavenInstallation(MAVEN_GLOBAL_TOOL_NAME, mavenHome, Collections.singletonList(installSourceProperty));
        jenkinsRule.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
    }

}
