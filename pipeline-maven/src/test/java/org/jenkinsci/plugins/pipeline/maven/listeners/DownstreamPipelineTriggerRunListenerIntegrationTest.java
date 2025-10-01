package org.jenkinsci.plugins.pipeline.maven.listeners;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;

import hudson.ExtensionList;
import hudson.model.Result;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

/**
 * We need some tests. Unfortunately, it is very hard to do unit tests because
 * Jenkins APIs are almost impossible to mock.
 *
 * Needed test
 * <ul>
 * <li>Pipeline doesn't trigger itself when it has a dependency on</li>
 * </ul>
 */
@EnabledOnOs(
        value = LINUX,
        disabledReason = "'fatal error: aux_index does not match even or odd indices' on Windows JDK 19")
public class DownstreamPipelineTriggerRunListenerIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    public void setup() throws Exception {
        ExtensionList.lookupSingleton(GlobalPipelineMavenConfig.class)
                .setDaoClass(PipelineMavenPluginH2Dao.class.getName());
        String jdbcUrl = "jdbc:h2:file:" + new File("target", getClass().getName() + "-h2").getAbsolutePath() + ";"
                + "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
        ExtensionList.lookupSingleton(GlobalPipelineMavenConfig.class).setJdbcUrl(jdbcUrl);
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
    }

    @Test
    public void test_infinite_loop() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/multi_module_maven_project/");
        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        WorkflowJob pipeline1 = jenkinsRule.createProject(WorkflowJob.class, "pipeline-1");
        pipeline1.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun pipeline1Build1 = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline1.scheduleBuild2(0));

        WorkflowJob pipeline2 = jenkinsRule.createProject(WorkflowJob.class, "pipeline-2");
        pipeline2.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun pipeline2Build1 = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline2.scheduleBuild2(0));

        for (WorkflowRun run : asList(pipeline1Build1, pipeline2Build1)) {
            List<MavenDependency> dependencies = GlobalPipelineMavenConfig.get()
                    .getDao()
                    .listDependencies(run.getParent().getFullName(), run.number);
            assertThat(dependencies)
                    .contains(
                            dep("jenkins.mvn.test.multimodule", "shared-core", "jar", "0.0.1-SNAPSHOT", "compile"),
                            dep("junit", "junit", "jar", "4.13.2", "test"),
                            dep("org.hamcrest", "hamcrest-core", "jar", "1.3", "test"));

            List<MavenArtifact> generatedArtifacts = GlobalPipelineMavenConfig.get()
                    .getDao()
                    .getGeneratedArtifacts(run.getParent().getFullName(), run.number);
            assertThat(generatedArtifacts)
                    .contains(
                            artifact("jenkins.mvn.test.multimodule:demo-1:jar:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:demo-1:pom:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:demo-2:jar:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:demo-2:pom:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:multimodule-parent:pom:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:shared-core:jar:0.0.1-SNAPSHOT"),
                            artifact("jenkins.mvn.test.multimodule:shared-core:pom:0.0.1-SNAPSHOT"));
        }
    }

    private MavenDependency dep(String groupId, String artifactId, String type, String version, String scope) {
        MavenDependency result = new MavenDependency();
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setType(type);
        result.setVersion(version);
        result.setScope(scope);
        return result;
    }

    private MavenArtifact artifact(String identifier) {
        MavenArtifact result = new MavenArtifact(identifier);
        result.setBaseVersion(result.getVersion());
        return result;
    }
}
