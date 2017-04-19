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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import hudson.model.Fingerprint;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.tasks.TasksResultAction;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Maven;
import hudson.tasks.junit.TestResultAction;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtendedToolInstallations;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * TODO migrate to {@link WithMavenStepTest} once we have implemented a GitRepoRule that can be used on remote agents
 */
public class WithMavenStepOnMasterTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Rule
    public GitSampleRepoRule gitRepoRule = new GitSampleRepoRule();

    private String mavenInstallationName;

    @Before
    public void setup() throws Exception {
        // Maven.MavenInstallation maven3 = ToolInstallations.configureMaven35();
        Maven.MavenInstallation maven3 = ExtendedToolInstallations.configureMaven35();

        mavenInstallationName = maven3.getName();

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());

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
        jenkinsRule.assertLogContains("use Maven installation 'apache-maven-3.5.0'", build);

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
    public void maven_build_on_master_succeeds() throws Exception {
        loadMavenJarProjectInGitRepo(this.gitRepoRule);

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
        jenkinsRule.assertLogContains("[withMaven] use Maven installation provided by the build agent with", build);

        // verify .pom is archived and fingerprinted
        // "[withMaven] Archive ... under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom"
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom", build);

        // verify .jar is archived and fingerprinted
        jenkinsRule.assertLogContains("under jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar", build);
        System.out.println(getClass() + " artifacts: " + build.getArtifacts());

        // TODO build.getArtifacts() is always empty (size=0), skip verification

        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.jar");
        verifyFileIsFingerPrinted(pipeline, build, "jenkins/mvn/test/mono-module-maven-app/0.1-SNAPSHOT/mono-module-maven-app-0.1-SNAPSHOT.pom");

        //  verify Junit Archiver is called for jenkins.mvn.test:mono-module-maven-app
        jenkinsRule.assertLogContains("[withMaven] Archive test results for Maven artifact MavenArtifact{jenkins.mvn.test:mono-module-maven-app::0.1-SNAPSHOT}", build);
        // TODO check why we have "[build-on-master #1] None of the test reports contained any result" when there should be 1 result
        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        System.out.println(getClass() + " testResultAction: " + testResultAction);

        // verify Task Scanner is called for jenkins.mvn.test:mono-module-maven-app
        jenkinsRule.assertLogContains("[withMaven] Scan Tasks for Maven artifact MavenArtifact{jenkins.mvn.test:mono-module-maven-app::0.1-SNAPSHOT}", build);
        // TODO check why we have "Found 0 files to scan for tasks" when there should be 2 java files to scan
        TasksResultAction tasksResultAction = build.getAction(TasksResultAction.class);
        assertThat(tasksResultAction.getProjectActions().size(), is(1));
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

    /**
     * https://issues.jenkins-ci.org/browse/JENKINS-42565
     */
    @Test
    public void mavenSettingsFilePath_should_work_with_relative_path() throws Exception {

        String pipelineScript = "node () {\n" +
                "    writeFile file: 'maven-settings.xml', text: '''<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<settings \n" +
                "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
                "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
                "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
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
                "        sh 'mvn clean'\n" +
                "    }\n" +
                "}\n";

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-with-relative-maven-settings-path");
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
        jenkinsRule.assertLogContains("[withMaven] use Maven settings provided on the build agent 'maven-settings.xml'", build);
    }

    private void loadMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        gitRepo.init();

        // File mavenProjectRoot = new File("src/test/test-maven-projects/maven-jar-project");
        Path mavenProjectRoot = Paths.get(WithMavenStepOnMasterTest.class.getResource("/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_project/").toURI());
        if (!Files.exists(mavenProjectRoot)) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot + "' not found");
        }

        GitSampleRepoRuleUtils.addFilesAndCommit(mavenProjectRoot, this.gitRepoRule);
    }
}
