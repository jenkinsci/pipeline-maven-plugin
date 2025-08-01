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

import hudson.model.DownloadService;
import hudson.model.Result;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tools.InstallSourceProperty;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@Issue("JENKINS-43651")
public class WithMavenStepMavenExecResolutionTest extends AbstractIntegrationTest {

    private static final String MAVEN_GLOBAL_TOOL_NAME = "maven";

    @Test
    public void testMavenNotInstalledInDockerImage() throws Exception {
        try (GenericContainer<?> nonMavenContainerRule = createContainer("java-git")) {
            nonMavenContainerRule.start();
            assertThat(nonMavenContainerRule.execInContainer("mvn", "--version").getStdout())
                    .contains("exec: \"mvn\": executable file not found in $PATH");
        }
    }

    @Test
    public void testMavenGlobalToolRecognizedInScriptedPipeline() throws Exception {
        try (GenericContainer<?> nonMavenContainerRule = createContainer("java-git")) {
            nonMavenContainerRule.start();
            registerAgentForContainer(nonMavenContainerRule);
            String version = registerLatestMavenVersionAsGlobalTool();

            // @formatter:off
            WorkflowRun run = runPipeline(
                "node('" + AGENT_NAME + "') {\n" +
                "  def mavenHome = tool '" + MAVEN_GLOBAL_TOOL_NAME + "'\n" +
                "  withEnv([\"MAVEN_HOME=${mavenHome}\"]) {\n" +
                "    withMaven(traceability: true) {\n" +
                "      sh \"mvn --version\"\n" +
                "    }\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            jenkinsRule.assertLogContains("Apache Maven " + version, run);
            jenkinsRule.assertLogContains(
                    "using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/home/test/slave",
                    run);
        }
    }

    @Test
    public void testMavenGlobalToolRecognizedInDeclarativePipeline() throws Exception {
        try (GenericContainer<?> nonMavenContainerRule = createContainer("java-git")) {
            nonMavenContainerRule.start();
            registerAgentForContainer(nonMavenContainerRule);
            String version = registerLatestMavenVersionAsGlobalTool();

            // @formatter:off
            WorkflowRun run = runPipeline(
                "pipeline {\n" +
                "  agent { label '" + AGENT_NAME + "' }\n" +
                "  tools {\n" +
                "    maven '" +MAVEN_GLOBAL_TOOL_NAME + "'\n" +
                "  }\n" +
                "  stages {\n" +
                "    stage('Build') {\n" +
                "      steps {\n" +
                "        withMaven(traceability: true) {\n" +
                "          sh \"mvn --version\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            jenkinsRule.assertLogContains("Apache Maven " + version, run);
            jenkinsRule.assertLogContains(
                    "using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/home/test/slave",
                    run);
        }
    }

    @Test
    public void testPreInstalledMavenRecognizedWithoutMavenHome() throws Exception {
        try (GenericContainer<?> javaMavenGitContainerRule = createContainer("java-maven-git")) {
            javaMavenGitContainerRule.start();
            registerAgentForContainer(javaMavenGitContainerRule);

            // @formatter:off
            WorkflowRun run = runPipeline(
                "node('" + AGENT_NAME + "') {\n" +
                "  withMaven(traceability: true) {\n" +
                "    sh \"mvn --version\"\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            jenkinsRule.assertLogContains("Apache Maven 3.8.7", run);
            jenkinsRule.assertLogContains(
                    "using Maven installation provided by the build agent with executable /usr/bin/mvn", run);
        }
    }

    @Test
    public void testPreInstalledMavenRecognizedWithMavenHome() throws Exception {
        try (GenericContainer<?> mavenWithMavenHomeContainerRule = createContainer("maven-home")) {
            mavenWithMavenHomeContainerRule.start();
            registerAgentForContainer(mavenWithMavenHomeContainerRule);

            // @formatter:off
            WorkflowRun run = runPipeline(
                "node('" + AGENT_NAME + "') {\n" +
                "  sh 'echo $MAVEN_HOME'\n" +
                "  withMaven(traceability: true) {\n" +
                "    sh \"mvn --version\"\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            jenkinsRule.assertLogContains("Apache Maven 3.8.7", run);
            jenkinsRule.assertLogContains(
                    "using Maven installation provided by the build agent with the environment variable MAVEN_HOME=/usr/share/maven",
                    run);
        }
    }

    private WorkflowRun runPipeline(String pipelineScript) throws Exception {
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "project");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        return jenkinsRule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
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
        return getMavenDownloadable()
                .getData()
                .getJSONArray("list")
                .getJSONObject(0)
                .getString("id");
    }

    private DownloadService.Downloadable getMavenDownloadable() {
        return DownloadService.Downloadable.get(Maven.MavenInstaller.class);
    }

    private void registerMavenVersionAsGlobalTool(String version) throws Exception {
        String mavenHome = "maven-" + version.replace(".", "-");

        InstallSourceProperty installSourceProperty =
                new InstallSourceProperty(Collections.singletonList(new Maven.MavenInstaller(version)));
        MavenInstallation mavenInstallation = new MavenInstallation(
                MAVEN_GLOBAL_TOOL_NAME, mavenHome, Collections.singletonList(installSourceProperty));
        jenkinsRule.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
    }
}
