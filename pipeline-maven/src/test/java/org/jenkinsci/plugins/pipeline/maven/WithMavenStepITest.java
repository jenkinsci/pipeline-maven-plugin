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

import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.JDK;
import hudson.model.Result;
import hudson.tasks.Maven;
import hudson.tools.ToolLocationNodeProperty;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.maven.util.MavenUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public class WithMavenStepITest extends AbstractIntegrationTest {

    private static final String COMMONS_LANG3_FINGERPRINT = "780b5a8b72eebe6d0dbff1c11b5658fa";

    @Test
    public void configRoundTrip() throws Exception {
        WithMavenStep step1 = new WithMavenStep();
        step1.setMaven("someMaven");
        step1.setJdk("someJdk");
        step1.setPublisherStrategy(MavenPublisherStrategy.EXPLICIT);
        step1.setMavenLocalRepo("aLocalRepo");
        step1.setGlobalMavenSettingsConfig("");
        step1.setGlobalMavenSettingsFilePath("globalSettingsFilePath");
        step1.setMavenOpts("someMavenOpts");
        step1.setMavenSettingsConfig("");
        step1.setMavenSettingsFilePath("settingsFilePath");
        step1.setTempBinDir("aTmpDir");
        step1.setTraceability(true);
        jenkinsRule.getInstance().setJDKs(List.of(new JDK("someJdk", "somePath")));
        MavenUtil.configureMaven(jenkinsRule.getInstance().getRootPath(), MavenUtil.MAVEN_VERSION, "someMaven");

        WithMavenStep step2 = new StepConfigTester(jenkinsRule).configRoundTrip(step1);
        jenkinsRule.assertEqualDataBoundBeans(step1, step2);
    }

    @Issue("SECURITY-441")
    @Test
    public void testMavenBuildOnRemoteAgentWithSettingsFileOnMasterFails() throws Exception {
        try (GenericContainer<?> mavenContainerRule = createContainer("java-maven-git")) {
            mavenContainerRule.start();
            registerAgentForContainer(mavenContainerRule);

            File onMaster = new File(jenkinsRule.jenkins.getRootDir(), "onmaster");
            String secret = "secret content on master";
            FileUtils.writeStringToFile(onMaster, secret, StandardCharsets.UTF_8);

            // @formatter:off
            WorkflowRun run = runPipeline(
                Result.FAILURE,
                "node('remote') {\n" +
                "  withMaven(mavenSettingsFilePath: '" + onMaster + "') {\n" +
                "    echo readFile(MVN_SETTINGS)\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            jenkinsRule.assertLogNotContains(secret, run);
        }
    }

    @Test
    public void testDisableAllPublishers() throws Exception {
        try (GenericContainer<?> mavenContainerRule = createContainer("java-maven-git")) {
            mavenContainerRule.start();
            registerAgentForContainer(mavenContainerRule);
            loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

            // @formatter:off
            runPipeline(
                Result.SUCCESS,
                "node() {\n" +
                "  git($/" + gitRepoRule.toString() + "/$)\n" +
                "  withMaven(publisherStrategy: 'EXPLICIT') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on

            assertFingerprintDoesNotExist(COMMONS_LANG3_FINGERPRINT);
        }
    }

    // the jdk path is configured in Dockerfile
    private static Stream<Arguments> jdkMapProvider() {
        return Stream.of(
                arguments("jdk8", "/opt/java/jdk8"),
                arguments("jdk11", "/opt/java/jdk11"),
                arguments("jdk17", "/opt/java/jdk17"),
                arguments("jdk21", "/opt/java/jdk21"),
                arguments("jdk25", "/opt/java/jdk25"));
    }

    @ParameterizedTest
    @MethodSource("jdkMapProvider")
    @Issue("JENKINS-71949")
    public void tesWithDifferentJavasForBuild(String jdkName, String jdkPath) throws Exception {
        try (GenericContainer<?> javasContainerRule = createContainer("javas")) {
            javasContainerRule.start();
            loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);
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
            WorkflowRun run = runPipeline(
                Result.SUCCESS,
                "node('" + AGENT_NAME + "') {\n" +
                "  git('" + containerPath + "')\n" +
                "  withMaven(jdk: '" + jdkName + "') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}"
            );
            // @formatter:on
            jenkinsRule.assertLogContains(
                    "artifactsPublisher - Archive artifact target/mono-dependency-maven-project-0.1-SNAPSHOT.jar", run);

            Collection<String> archives = run.pickArtifactManager().root().list("**/**.jar", "", true);
            assertThat(archives).hasSize(1);
            assertThat(archives.iterator().next().endsWith("mono-dependency-maven-project-0.1-SNAPSHOT.jar"))
                    .isTrue();
            jenkinsRule.assertLogNotContains(
                    "[withMaven] WARNING: You are running an old version of Maven (UNKNOWN), you should update to at least 3.8.x",
                    run);
        }
    }

    private static Stream<Arguments> mavenMapProvider() {
        return Stream.of(
                arguments("3.8.8", false),
                arguments("3.6.3", true),
                arguments("3.5.4", true),
                arguments("3.3.9", true));
    }

    @ParameterizedTest
    @MethodSource("mavenMapProvider")
    public void tesWithDifferentMavensForBuild(String mavenVersion, boolean shouldWarn) throws Exception {
        Maven.MavenInstallation mvn =
                MavenUtil.configureMaven(Jenkins.get().getRootPath(), mavenVersion, "apache-maven-" + mavenVersion);
        loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        WorkflowRun run = runPipeline(
                Result.SUCCESS,
                "node() {\n" +
                "  git($/" + gitRepoRule.toString() + "/$)\n" +
                "  withMaven(maven: '" + mvn.getName() + "') {\n" +
                "    sh 'mvn package'\n" +
                "  }\n" +
                "}"
            );
        // @formatter:on

        if (shouldWarn) {
            jenkinsRule.assertLogContains(
                    "[withMaven] WARNING: You are running an old version of Maven (" + mavenVersion
                            + "), you should update to at least 3.8.x",
                    run);
        }
        jenkinsRule.assertLogContains(
                "artifactsPublisher - Archive artifact target/mono-dependency-maven-project-0.1-SNAPSHOT.jar", run);

        Collection<String> archives = run.pickArtifactManager().root().list("**/**.jar", "", true);
        assertThat(archives).hasSize(1);
        assertThat(archives.iterator().next().endsWith("mono-dependency-maven-project-0.1-SNAPSHOT.jar"))
                .isTrue();
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
}
