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
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Result;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;

public class WithMavenStepTest extends AbstractIntegrationTest {

    private static final String SSH_CREDENTIALS_ID = "test";
    private static final String AGENT_NAME = "remote";
    private static final String SLAVE_BASE_PATH = "/home/test/slave";
    private static final String COMMONS_LANG3_FINGERPRINT = "780b5a8b72eebe6d0dbff1c11b5658fa";

    @Issue("SECURITY-441")
    @Test
    public void testMavenBuildOnRemoteAgentWithSettingsFileOnMasterFails() throws Exception {
        registerAgentForContainer(javaGitContainerRule);

        File onMaster = new File(jenkinsRule.jenkins.getRootDir(), "onmaster");
        String secret = "secret content on master";
        FileUtils.writeStringToFile(onMaster, secret, StandardCharsets.UTF_8);

        WorkflowRun run = runPipeline(Result.FAILURE, "" +
                "node('remote') {\n" +
                "  withMaven(mavenSettingsFilePath: '" + onMaster + "') {\n" +
                "    echo readFile(MVN_SETTINGS)\n" +
                "  }\n" +
                "}");

        jenkinsRule.assertLogNotContains(secret, run);
    }

    @Test
    public void testDisableAllPublishers() throws Exception {
        registerAgentForContainer(javaGitContainerRule);
        loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

        runPipeline(Result.SUCCESS, "" +
                "node() {\n" +
                "  git($/" + gitRepoRule.toString() + "/$)\n" +
                "  withMaven(publisherStrategy: 'EXPLICIT') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}");

        assertFingerprintDoesNotExist(COMMONS_LANG3_FINGERPRINT);
    }

    private WorkflowRun runPipeline(Result expectedResult, String pipelineScript) throws Exception {
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "project");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        return jenkinsRule.assertBuildStatus(expectedResult, p.scheduleBuild2(0));
    }

    private void assertFingerprintDoesNotExist(String fingerprintAsString) throws Exception {
        FingerprintMap fingerprintMap = jenkinsRule.jenkins.getFingerprintMap();
        Fingerprint fingerprint = fingerprintMap.get(fingerprintAsString);
        assertThat(fingerprint, Matchers.nullValue());
    }

    private void registerAgentForContainer(GenericContainer<?> container) throws Exception {
        addTestSshCredentials();
        registerAgentForSlaveContainer(container);
    }

    private void addTestSshCredentials() {
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, SSH_CREDENTIALS_ID, null, "test", "test");

        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }

    private void registerAgentForSlaveContainer(GenericContainer<?> slaveContainer) throws Exception {
        SSHLauncher sshLauncher = new SSHLauncher(slaveContainer.getHost(), slaveContainer.getMappedPort(22), SSH_CREDENTIALS_ID);

        DumbSlave agent = new DumbSlave(AGENT_NAME, SLAVE_BASE_PATH, sshLauncher);
        agent.setNumExecutors(1);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);

        jenkinsRule.jenkins.addNode(agent);
    }

}
