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


import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;


import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Fingerprint;
import hudson.model.Result;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.tasks.TasksResultAction;
import hudson.tasks.Fingerprinter;
import hudson.tasks.junit.TestResultAction;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.TasksScannerPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.Symbol;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;



/**
 * TODO migrate to {@link WithMavenStepTest} once we have implemented a GitRepoRule that can be used on remote agents
 */
public class WithMavenStepOnMasterTest extends AbstractIntegrationTest {

    Logger logger;
    Level savedLevel;

    @Before
    public void before() {
        // Many log messages checked here are not logged if we are not in FINE level.
        logger = Logger.getLogger(WithMavenStepExecution.class.getName());
        savedLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
    }

    @After
    public void after() {
        logger.setLevel(savedLevel);
    }

    @Test
    public void maven_build_on_master_with_specified_maven_installation_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-tool-provided-maven");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify provided Maven is used
        jenkinsRule.assertLogContains("using Maven installation 'apache-maven-3.5.0'", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom", build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar", build);
    }


    @Test
    public void maven_build_on_master_with_missing_specified_maven_installation_fails() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven(maven: 'install-does-not-exist') {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-tool-provided-maven");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
    }

    @Test
    public void maven_build_jar_project_on_master_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package verify'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom", build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("mono-module-maven-app-0.1-SNAPSHOT.pom", "mono-module-maven-app-0.1-SNAPSHOT.jar"));

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom");

        //  verify Junit Archiver is called for maven-surefire-plugin
        jenkinsRule.assertLogContains("[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT " +
                "generated by maven-surefire-plugin:test", build);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount(), is(3));
        assertThat(testResultAction.getFailCount(), is(0));

        //  verify Junit Archiver is called for maven-failsafe-plugin
        jenkinsRule.assertLogContains("[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT " +
                "generated by maven-failsafe-plugin:integration-test", build);

        // verify Task Scanner is called for jenkins.mvn.test:mono-module-maven-app
        jenkinsRule.assertLogContains("[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:mono-module-maven-app:jar:0.1-SNAPSHOT", build);
        TasksResultAction tasksResultAction = build.getAction(TasksResultAction.class);
        assertThat(tasksResultAction.getProjectActions().size(), is(1));
    }

    @Ignore("disable while we can't use ASM 6.x due to jenkins enforcer preventing jars supporting java 11 https://gist.github.com/cyrille-leclerc/ac2ad901c27b3b96fd3efa6ed8062f7c")
    @Test
    public void maven_build_jar_with_jacoco_succeeds() throws Exception {
        loadMavenJarWithJacocoInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package verify'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "jar-with-jacoco");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

       Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("jar-with-jacoco-0.1-SNAPSHOT.pom", "jar-with-jacoco-0.1-SNAPSHOT.jar"));

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/jar-with-jacoco/0.1-SNAPSHOT/jar-with-jacoco-0.1-SNAPSHOT.pom");

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount(), is(2));
        assertThat(testResultAction.getFailCount(), is(0));

        // verify Task Scanner is called for jenkins.mvn.test:mono-module-maven-app
        JacocoBuildAction jacocoBuildAction = build.getAction(JacocoBuildAction.class);
        assertThat(jacocoBuildAction.getProjectActions().size(), is(1));
    }

    @Issue("JENKINS-48264")
    @Test
    public void maven_build_jar_project_with_whitespace_char_in_name() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}";

        String mavenSettings =                 "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-settings-test-through-config-file-provider</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";
        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig("maven-config-test", "maven-config-test", "", mavenSettings, false, null);

        GlobalConfigFiles.get().save(mavenSettingsConfig);
        GlobalMavenConfig.get().setSettingsProvider(new MvnSettingsProvider(mavenSettingsConfig.id));


        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build on master with spaces");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }

    }

    @Test
    public void maven_build_jar_project_on_master_disable_findbugs_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new FindbugsAnalysisPublisher.DescriptorImpl(), "findbugsPublisher", true);
    }

    @Test
    public void maven_build_jar_project_on_master_disable_tasks_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new TasksScannerPublisher.DescriptorImpl(), "openTasksPublisher", true);
    }

    @Test
    public void maven_build_jar_project_on_master_disable_junit_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new JunitTestsPublisher.DescriptorImpl(), "junitPublisher", true);
    }

    @Test
    public void maven_build_jar_project_on_master_disable_generated_artifacts_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new GeneratedArtifactsPublisher.DescriptorImpl(), "artifactsPublisher", true);
    }

    @Test
    public void maven_build_jar_project_on_master_force_enable_findbugs_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new FindbugsAnalysisPublisher.DescriptorImpl(), "findbugsPublisher", false);
    }

    @Test
    public void maven_build_jar_project_on_master_force_enable_tasks_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new TasksScannerPublisher.DescriptorImpl(), "openTasksPublisher", false);
    }

    @Test
    public void maven_build_jar_project_on_master_force_enable_junit_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new JunitTestsPublisher.DescriptorImpl(), "junitPublisher", false);
    }

    @Test
    public void maven_build_jar_project_on_master_force_enable_generated_artifacts_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(new GeneratedArtifactsPublisher.DescriptorImpl(), "artifactsPublisher", false);
    }


    private void maven_build_jar_project_on_master_with_disabled_publisher_param_succeeds(MavenPublisher.DescriptorImpl descriptor, String symbol, boolean disabled) throws Exception {

        Logger logger = Logger.getLogger(MavenSpyLogProcessor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.FINE);
        try {
            String displayName = descriptor.getDisplayName();

            Symbol symbolAnnotation = descriptor.getClass().getAnnotation(Symbol.class);
            String[] symbols = symbolAnnotation.value();
            assertThat(new String[]{symbol}, is(symbols));

            loadMavenJarProjectInGitRepo(this.gitRepoRule);

            String pipelineScript = "node('master') {\n" +
                    "    git($/" + gitRepoRule.toString() + "/$)\n" +
                    "    withMaven(options:[" + symbol + "(disabled:" + disabled + ")]) {\n" +
                    "        sh 'mvn package verify'\n" +
                    "    }\n" +
                    "}";

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-" + symbol + "-publisher-disabled-" + disabled);
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
            if (disabled) {
                jenkinsRule.assertLogContains(message, build);
            } else {
                jenkinsRule.assertLogNotContains(message, build);
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
        assertThat(new String[]{"openTasksPublisher"},  is(symbols));

        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven(options:[openTasksPublisher(" +
                "       disabled:false, " +
                "       pattern:'src/main/java', excludePattern:'a/path'," +
                "       ignoreCase:true, asRegexp:false, " +
                "       lowPriorityTaskIdentifiers:'minor', normalPriorityTaskIdentifiers:'todo', highPriorityTaskIdentifiers:'fixme')]) {\n" +
                "           sh 'mvn package verify'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-openTasksPublisher-publisher-config");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
        jenkinsRule.assertLogNotContains(message, build);
    }

    @Test
    public void maven_build_maven_jar_with_flatten_pom_project_on_master_succeeds() throws Exception {
        loadMavenJarWithFlattenPomProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-jar-with-flatten-pom-project-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        jenkinsRule.assertLogNotContains("[jenkins-maven-event-spy] WARNING: unexpected Maven project file name '.flattened-pom.xml', problems may occur", build);


        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom", build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom", "maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar"));

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/maven-jar-with-flattened-pom/0.1-SNAPSHOT/maven-jar-with-flattened-pom-0.1-SNAPSHOT.pom");

        //  verify Junit Archiver is called for jenkins.mvn.test:maven-jar-with-flattened-pom
        jenkinsRule.assertLogContains("[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:maven-jar-with-flattened-pom:jar:0.1-SNAPSHOT generated by", build);

        // verify Task Scanner is called for jenkins.mvn.test:maven-jar-with-flattened-pom
        jenkinsRule.assertLogContains("[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:maven-jar-with-flattened-pom:jar:0.1-SNAPSHOT in source directory", build);
    }


    @Test
    public void maven_build_maven_hpi_project_on_master_succeeds() throws Exception {
        loadJenkinsPluginProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.pom", build);

        // verify .jar and .hpi is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.hpi", build);
        jenkinsRule.assertLogContains("under jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("test-jenkins-hpi-0.1-SNAPSHOT.pom", "test-jenkins-hpi-0.1-SNAPSHOT.jar", "test-jenkins-hpi-0.1-SNAPSHOT.hpi"));

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.hpi");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/test-jenkins-hpi/0.1-SNAPSHOT/test-jenkins-hpi-0.1-SNAPSHOT.pom");

        //  verify Junit Archiver is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains("[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:test-jenkins-hpi:hpi:0.1-SNAPSHOT generated by", build);

        // verify Task Scanner is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains("[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:test-jenkins-hpi:hpi:0.1-SNAPSHOT in source directory", build);
    }

    @Test
    public void maven_build_maven_plugin_project_on_master_succeeds() throws Exception {
        loadMavenPluginProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // verify Maven installation provided by the build agent is used
        // can be either "by the build agent with executable..." or "by the build agent with the environment variable MAVEN_HOME=..."
        jenkinsRule.assertLogContains("[withMaven] using Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/maven-test-plugin/1.0-SNAPSHOT/maven-test-plugin-1.0-SNAPSHOT.pom", build);

        // verify .jar and .hpi is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/maven-test-plugin/1.0-SNAPSHOT/maven-test-plugin-1.0-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("maven-test-plugin-1.0-SNAPSHOT.pom", "maven-test-plugin-1.0-SNAPSHOT.jar"));


        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/maven-test-plugin/1.0-SNAPSHOT/maven-test-plugin-1.0-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/maven-test-plugin/1.0-SNAPSHOT/maven-test-plugin-1.0-SNAPSHOT.pom");

        //  verify Junit Archiver is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains("[withMaven] junitPublisher - Archive test results for Maven artifact jenkins.mvn.test:maven-test-plugin:jar:1.0-SNAPSHOT generated by", build);

        // verify Task Scanner is called for jenkins.mvn.test:test-jenkins-hpi
        jenkinsRule.assertLogContains("[withMaven] openTasksPublisher - Scan Tasks for Maven artifact jenkins.mvn.test:maven-test-plugin:jar:1.0-SNAPSHOT in source directory", build);
    }

    /**
     * JENKINS-43678
     */
    @Test
    public void maven_build_on_master_with_no_generated_jar_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn test'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        // don't try to archive the artifact as it has not been generated
        jenkinsRule.assertLogNotContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar", build);

        Collection<String> artifactsFileNames = TestUtils.artifactsToArtifactsFileNames(build.getArtifacts());
        assertThat(artifactsFileNames, hasItems("mono-module-maven-app-0.1-SNAPSHOT.pom"));
        assertThat(build.getArtifacts().toString(), build.getArtifacts().size(), is(1));
    }

    private void verifyFileIsFingerPrinted(WorkflowJob pipeline, WorkflowRun build, String fileName) throws java.io.IOException {
        System.out.println(getClass() + " verifyFileIsFingerPrinted(" + build + ", " + fileName + ")");
        Fingerprinter.FingerprintAction fingerprintAction = build.getAction(Fingerprinter.FingerprintAction.class);
        Map<String, String> records = fingerprintAction.getRecords();
        System.out.println(getClass() + " records: " + records);
        String jarFileMd5sum = records.get(fileName);
        assertThat(jarFileMd5sum, not(nullValue()));

        Fingerprint jarFileFingerPrint = jenkinsRule.getInstance().getFingerprintMap().get(jarFileMd5sum);
        assertThat(jarFileFingerPrint.getFileName(), is(fileName));
        assertThat(jarFileFingerPrint.getOriginal().getJob().getName(), is(pipeline.getName()));
        assertThat(jarFileFingerPrint.getOriginal().getNumber(), is(build.getNumber()));
    }

    @Test
    public void maven_global_settings_path_defined_through_jenkins_global_config() throws Exception {

        File mavenGlobalSettingsFile = new File(jenkinsRule.jenkins.getRootDir(), "maven-global-settings.xml");
        String mavenGlobalSettings =                 "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-global-settings-test</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";
        FileUtils.writeStringToFile(mavenGlobalSettingsFile, mavenGlobalSettings);


        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";
        GlobalMavenConfig.get().setGlobalSettingsProvider(new FilePathGlobalSettingsProvider(mavenGlobalSettingsFile.getAbsolutePath()));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven global settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
        }
    }

    @Test
    public void maven_global_settings_defined_through_jenkins_global_config_and_config_file_provider() throws Exception {

        String mavenGlobalSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-global-settings-test-from-config-file-provider</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig("maven-global-config-test", "maven-global-config-test", "", mavenGlobalSettings);

        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";

        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig);
        GlobalMavenConfig.get().setGlobalSettingsProvider(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven global settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test-from-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
            GlobalConfigFiles.get().remove(mavenGlobalSettingsConfig.id);
        }
    }

    @Test
    public void maven_global_settings_defined_through_folder_config_and_config_file_provider() throws Exception {

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

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig("maven-global-config-test-folder", "maven-global-config-test-folder", "",
            mavenGlobalSettings);

        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";

        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig);

        Folder folder = jenkinsRule.createProject(Folder.class, "folder");
        MavenConfigFolderOverrideProperty configOverrideProperty = new MavenConfigFolderOverrideProperty();
        configOverrideProperty.setOverride(true);
        configOverrideProperty.setGlobalSettings(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig.id));
        folder.addProperty(configOverrideProperty);

        try {
            WorkflowJob pipeline = folder.createProject(WorkflowJob.class,
                "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                "[withMaven] using overriden Maven global settings by folder 'folder'. Config File Provider maven global settings file 'maven-global-config-test-folder'",
                build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test-from-config-file-provider-on-a-folder</id>", build);
        } finally {
            GlobalMavenConfig.get().setGlobalSettingsProvider(null);
            GlobalConfigFiles.get().remove(mavenGlobalSettingsConfig.id);
        }
    }

    @Test
    public void maven_global_settings_path_defined_through_pipeline_attribute() throws Exception {

        String pipelineScript = "node () {\n" +
                "    writeFile file: 'maven-global-settings.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-global-settings-test</id>\n" +
                "	    </server>\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0', globalMavenSettingsFilePath: 'maven-global-settings.xml') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-pipeline");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains("[withMaven] using Maven global settings provided on the build agent", build);
            jenkinsRule.assertLogContains("<id>id-global-settings-test</id>", build);

    }

    /**
     * https://issues.jenkins-ci.org/browse/JENKINS-42565
     */
    @Test
    public void maven_settings_path_defined_through_pipeline_attribute() throws Exception {

        String pipelineScript = "node () {\n" +
                "    writeFile file: 'maven-settings.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-settings-test</id>\n" +
                "	    </server>\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0', mavenSettingsFilePath: 'maven-settings.xml') {\n" +
                "        sh 'env && mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-pipeline");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains("[withMaven] using Maven settings provided on the build agent", build);
        jenkinsRule.assertLogContains("<id>id-settings-test</id>", build);

    }

    @Test
    public void maven_settings_defined_through_jenkins_global_config() throws Exception {

        File mavenSettingsFile = new File(jenkinsRule.jenkins.getRootDir(), "maven-settings.xml");
        String mavenSettings =                 "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-settings-test</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";
        FileUtils.writeStringToFile(mavenSettingsFile, mavenSettings);


        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";
        GlobalMavenConfig.get().setSettingsProvider(new FilePathSettingsProvider(mavenSettingsFile.getAbsolutePath()));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-jenkins-global-config");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    @Test
    public void maven_settings_defined_through_jenkins_global_config_and_config_file_provider() throws Exception {

        String mavenSettings =                 "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-settings-test-through-config-file-provider</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";
        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig("maven-config-test", "maven-config-test", "", mavenSettings, false, null);


        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";
        GlobalConfigFiles.get().save(mavenSettingsConfig);
        GlobalMavenConfig.get().setSettingsProvider(new MvnSettingsProvider(mavenSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven settings provided by the Jenkins global configuration", build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider</id>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    @Test
    public void maven_settings_defined_through_folder_config_and_config_file_provider() throws Exception {

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
        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig("maven-config-test-folder", "maven-config-test-folder", "", mavenSettings, false, null);

        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";
        GlobalConfigFiles.get().save(mavenSettingsConfig);

        Folder folder = jenkinsRule.createProject(Folder.class, "folder");
        MavenConfigFolderOverrideProperty configOverrideProperty = new MavenConfigFolderOverrideProperty();
        configOverrideProperty.setOverride(true);
        GlobalMavenConfig globalMavenConfig = GlobalMavenConfig.get();
        configOverrideProperty.setGlobalSettings(globalMavenConfig.getGlobalSettingsProvider());
        configOverrideProperty.setSettings(new MvnSettingsProvider(mavenSettingsConfig.id));
        folder.addProperty(configOverrideProperty);

        try {
            WorkflowJob pipeline = folder.createProject(WorkflowJob.class, "build-on-master-with-maven-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using overriden Maven settings by folder 'folder'. Config File Provider maven settings file 'maven-config-test-folder'",
                build);
            jenkinsRule.assertLogContains("<id>id-settings-test-through-config-file-provider-on-a-folder</id>", build);
        } finally {
            configOverrideProperty.setOverride(false);
        }
    }

    @Test
    public void maven_settings_defined_through_pipeline_attribute_and_config_file_provider() throws Exception {

        String mavenSettings =                 "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
                "    <servers>\n" +
                "    	<server>\n" +
                "	        <id>id-settings-test-from-pipeline-attribute-and-config-file-provider</id>\n" +
                "	    </server>\n" +
                "    </servers>\n" +
                "</settings>\n";

        MavenSettingsConfig mavenSettingsConfig = new MavenSettingsConfig("maven-config-test-from-pipeline-attribute", "maven-config-test-from-pipeline-attribute", "", mavenSettings, false, null);

        String pipelineScript = "node () {\n" +
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
                "    withMaven(maven: 'apache-maven-3.5.0', mavenSettingsConfig: 'maven-config-test-from-pipeline-attribute') {\n" +
                "        sh 'mvn help:effective-settings'\n" +
                "    }\n" +
                "}\n";

        GlobalConfigFiles.get().save(mavenSettingsConfig);

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-maven-global-settings-defined-in-jenkins-global-config-with-config-file-provider");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains("[withMaven] using Maven settings provided by the Jenkins Managed Configuration File 'maven-config-test-from-pipeline-attribute'", build);
            jenkinsRule.assertLogContains("<id>id-settings-test-from-pipeline-attribute-and-config-file-provider</id>", build);
        } finally {
            GlobalConfigFiles.get().remove(mavenSettingsConfig.id);
        }
    }

    @Issue("JENKINS-27395")
    @Test
    public void maven_build_test_results_by_stage_and_branch() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "stage('first') {\n" +
                "    parallel(a: {\n" +
                "        node('master') {\n" +
                "            git($/" + gitRepoRule.toString() + "/$)\n" +
                "            withMaven() {\n" +
                "                sh 'mvn package verify'\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    b: {\n" +
                "        node('master') {\n" +
                "            git($/" + gitRepoRule.toString() + "/$)\n" +
                "            withMaven() {\n" +
                "                sh 'mvn package verify'\n" +
                "            }\n" +
                "        }\n" +
                "    })\n" +
                "}";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.buildAndAssertSuccess(pipeline);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertThat(testResultAction.getTotalCount(), is(6));
        assertThat(testResultAction.getFailCount(), is(0));

        /*
        TODO enable test below when we can bump the junit-plugin to version 1.23+
        JUnitResultsStepTest.assertStageResults(build, 4, 6, "first");

        JUnitResultsStepTest.assertBranchResults(build, 2, 3, "a", "first");
        JUnitResultsStepTest.assertBranchResults(build, 2, 3, "b", "first");
        */
    }

}
