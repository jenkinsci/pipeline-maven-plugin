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

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import hudson.model.Result;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.publishers.ConcordionTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.CoveragePublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesFingerprintPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.InvokerRunsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JGivenTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.WarningsPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TODO migrate to {@link WithMavenStepTest} once we have implemented a
 * GitRepoRule that can be used on remote agents
 */
public class WithMavenStepGlobalConfigurationTest extends AbstractIntegrationTest {

    @ParameterizedTest(name = "{0}")
    @DisplayName("Publisher disabled")
    @MethodSource("mavenPublisherDescriptors")
    public void maven_build_jar_project_on_master_with_globally_disabled_publisher_succeeds(
            MavenPublisher.DescriptorImpl descriptor) throws Exception {

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

            // @formatter:off
            String pipelineScript = "node() {\n" +
                "    echo 'Running pipeline for " + displayName + "'\n" +
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

            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class, "build-on-master-" + symbol + "-publisher-globally-disabled");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            String message = "[withMaven] Skip '" + displayName + "' disabled by configuration";
            jenkinsRule.assertLogContains(message, build);
        } finally {
            logger.setLevel(level);
            globalPipelineMavenConfig.setPublisherOptions(null);
        }
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Publisher enabled globally and on pipeline")
    @MethodSource("mavenPublisherDescriptors")
    public void maven_build_jar_project_on_master_with_publisher_configured_both_globally_and_on_the_pipeline_succeeds(
            MavenPublisher.DescriptorImpl descriptor) throws Exception {

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

            // @formatter:off
            String pipelineScript = "node() {\n" +
                "    echo 'Running pipeline for " + displayName + "'\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven(options:[" + symbol + "(disabled: true)]) {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn package verify'\n" +
                "        } else {\n" +
                "            bat 'mvn package verify'\n" +
                "        }\n" +
                "    }\n" +
                "}";
            // @formatter:on

            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class, "build-on-master-" + symbol + "-publisher-defined-globally-and-in-the-pipeline");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            jenkinsRule.assertLogContains(
                    "[withMaven] WARNING merging publisher configuration defined in the 'Global Tool Configuration' and at the pipeline level is not yet supported. "
                            + "Use pipeline level configuration for '" + displayName + "'",
                    build);
            jenkinsRule.assertLogContains("[withMaven] Skip '" + displayName + "' disabled by configuration", build);
        } finally {
            logger.setLevel(level);
            globalPipelineMavenConfig.setPublisherOptions(null);
        }
    }

    private static Stream<Arguments> mavenPublisherDescriptors() {
        return Stream.of(
                arguments(named("Coverage", new CoveragePublisher.DescriptorImpl())),
                arguments(named("Concordion", new ConcordionTestsPublisher.DescriptorImpl())),
                arguments(named("DependenciesFingerprint", new DependenciesFingerprintPublisher.DescriptorImpl())),
                arguments(named("GeneratedArtifacts", new GeneratedArtifactsPublisher.DescriptorImpl())),
                arguments(named("InvokerRuns", new InvokerRunsPublisher.DescriptorImpl())),
                arguments(named("JGiven", new JGivenTestsPublisher.DescriptorImpl())),
                arguments(named("Junit", new JunitTestsPublisher.DescriptorImpl())),
                arguments(named("MavenLinker", new MavenLinkerPublisher2.DescriptorImpl())),
                arguments(named("PipelineGraph", new PipelineGraphPublisher.DescriptorImpl())),
                arguments(named("Warnings", new WarningsPublisher.DescriptorImpl())));
    }
}
