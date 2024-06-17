package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runAfterMethod;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runBeforeMethod;

import hudson.ExtensionList;
import hudson.model.Cause;
import hudson.model.Result;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public class DockerDowstreamTest extends AbstractIntegrationTest {

    public GitSampleRepoRule downstreamArtifactRepoRule;

    @BeforeEach
    public void setup() throws Exception {
        ExtensionList.lookupSingleton(GlobalPipelineMavenConfig.class)
                .setDaoClass(PipelineMavenPluginH2Dao.class.getName());
        String jdbcUrl = "jdbc:h2:file:" + new File("target", getClass().getName() + "-h2").getAbsolutePath() + ";"
                + "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
        ExtensionList.lookupSingleton(GlobalPipelineMavenConfig.class).setJdbcUrl(jdbcUrl);
        downstreamArtifactRepoRule = new GitSampleRepoRule();
        runBeforeMethod(downstreamArtifactRepoRule);

        PipelineGraphPublisher publisher = new PipelineGraphPublisher();
        publisher.setLifecycleThreshold("install");

        List<MavenPublisher> publisherOptions = GlobalPipelineMavenConfig.get().getPublisherOptions();
        if (publisherOptions == null) {
            publisherOptions = new ArrayList<>();
            GlobalPipelineMavenConfig.get().setPublisherOptions(publisherOptions);
        }
        publisherOptions.add(publisher);
    }

    @AfterEach
    public void destroy() {
        runAfterMethod(downstreamArtifactRepoRule);
    }

    @Test
    public void verify_docker_downstream_simple_pipeline_trigger() throws Exception {
        downstreamArtifactRepoRule = new GitSampleRepoRule();
        runBeforeMethod(downstreamArtifactRepoRule);
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_docker_dependency_project/");
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadSourceCodeInGitRepository(
                this.downstreamArtifactRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_docker_base_project/");

        // @formatter:off
        String mavenDockerDependencyPipelineScript = "node() {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn install'\n" +
                "        } else {\n" +
                "            bat 'mvn install'\n" +
                "        }\n" +
                "    }\n" +
                "}";
        String mavenDockerBasePipelineScript = "node() {\n" +
                "    git($/" + downstreamArtifactRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        if (isUnix()) {\n" +
                "            sh 'mvn install'\n" +
                "        } else {\n" +
                "            bat 'mvn install'\n" +
                "        }\n" +
                "    }\n" +
                "}";
        // @formatter:on

        WorkflowJob mavenDockerDependency = jenkinsRule.createProject(WorkflowJob.class, "build-docker-dependency");
        mavenDockerDependency.setDefinition(new CpsFlowDefinition(mavenDockerDependencyPipelineScript, true));
        mavenDockerDependency.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenDockerDependencyPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDockerDependency.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenDockerBasePipeline = jenkinsRule.createProject(WorkflowJob.class, "build-docker-base");
        mavenDockerBasePipeline.setDefinition(new CpsFlowDefinition(mavenDockerBasePipelineScript, true));
        mavenDockerBasePipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenDockerBasePipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDockerBasePipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the docker project is recorded
        System.out.println("build-docker-dependencyFirstRun: " + mavenDockerBasePipelineFirstRun);

        WorkflowRun mavenDockerDependencyPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDockerDependency.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenDockerBasePipelineLastRun = mavenDockerBasePipeline.getLastBuild();

        System.out.println("build-docker-baseLastBuild: " + mavenDockerBasePipelineLastRun + " caused by "
                + mavenDockerBasePipelineLastRun.getCauses());

        assertThat(mavenDockerBasePipelineLastRun.getNumber())
                .isEqualTo(mavenDockerBasePipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenDockerBasePipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }
}
