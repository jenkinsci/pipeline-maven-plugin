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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.JDK;
import hudson.model.Result;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tools.ToolLocationNodeProperty;

@Testcontainers(disabledWithoutDocker = true)
public class WithMavenStepTest extends AbstractIntegrationTest {

    private static final String SSH_CREDENTIALS_ID = "test";
    private static final String AGENT_NAME = "remote";
    private static final String SLAVE_BASE_PATH = "/home/test/slave";
    private static final String COMMONS_LANG3_FINGERPRINT = "780b5a8b72eebe6d0dbff1c11b5658fa";

    @Issue("SECURITY-441")
    @Test
    public void testMavenBuildOnRemoteAgentWithSettingsFileOnMasterFails() throws Exception {
        try (GenericContainer<?> mavenContainerRule = new GenericContainer<>("localhost/pipeline-maven/java-maven-git").withExposedPorts(22)) {
            mavenContainerRule.start();
            registerAgentForContainer(mavenContainerRule);

            File onMaster = new File(jenkinsRule.jenkins.getRootDir(), "onmaster");
            String secret = "secret content on master";
            FileUtils.writeStringToFile(onMaster, secret, StandardCharsets.UTF_8);

            //@formatter:off
            WorkflowRun run = runPipeline(Result.FAILURE, "" +
                "node('remote') {\n" +
                "  withMaven(mavenSettingsFilePath: '" + onMaster + "') {\n" +
                "    echo readFile(MVN_SETTINGS)\n" +
                "  }\n" +
                "}");
            //@formatter:on

            jenkinsRule.assertLogNotContains(secret, run);
        }
    }

    @Test
    public void testDisableAllPublishers() throws Exception {
        try (GenericContainer<?> mavenContainerRule = new GenericContainer<>("localhost/pipeline-maven/java-maven-git").withExposedPorts(22)) {
            mavenContainerRule.start();
            registerAgentForContainer(mavenContainerRule);
            loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

            //@formatter:off
            runPipeline(Result.SUCCESS, "" +
                "node() {\n" +
                "  git($/" + gitRepoRule.toString() + "/$)\n" +
                "  withMaven(publisherStrategy: 'EXPLICIT') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}");
            //@formatter:on

            assertFingerprintDoesNotExist(COMMONS_LANG3_FINGERPRINT);
        }
    }

    // the jdk path is configured in Dockerfile
    private static Stream<Arguments> jdkMapProvider() {
        return Stream.of(arguments("jdk8", "/opt/java/jdk8"), arguments("jdk11", "/opt/java/jdk11"));
    }

    @ParameterizedTest
    @MethodSource("jdkMapProvider")
    @Issue("JENKINS-71949")
    public void tesWithDifferentJavasForBuild(String jdkName, String jdkPath) throws Exception {
        try (GenericContainer<?> javasContainerRule = new GenericContainer<>("localhost/pipeline-maven/javas").withExposedPorts(22)) {
            javasContainerRule.start();
            loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);
            String gitRepoPath = this.gitRepoRule.toString();
            javasContainerRule.copyFileToContainer(MountableFile.forHostPath(gitRepoPath), "/tmp/gitrepo");
            javasContainerRule.execInContainer("chmod", "-R", "777", "/tmp/gitrepo");
            registerAgentForContainer(javasContainerRule);
            ToolLocationNodeProperty.ToolLocation toolLocation = new ToolLocationNodeProperty.ToolLocation(new JDK.DescriptorImpl(), jdkName, jdkPath);
            ToolLocationNodeProperty toolLocationNodeProperty = new ToolLocationNodeProperty(toolLocation);
            Objects.requireNonNull(jenkinsRule.jenkins.getNode(AGENT_NAME)).getNodeProperties().add(toolLocationNodeProperty);

            jenkinsRule.jenkins.getJDKs().add(new JDK(jdkName, jdkPath));

            //@formatter:off
            WorkflowRun run = runPipeline(Result.SUCCESS,
                "node('" + AGENT_NAME + "') {\n" +
                "  git('/tmp/gitrepo')\n" +
                "  withMaven(jdk: '" + jdkName + "') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}");
            //@formatter:on
            jenkinsRule.assertLogContains("artifactsPublisher - Archive artifact target/mono-dependency-maven-project-0.1-SNAPSHOT.jar", run);

            Collection<String> archives = run.pickArtifactManager().root().list("**/**.jar", "", true);
            assertThat(archives).hasSize(1);
            assertThat(archives.iterator().next().endsWith("mono-dependency-maven-project-0.1-SNAPSHOT.jar")).isTrue();
        }
    }

    private WorkflowRun runPipeline(Result expectedResult, String pipelineScript) throws Exception {
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "project");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        return jenkinsRule.assertBuildStatus(expectedResult, p.scheduleBuild2(0));
    }

    private void assertFingerprintDoesNotExist(String fingerprintAsString) throws Exception {
        FingerprintMap fingerprintMap = jenkinsRule.jenkins.getFingerprintMap();
        Fingerprint fingerprint = fingerprintMap.get(fingerprintAsString);
        assertThat(fingerprint).isNull();
    }

    private void registerAgentForContainer(GenericContainer<?> container) throws Exception {
        addTestSshCredentials();
        registerAgentForSlaveContainer(container);
    }

    private void addTestSshCredentials() {
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, SSH_CREDENTIALS_ID, null, "test", "test");

        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(), Collections.singletonList(credentials));
    }

    private void registerAgentForSlaveContainer(GenericContainer<?> slaveContainer) throws Exception {
        SSHLauncher sshLauncher = new SSHLauncher(slaveContainer.getHost(), slaveContainer.getMappedPort(22), SSH_CREDENTIALS_ID);

        DumbSlave agent = new DumbSlave(AGENT_NAME, SLAVE_BASE_PATH, sshLauncher);
        agent.setNumExecutors(1);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);
        jenkinsRule.jenkins.addNode(agent);
    }

}
