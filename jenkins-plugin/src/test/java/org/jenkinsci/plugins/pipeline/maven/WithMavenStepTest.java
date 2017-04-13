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

import hudson.model.Node;
import hudson.model.Result;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Maven;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.maven.docker.JavaGitContainer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtendedToolInstallations;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;

public class WithMavenStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Rule
    public GitSampleRepoRule gitRepoRule = new GitSampleRepoRule();

    @Rule
    public DockerRule<JavaGitContainer> slaveRule = new DockerRule<>(JavaGitContainer.class);

    private String mavenInstallationName;

    @Before
    public void setup() throws Exception {
        // Maven.MavenInstallation maven3 = ToolInstallations.configureMaven35();
        Maven.MavenInstallation maven3 = ExtendedToolInstallations.configureMaven35();

        mavenInstallationName = maven3.getName();

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());


        JavaGitContainer slaveContainer = slaveRule.get();

        DumbSlave agent = new DumbSlave("remote", "", "/home/test/slave", "1", Node.Mode.NORMAL, "",
                new SSHLauncher(slaveContainer.ipBound(22), slaveContainer.port(22), "test", "test", "", ""),
                RetentionStrategy.INSTANCE, Collections.<NodeProperty<?>>emptyList());
        jenkinsRule.jenkins.addNode(agent);
    }

    @Issue("SECURITY-441")
    @Test
    public void maven_build_on_remote_agent_with_settings_file_on_master_fails() throws Exception {
        File onMaster = new File(jenkinsRule.jenkins.getRootDir(), "onmaster");
        String secret = "secret content on master";
        FileUtils.writeStringToFile(onMaster, secret);
        String pipelineScript = "node('remote') {withMaven(mavenSettingsFilePath: '" + onMaster + "') {echo readFile(MVN_SETTINGS)}}";

        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun b = jenkinsRule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkinsRule.assertLogNotContains(secret, b);
    }
}
