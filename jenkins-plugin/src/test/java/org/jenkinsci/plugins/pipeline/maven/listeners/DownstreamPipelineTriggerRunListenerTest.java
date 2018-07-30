package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.maven.*;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * We need some tests. Unfortunately, it is very hard to do unit tests because Jenkins APIs 
 * are almost impossible to mock.
 * 
 * Needed test
 * <ul>
 *     <li>Pipeline doesn't trigger itself when it has a dependency on </li>
 * </ul>
 */
public class DownstreamPipelineTriggerRunListenerTest extends AbstractIntegrationTest {

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();


        List<MavenPublisher> publisherOptions = GlobalPipelineMavenConfig.get().getPublisherOptions();
        if (publisherOptions == null) {
            publisherOptions = new ArrayList<>();
            GlobalPipelineMavenConfig.get().setPublisherOptions(publisherOptions);
        }
        {
            PipelineGraphPublisher publisher = new PipelineGraphPublisher();
            publisher.setLifecycleThreshold("install");
            publisher.setIncludeReleaseVersions(true);
            publisher.setIncludeScopeTest(true);
            publisherOptions.add(publisher);
        }
        {
            FindbugsAnalysisPublisher publisher = new FindbugsAnalysisPublisher();
            publisher.setDisabled(true);
            publisherOptions.add(publisher);
        }
    }


    @Test
    public void test_infinite_loop() throws Exception {
        loadMultiModuleProjectInGitRepo(this.gitRepoRule);
        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";

        WorkflowJob pipeline1 = jenkinsRule.createProject(WorkflowJob.class, "pipeline-1");
        pipeline1.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun pipeline1Build1 = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline1.scheduleBuild2(0));

        WorkflowJob pipeline2 = jenkinsRule.createProject(WorkflowJob.class, "pipeline-2");
        pipeline2.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun pipeline2Build1 = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline2.scheduleBuild2(0));


        dumpBuildDetails(pipeline1Build1);
        dumpBuildDetails(pipeline2Build1);

    }

    protected void dumpBuildDetails(WorkflowRun build) {
        System.out.println();
        System.out.println("# BUILD: " + build.getFullDisplayName());
        System.out.println("## Dependencies");
        List<MavenDependency> mavenDependencies = GlobalPipelineMavenConfig.get().getDao().listDependencies(build.getParent().getFullName(), build.number);
        for(MavenDependency mavenDependency: mavenDependencies) {
            System.out.println(mavenDependency);
        }
        System.out.println("## Generated Artifacts");
        List<MavenArtifact> generatedArtifacts = GlobalPipelineMavenConfig.get().getDao().getGeneratedArtifacts(build.getParent().getFullName(), build.number);
        for(MavenArtifact generatedArtifact: generatedArtifacts) {
            System.out.println(generatedArtifact);
        }
    }

}
