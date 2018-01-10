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

import hudson.model.Result;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.TasksScannerPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO migrate to {@link WithMavenStepTest} once we have implemented a GitRepoRule that can be used on remote agents
 */
public class WithMavenStepGlobalConfigurationTest extends AbstractIntegrationTest {

    @Test
    public void maven_build_jar_project_on_master_disable_globally_findbugs_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(new FindbugsAnalysisPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_disable_globally_tasks_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(new TasksScannerPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_disable_globally_junit_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(new JunitTestsPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_disable_globally_generated_artifacts_publisher_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(new GeneratedArtifactsPublisher.DescriptorImpl());
    }

    private void maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(MavenPublisher.DescriptorImpl descriptor) throws Exception {

        MavenPublisher publisher = descriptor.clazz.newInstance();
        publisher.setDisabled(true);

        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();

        globalPipelineMavenConfig.setPublisherOptions(Collections.singletonList(publisher));
        Logger logger = Logger.getLogger(MavenSpyLogProcessor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.FINE);
        try {


            Symbol symbolAnnotation = descriptor.getClass().getAnnotation(Symbol.class);
            String symbol = symbolAnnotation.value()[0];
            String displayName = descriptor.getDisplayName();

            loadMavenJarProjectInGitRepo(this.gitRepoRule);

            String pipelineScript = "node('master') {\n" +
                    "    git($/" + gitRepoRule.toString() + "/$)\n" +
                    "    withMaven() {\n" +
                    "        sh 'mvn package verify'\n" +
                    "    }\n" +
                    "}";

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-" + symbol + "-publisher-globally-disabled");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
            jenkinsRule.assertLogContains(message, build);
        } finally {
            logger.setLevel(level);
            globalPipelineMavenConfig.setPublisherOptions((List<MavenPublisher>) null);
        }
    }


    @Test
    public void maven_build_jar_project_on_master_with_findbugs_publisher_configured_both_globally_and_on_the_pipeline_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(new FindbugsAnalysisPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_with_task_scanner_publisher_configured_both_globally_and_on_the_pipeline_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(new TasksScannerPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_with_junit_publisher_configured_both_globally_and_on_the_pipeline_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(new JunitTestsPublisher.DescriptorImpl());
    }

    @Test
    public void maven_build_jar_project_on_master_with_generated_artifacts_publisher_configured_both_globally_and_on_the_pipeline_succeeds() throws Exception {
        maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(new GeneratedArtifactsPublisher.DescriptorImpl());
    }

    private void maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(MavenPublisher.DescriptorImpl descriptor) throws Exception {

        MavenPublisher publisher = descriptor.clazz.newInstance();
        publisher.setDisabled(true);

        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();

        globalPipelineMavenConfig.setPublisherOptions(Collections.singletonList(publisher));
        Logger logger = Logger.getLogger(MavenSpyLogProcessor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.FINE);
        try {


            Symbol symbolAnnotation = descriptor.getClass().getAnnotation(Symbol.class);
            String symbol = symbolAnnotation.value()[0];
            String displayName = descriptor.getDisplayName();

            loadMavenJarProjectInGitRepo(this.gitRepoRule);

            String pipelineScript = "node('master') {\n" +
                    "    git($/" + gitRepoRule.toString() + "/$)\n" +
                    "    withMaven(options:[" + symbol + "(disabled: true)]) {\n" +
                    "        sh 'mvn package verify'\n" +
                    "    }\n" +
                    "}";

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "build-on-master-" + symbol + "-publisher-defined-globally-and-in-the-pipeline");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            jenkinsRule.assertLogContains("[withMaven] WARNING merging publisher configuration defined in the 'Global Tool Configuration' and at the pipeline level is not yet supported. " +
                    "Use pipeline level configuration for '" + displayName +                     "'", build);
            jenkinsRule.assertLogContains("[withMaven] Skip '" + displayName + "' disabled by configuration", build);
        } finally {
            logger.setLevel(level);
            globalPipelineMavenConfig.setPublisherOptions((List<MavenPublisher>) null);
        }
    }


}
