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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.condition.OS.LINUX;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.base.Predicate;
import hudson.model.Result;
import hudson.plugins.tasks.TasksResultAction;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.pipeline.JUnitResultsStep;
import hudson.tasks.test.PipelineBlockWithTests;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider;
import org.jenkinsci.plugins.pipeline.maven.publishers.ConcordionTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.CoveragePublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesFingerprintPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.InvokerRunsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JGivenTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.SpotBugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.TasksScannerPublisher;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;

/**
 * TODO migrate to {@link WithMavenStepTest} once we have implemented a
 * GitRepoRule that can be used on remote agents
 */
public class WithMavenStepOnMasterTest extends AbstractIntegrationTest {

    Logger logger;
    Level savedLevel;

    @BeforeEach
    public void before() {
        // Many log messages checked here are not logged if we are not in FINE level.
        logger = Logger.getLogger(WithMavenStepExecution2.class.getName());
        savedLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
    }

    @Override
    @AfterEach
    public void after() {
        logger.setLevel(savedLevel);
    }

    @Test
    public void maven_build_on_master_with_specified_maven_installation_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package'\n" +
            "        } else {\n" +
            "            bat 'mvn package'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-tool-provided-maven");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify provided Maven is used
        jenkinsRule.assertLogContains("using Maven installation 'apache-maven-3'", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under
        // jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom",
                build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar",
                build);
    }

    @Test
    public void maven_build_on_master_with_missing_specified_maven_installation_fails() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(maven: 'install-does-not-exist') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package'\n" +
            "        } else {\n" +
            "            bat 'mvn package'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-tool-provided-maven");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
    }

    @Issue("JENKINS-70543")
    @Test
    public void maven_build_no_traceability() throws Exception {
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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-no-traceability");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogNotContains("[withMaven] using JDK installation", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven installation", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven settings", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven global settings", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under
        // jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom",
                build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar",
                build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames)
                .contains("mono-module-maven-app-0.1-SNAPSHOT.pom", "mono-module-maven-app-0.1-SNAPSHOT.jar");

        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom");

        // verify Junit Archiver is called for maven-surefire-plugin
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT "
                        + "generated by maven-surefire-plugin:test",
                build);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount()).isEqualTo(2);
        assertThat(testResultAction.getFailCount()).isEqualTo(0);

        // verify Junit Archiver is called for maven-failsafe-plugin
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT "
                        + "generated by maven-failsafe-plugin:integration-test",
                build);

        // verify Task Scanner is called for jenkins.mvn.test:mono-module-maven-app
        jenkinsRule.assertLogContains(
                "[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT",
                build);
        TasksResultAction tasksResultAction = build.getAction(TasksResultAction.class);
        assertThat(tasksResultAction.getProjectActions()).hasSize(1);
    }

    @Issue("JENKINS-70543")
    @Test
    public void maven_build_global_traceability() throws Exception {
        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();
        globalPipelineMavenConfig.setGlobalTraceability(true);

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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-global-traceability");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogContains("[withMaven] using JDK installation", build);
        jenkinsRule.assertLogContains("[withMaven] using Maven installation", build);
        jenkinsRule.assertLogContains("[withMaven] using Maven settings", build);
        jenkinsRule.assertLogContains("[withMaven] using Maven global settings", build);
    }

    @Issue("JENKINS-70543")
    @Test
    public void maven_build_global_traceability_disabled_for_one_step() throws Exception {
        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();
        globalPipelineMavenConfig.setGlobalTraceability(true);

        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: false) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(
                WorkflowJob.class, "build-on-master-global-traceability-disabled-for-one-step");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogNotContains("[withMaven] using JDK installation", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven installation", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven settings", build);
        jenkinsRule.assertLogNotContains("[withMaven] using Maven global settings", build);
    }

    @Test
    public void maven_build_jar_project_on_master_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package verify'\n" +
            "        } else {\n" +
            "            bat 'mvn package verify'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-jar-project");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent
        // with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under
        // jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom",
                build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar",
                build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames)
                .contains("mono-module-maven-app-0.1-SNAPSHOT.pom", "mono-module-maven-app-0.1-SNAPSHOT.jar");

        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom");

        // verify Junit Archiver is called for maven-surefire-plugin
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT "
                        + "generated by maven-surefire-plugin:test",
                build);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount()).isEqualTo(2);
        assertThat(testResultAction.getFailCount()).isEqualTo(0);

        // verify Junit Archiver is called for maven-failsafe-plugin
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT "
                        + "generated by maven-failsafe-plugin:integration-test",
                build);

        // verify Task Scanner is called for jenkins.mvn.test:mono-module-maven-app
        jenkinsRule.assertLogContains(
                "[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT",
                build);
        TasksResultAction tasksResultAction = build.getAction(TasksResultAction.class);
        assertThat(tasksResultAction.getProjectActions()).hasSize(1);
    }

    @Issue("JENKINS-48264")
    @Test
    public void maven_build_jar_project_with_whitespace_char_in_name() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'unset MAVEN_ARGS; mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'set MAVEN_ARGS= && mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}";

        String mavenSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-settings-test-through-config-file-provider</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on
        MavenSettingsConfig mavenSettingsConfig =
                new MavenSettingsConfig("maven-config-test", "maven-config-test", "", mavenSettings, false, null);

        GlobalConfigFiles.get().save(mavenSettingsConfig);
        GlobalMavenConfig.get().setSettingsProvider(new MvnSettingsProvider(mavenSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build on master with spaces");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    private static Stream<Arguments> mavenPublisherDescriptors() {
        return Stream.of(
                Arguments.of(new FindbugsAnalysisPublisher.DescriptorImpl(), "findbugsPublisher"),
                Arguments.of(new SpotBugsAnalysisPublisher.DescriptorImpl(), "spotbugsPublisher"),
                Arguments.of(new TasksScannerPublisher.DescriptorImpl(), "openTasksPublisher"),
                Arguments.of(new ConcordionTestsPublisher.DescriptorImpl(), "concordionPublisher"),
                Arguments.of(new DependenciesFingerprintPublisher.DescriptorImpl(), "dependenciesFingerprintPublisher"),
                Arguments.of(new GeneratedArtifactsPublisher.DescriptorImpl(), "artifactsPublisher"),
                Arguments.of(new InvokerRunsPublisher.DescriptorImpl(), "invokerPublisher"),
                Arguments.of(new JGivenTestsPublisher.DescriptorImpl(), "jgivenPublisher"),
                Arguments.of(new JunitTestsPublisher.DescriptorImpl(), "junitPublisher"),
                Arguments.of(new MavenLinkerPublisher2.DescriptorImpl(), "mavenLinkerPublisher"),
                Arguments.of(new PipelineGraphPublisher.DescriptorImpl(), "pipelineGraphPublisher"),
                Arguments.of(new CoveragePublisher.DescriptorImpl(), "coveragePublisher"));
    }

    @ParameterizedTest
    @MethodSource("mavenPublisherDescriptors")
    public void maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(
            MavenPublisher.DescriptorImpl descriptor, String symbol) throws Exception {
        Logger logger = Logger.getLogger(MavenSpyLogProcessor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.FINE);
        try {
            String displayName = descriptor.getDisplayName();

            Symbol symbolAnnotation = descriptor.getClass().getAnnotation(Symbol.class);
            String[] symbols = symbolAnnotation.value();
            assertThat(new String[] {symbol}).isEqualTo(symbols);

            loadMavenJarProjectInGitRepo(this.gitRepoRule);

            for (Boolean disabled : Arrays.asList(Boolean.TRUE, Boolean.FALSE)) {
                // @formatter:off
                String pipelineScript = "node() {\n" +
                    "    git($/" + gitRepoRule.toString() + "/$)\n" +
                    "    withMaven(options:[" + symbol + "(disabled:" + disabled + ")]) {\n" +
                    "        if (isUnix()) {\n" +
                    "            sh 'mvn package verify'\n" +
                    "        } else {\n" +
                    "            bat 'mvn package verify'\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";
                // @formatter:on

                WorkflowJob pipeline = jenkinsRule.createProject(
                        WorkflowJob.class, "build-on-master-" + symbol + "-publisher-disabled-" + disabled);
                pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
                WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

                String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
                if (disabled) {
                    jenkinsRule.assertLogContains(message, build);
                } else {
                    jenkinsRule.assertLogNotContains(message, build);
                }
            }
        } finally {
            logger.setLevel(level);
        }
    }

    @Test
    public void maven_build_jar_project_on_master_with_open_task_scanner_config_succeeds() throws Exception {

        MavenPublisher.DescriptorImpl descriptor = new TasksScannerPublisher.DescriptorImpl();
        String displayName = descriptor.getDisplayName();

        Symbol symbolAnnotation = descriptor.getClass().getAnnotation(Symbol.class);
        String[] symbols = symbolAnnotation.value();
        assertThat(new String[] {"openTasksPublisher"}).isEqualTo(symbols);

        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(options:[openTasksPublisher(" +
            "        disabled:false, " +
            "        pattern:'src/main/java', excludePattern:'a/path'," +
            "        ignoreCase:true, asRegexp:false, " +
            "        lowPriorityTaskIdentifiers:'minor', normalPriorityTaskIdentifiers:'todo', highPriorityTaskIdentifiers:'fixme')]) {\n" +
            "            if (isUnix()) {\n" +
            "                sh 'mvn package verify'\n" +
            "            } else {\n" +
            "                bat 'mvn package verify'\n" +
            "            }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-on-master-openTasksPublisher-publisher-config");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
        jenkinsRule.assertLogNotContains(message, build);
    }

    @Test
    public void maven_build_maven_jar_with_flatten_pom_project_on_master_succeeds() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_flatten_pom_project/");

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package'\n" +
            "        } else {\n" +
            "            bat 'mvn package'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-jar-with-flatten-pom-project-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogNotContains(
                "[jenkins-maven-event-spy] WARNING: unexpected Maven project file name '.flattened-pom.xml', problems may occur",
                build);

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent
        // with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom",
                build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar",
                build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames)
                .contains(
                        "maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom",
                        "maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar");

        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(
                pipeline,
                build,
                "jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom");

        // verify Junit Archiver is called for
        // jenkins.mvn.test:maven-jar-with-flattened-pom
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:maven-jar-with-flattened-pom:jar:0.1-SNAPSHOT generated by",
                build);

        // verify Task Scanner is called for
        // jenkins.mvn.test:maven-jar-with-flattened-pom
        jenkinsRule.assertLogContains(
                "[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:maven-jar-with-flattened-pom:jar:0.1-SNAPSHOT in source directory",
                build);
    }

    @Test
    public void maven_build_maven_hpi_project_on_master_succeeds() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_hpi_project/");

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(traceability: true, mavenLocalRepo: \"${pwd tmp: true}/m2repo\") {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn -P!might-produce-incrementals package'\n" +
            "        } else {\n" +
            "            bat 'mvn -P!might-produce-incrementals package'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-hpi-project");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent
        // with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.pom", build);

        // verify .jar and .hpi is archived and fingerprinted
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.hpi", build);
        jenkinsRule.assertLogContains(
                "under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames)
                .contains(
                        "test-jenkins-hpi-0.1-SNAPSHOT.pom",
                        "test-jenkins-hpi-0.1-SNAPSHOT.jar",
                        "test-jenkins-hpi-0.1-SNAPSHOT.hpi");

        verifyFileIsFingerPrinted(
                pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.hpi");
        verifyFileIsFingerPrinted(
                pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(
                pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.pom");

        // verify Junit Archiver is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains(
                "[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:test-jenkins-hpi:hpi:0.1-SNAPSHOT generated by",
                build);

        // verify Task Scanner is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains(
                "[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:test-jenkins-hpi:hpi:0.1-SNAPSHOT in source directory",
                build);
    }

    @Issue("JENKINS-43678")
    @Test
    public void maven_build_on_master_with_no_generated_jar_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn test'\n" +
            "        } else {\n" +
            "            bat 'mvn test'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // don't try to archive the artifact as it has not been generated
        jenkinsRule.assertLogNotContains(
                "under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar",
                build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames).contains("mono-module-maven-app-0.1-SNAPSHOT.pom");
        assertThat(build.getArtifacts()).hasSize(1);
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_global_settings_path_defined_through_jenkins_global_config() throws Exception {

        File mavenGlobalSettingsFile = new File(jenkinsRule.jenkins.getRootDir(), "maven-global-settings.xml");
        // @formatter:off
        String mavenGlobalSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-global-settings-test</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on
        FileUtils.writeStringToFile(mavenGlobalSettingsFile, mavenGlobalSettings, StandardCharsets.UTF_8);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on
        GlobalMavenConfig.get()
                .setGlobalSettingsProvider(
                        new FilePathGlobalSettingsProvider(mavenGlobalSettingsFile.getAbsolutePath()));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven global settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_global_settings_defined_through_jenkins_global_config_and_config_file_provider()
            throws Exception {

        // @formatter:off
        String mavenGlobalSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-global-settings-test-from-config-file-provider</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig(
                "maven-global-config-test", "maven-global-config-test", "", mavenGlobalSettings);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on

        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig);
        GlobalMavenConfig.get().setGlobalSettingsProvider(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class,
                    "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven global settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test-from-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
            GlobalConfigFiles.get().remove(mavenGlobalSettingsConfig.id);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_global_settings_defined_through_folder_config_and_config_file_provider() throws Exception {

        // @formatter:off
        String mavenGlobalSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "       <server>\n" +
            "           <id>id-global-settings-test-from-config-file-provider-on-a-folder</id>\n" +
            "       </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig(
                "maven-global-config-test-folder", "maven-global-config-test-folder", "", mavenGlobalSettings);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'echo %cd%'\n" +
            "            bat 'dir'\n" +
            "            bat 'set'\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on

        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig);

        Folder folder = jenkinsRule.createProject(Folder.class, "folder");
        MavenConfigFolderOverrideProperty configOverrideProperty = new MavenConfigFolderOverrideProperty();
        configOverrideProperty.setOverride(true);
        configOverrideProperty.setGlobalSettings(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig.id));
        folder.addProperty(configOverrideProperty);

        try {
            WorkflowJob pipeline = folder.createProject(
                    WorkflowJob.class,
                    "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using overridden Maven global settings by folder 'folder'. Config File Provider maven global settings file 'maven-global-config-test-folder'",
                    build);
            jenkinsRule.assertLogContains(
                    "<id>id-global-settings-test-from-config-file-provider-on-a-folder</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
            GlobalConfigFiles.get().remove(mavenGlobalSettingsConfig.id);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_global_settings_path_defined_through_pipeline_attribute() throws Exception {

        // @formatter:off
        String pipelineScript = "node () {\n" +
            "    writeFile file: 'maven-global-settings.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-global-settings-test</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>'''\n" +
            "\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3', globalMavenSettingsFilePath: 'maven-global-settings.xml') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on

        WorkflowJob pipeline = jenkinsRule.createProject(
                WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-pipeline");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains("[withMaven] using Maven global settings provided on the build agent", build);
        jenkinsRule.assertLogContains("<id>id-global-settings-test</id>", build);
    }

    @Issue("JENKINS-42565")
    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_settings_path_defined_through_pipeline_attribute() throws Exception {

        // @formatter:off
        String pipelineScript = "node () {\n" +
            "    writeFile file: 'maven-settings.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-settings-test</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>'''\n" +
            "\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3', mavenSettingsFilePath: 'maven-settings.xml') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'env && mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'set && mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on

        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-pipeline");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains("[withMaven] using Maven settings provided on the build agent", build);
        jenkinsRule.assertLogContains("<id>id-settings-test</id>", build);
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_settings_defined_through_jenkins_global_config() throws Exception {

        File mavenSettingsFile = new File(jenkinsRule.jenkins.getRootDir(), "maven-settings.xml");
        // @formatter:off
        String mavenSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-settings-test</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on
        FileUtils.writeStringToFile(mavenSettingsFile, mavenSettings, StandardCharsets.UTF_8);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on
        GlobalMavenConfig.get().setSettingsProvider(new FilePathSettingsProvider(mavenSettingsFile.getAbsolutePath()));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-jenkins-global-config");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_settings_defined_through_jenkins_global_config_and_config_file_provider() throws Exception {

        // @formatter:off
        String mavenSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-settings-test-through-config-file-provider</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on
        MavenSettingsConfig mavenSettingsConfig =
                new MavenSettingsConfig("maven-config-test", "maven-config-test", "", mavenSettings, false, null);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on
        GlobalConfigFiles.get().save(mavenSettingsConfig);
        GlobalMavenConfig.get().setSettingsProvider(new MvnSettingsProvider(mavenSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class,
                    "build-on-master-with-maven-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_settings_defined_through_folder_config_and_config_file_provider() throws Exception {

        // @formatter:off
        String mavenSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "       <server>\n" +
            "           <id>id-settings-test-through-config-file-provider-on-a-folder</id>\n" +
            "       </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on
        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig(
                "maven-config-test-folder", "maven-config-test-folder", "", mavenSettings, false, null);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on
        GlobalConfigFiles.get().save(mavenSettingsConfig);

        Folder folder = jenkinsRule.createProject(Folder.class, "folder");
        MavenConfigFolderOverrideProperty configOverrideProperty = new MavenConfigFolderOverrideProperty();
        configOverrideProperty.setOverride(true);
        GlobalMavenConfig globalMavenConfig = GlobalMavenConfig.get();
        configOverrideProperty.setGlobalSettings(globalMavenConfig.getGlobalSettingsProvider());
        configOverrideProperty.setSettings(new MvnSettingsProvider(mavenSettingsConfig.id));
        folder.addProperty(configOverrideProperty);

        try {
            WorkflowJob pipeline = folder.createProject(
                    WorkflowJob.class,
                    "build-on-master-with-maven-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using overridden Maven settings by folder 'folder'. Config File Provider maven settings file 'maven-config-test-folder'",
                    build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider-on-a-folder</id>", build);
        } finally {
            configOverrideProperty.setOverride(false);
        }
    }

    @Test
    @EnabledOnOs(value = LINUX, disabledReason = "bat step get stuck on Windows 2019 CI agents")
    public void maven_settings_defined_through_pipeline_attribute_and_config_file_provider() throws Exception {

        // @formatter:off
        String mavenSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "        <server>\n" +
            "            <id>id-settings-test-from-pipeline-attribute-and-config-file-provider</id>\n" +
            "        </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        // @formatter:on

        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig(
                "maven-config-test-from-pipeline-attribute",
                "maven-config-test-from-pipeline-attribute",
                "",
                mavenSettings,
                false,
                null);

        // @formatter:off
        String pipelineScript =
            "node () {\n" +
            "    writeFile file: 'pom.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<project\n" +
            "        xmlns='http://maven.apache.org/POM/4.0.0' \n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
            "        xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>my-artifact</artifactId>\n" +
            "    <version>1.0.0-SNAPSHOT</version>\n" +
            "    <packaging>pom</packaging>\n" +
            "</project>'''\n" +
            "\n" +
            "    withMaven(traceability: true, maven: 'apache-maven-3', mavenSettingsConfig: 'maven-config-test-from-pipeline-attribute') {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn help:effective-settings'\n" +
            "        } else {\n" +
            "            bat 'mvn help:effective-settings'\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        // @formatter:on

        GlobalConfigFiles.get().save(mavenSettingsConfig);

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class,
                    "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven settings provided by the Jenkins Managed Configuration File 'maven-config-test-from-pipeline-attribute'",
                    build);
            jenkinsRule.assertLogContains(
                    "<id>id-settings-test-from-pipeline-attribute-and-config-file-provider</id>", build);
        } finally {
            GlobalConfigFiles.get().remove(mavenSettingsConfig.id);
        }
    }

    @Issue("JENKINS-27395")
    @Test
    public void maven_build_test_results_by_stage_and_branch() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "stage('first') {\n" +
            "    parallel(a: {\n" +
            "        node() {\n" +
            "            git($/" + gitRepoRule.toString() + "/$)\n" +
            "            withMaven() {\n" +
            "                if (isUnix()) {\n" +
            "                    sh 'mvn package verify'\n" +
            "                } else {\n" +
            "                    bat 'mvn package verify'\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    },\n" +
            "    b: {\n" +
            "        node() {\n" +
            "            git($/" + gitRepoRule.toString() + "/$)\n" +
            "            withMaven() {\n" +
            "                if (isUnix()) {\n" +
            "                    sh 'mvn package verify'\n" +
            "                } else {\n" +
            "                    bat 'mvn package verify'\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    })\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-on-master-test-results-by-stage-and-branch");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.buildAndAssertSuccess(pipeline);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount()).isEqualTo(4);
        assertThat(testResultAction.getFailCount()).isEqualTo(0);

        assertStageResults(build, 4, 4, 0, "first");

        assertBranchResults(build, 2, 2, 0, "a", "first", null, false);
        assertBranchResults(build, 2, 2, 0, "b", "first", null, false);
    }

    private static void assertBranchResults(
            WorkflowRun run,
            int suiteCount,
            int testCount,
            int failCount,
            String branchName,
            String stageName,
            String innerStageName,
            boolean keepTestNames) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        BlockStartNode aBranch = (BlockStartNode) scanner.findFirstMatch(execution, branchForName(branchName));
        assertNotNull(aBranch);
        TestResult branchResult = assertBlockResults(run, suiteCount, testCount, failCount, aBranch);
        String namePrefix;
        if (!keepTestNames) {
            namePrefix = stageName + " / " + branchName;
            if (innerStageName != null) {
                namePrefix += " / " + innerStageName;
            }
            namePrefix += " / ";
        } else {
            namePrefix = "";
        }
        for (CaseResult c : branchResult.getPassedTests()) {
            assertEquals(namePrefix + c.getTransformedTestName(), c.getDisplayName());
        }
    }

    private static void assertStageResults(
            WorkflowRun run, int suiteCount, int testCount, int failCount, String stageName) {
        FlowExecution execution = run.getExecution();
        DepthFirstScanner scanner = new DepthFirstScanner();
        BlockStartNode aStage = (BlockStartNode) scanner.findFirstMatch(execution, stageForName(stageName));
        assertNotNull(aStage);
        assertBlockResults(run, suiteCount, testCount, failCount, aStage);
    }

    private static TestResult assertBlockResults(
            WorkflowRun run, int suiteCount, int testCount, int failCount, BlockStartNode blockNode) {
        assertNotNull(blockNode);

        TestResultAction action = run.getAction(TestResultAction.class);
        assertNotNull(action);

        TestResult aResult = action.getResult().getResultForPipelineBlock(blockNode.getId());
        assertNotNull(aResult);

        assertEquals(suiteCount, aResult.getSuites().size());
        assertEquals(testCount, aResult.getTotalCount());
        assertEquals(failCount, aResult.getFailCount());
        if (failCount > 0) {
            MatcherAssert.assertThat(findJUnitSteps(blockNode), CoreMatchers.hasItem(hasWarningAction()));
        } else {
            MatcherAssert.assertThat(
                    findJUnitSteps(blockNode), CoreMatchers.not(CoreMatchers.hasItem(hasWarningAction())));
        }

        PipelineBlockWithTests aBlock = action.getResult().getPipelineBlockWithTests(blockNode.getId());

        assertNotNull(aBlock);
        List<String> aTestNodes = new ArrayList<>(aBlock.nodesWithTests());
        TestResult aFromNodes = action.getResult().getResultByNodes(aTestNodes);
        assertNotNull(aFromNodes);
        assertEquals(aResult.getSuites().size(), aFromNodes.getSuites().size());
        assertEquals(aResult.getFailCount(), aFromNodes.getFailCount());
        assertEquals(aResult.getSkipCount(), aFromNodes.getSkipCount());
        assertEquals(aResult.getPassCount(), aFromNodes.getPassCount());

        return aResult;
    }

    private static Predicate<FlowNode> stageForName(final String name) {
        return input -> input instanceof StepStartNode
                && ((StepStartNode) input).getDescriptor() instanceof StageStep.DescriptorImpl
                && input.getDisplayName().equals(name);
    }

    private static Predicate<FlowNode> branchForName(final String name) {
        return input -> input != null
                && input.getAction(LabelAction.class) != null
                && input.getAction(ThreadNameAction.class) != null
                && name.equals(input.getAction(ThreadNameAction.class).getThreadName());
    }

    private static List<FlowNode> findJUnitSteps(BlockStartNode blockStart) {
        return new DepthFirstScanner()
                .filteredNodes(
                        Collections.singletonList(blockStart.getEndNode()),
                        Collections.singletonList(blockStart),
                        node -> node instanceof StepAtomNode
                                && ((StepAtomNode) node).getDescriptor() instanceof JUnitResultsStep.DescriptorImpl);
    }

    private static BaseMatcher<FlowNode> hasWarningAction() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof FlowNode && ((FlowNode) item).getPersistentAction(WarningAction.class) != null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a FlowNode with a WarningAction");
            }
        };
    }
}
