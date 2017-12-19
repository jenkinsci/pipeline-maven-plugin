package org.jenkinsci.plugins.pipeline.maven;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Result;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.pipeline.maven.util.WorkflowMultibranchProjectTestsUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyGraphTest extends AbstractIntegrationTest {

    @Rule
    public GitSampleRepoRule downstreamArtifactRepoRule = new GitSampleRepoRule();

    /*
    Does not work
    @Inject
    public GlobalPipelineMavenConfig globalPipelineMavenConfig;
    */

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        PipelineGraphPublisher publisher = new PipelineGraphPublisher();
        publisher.setLifecycleThreshold("install");

        List<MavenPublisher> publisherOptions = GlobalPipelineMavenConfig.get().getPublisherOptions();
        if (publisherOptions == null) {
            publisherOptions = new ArrayList<>();
            GlobalPipelineMavenConfig.get().setPublisherOptions(publisherOptions);
        }
        publisherOptions.add(publisher);
    }

    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_simple_pipeline_trigger() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadMavenWarProjectInGitRepo(this.downstreamArtifactRepoRule);

        String mavenJarPipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";
        String mavenWarPipelineScript = "node('master') {\n" +
                "    git($/" + downstreamArtifactRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";


        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenJarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded


        WorkflowJob mavenWarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-war");
        mavenWarPipeline.setDefinition(new CpsFlowDefinition(mavenWarPipelineScript, true));
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenWarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenWarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded
        System.out.println("mavenWarPipelineFirstRun: " + mavenWarPipelineFirstRun);

        WorkflowRun mavenJarPipelineSecondRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by " + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber(), is(mavenWarPipelineFirstRun.getNumber() + 1));
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause, notNullValue());
    }

    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_multi_branch_pipeline_trigger() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadMavenWarProjectInGitRepo(this.downstreamArtifactRepoRule);

        String script = "node('master') {\n" +
                "    checkout scm\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";
        gitRepoRule.write("Jenkinsfile", script);
        gitRepoRule.git("add", "Jenkinsfile");
        gitRepoRule.git("commit", "--message=jenkinsfile");


        downstreamArtifactRepoRule.write("Jenkinsfile", script);
        downstreamArtifactRepoRule.git("add", "Jenkinsfile");
        downstreamArtifactRepoRule.git("commit", "--message=jenkinsfile");

        // TRIGGER maven-jar#1 to record that "build-maven-jar" generates this jar and install this maven jar in the local maven repo
        WorkflowMultiBranchProject mavenJarPipeline = jenkinsRule.createProject(WorkflowMultiBranchProject.class, "build-maven-jar");
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        mavenJarPipeline.getSourcesList().add(new BranchSource(new GitSCMSource(null, gitRepoRule.toString(), "", "*", "", false)));
        System.out.println("trigger maven-jar#1...");
        WorkflowJob mavenJarPipelineMasterPipeline = WorkflowMultibranchProjectTestsUtils.scheduleAndFindBranchProject(mavenJarPipeline, "master");
        assertEquals(1, mavenJarPipeline.getItems().size());
        System.out.println("wait for maven-jar#1...");
        jenkinsRule.waitUntilNoActivity();

        assertThat(mavenJarPipelineMasterPipeline.getLastBuild().getNumber(), is(1));
        // TODO check in DB that the generated artifact is recorded

        // TRIGGER maven-war#1 to record that "build-maven-war" has a dependency on "build-maven-jar"
        WorkflowMultiBranchProject mavenWarPipeline = jenkinsRule.createProject(WorkflowMultiBranchProject.class, "build-maven-war");
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        mavenWarPipeline.getSourcesList().add(new BranchSource(new GitSCMSource(null, downstreamArtifactRepoRule.toString(), "", "*", "", false)));
        System.out.println("trigger maven-war#1...");
        WorkflowJob mavenWarPipelineMasterPipeline = WorkflowMultibranchProjectTestsUtils.scheduleAndFindBranchProject(mavenWarPipeline, "master");
        assertEquals(1, mavenWarPipeline.getItems().size());
        System.out.println("wait for maven-war#1...");
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun mavenWarPipelineFirstRun = mavenWarPipelineMasterPipeline.getLastBuild();

        // TODO check in DB that the dependency on the war project is recorded


        // TRIGGER maven-jar#2 so that it triggers "maven-war" and creates maven-war#2
        System.out.println("trigger maven-jar#2...");
        Future<WorkflowRun> mavenJarPipelineMasterPipelineSecondRunFuture = mavenJarPipelineMasterPipeline.scheduleBuild2(0, new CauseAction(new Cause.RemoteCause("127.0.0.1", "junit test")));
        System.out.println("wait for maven-jar#2...");
        mavenJarPipelineMasterPipelineSecondRunFuture.get();
        jenkinsRule.waitUntilNoActivity();


        WorkflowRun mavenWarPipelineLastRun = mavenWarPipelineMasterPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by " + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber(), is(mavenWarPipelineFirstRun.getNumber() + 1));
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause, notNullValue());

    }

    @Test
    public void verify_osgi_bundle_recorded_as_bundle_and_as_jar() throws Exception {
        loadOsgiBundleProjectInGitRepo(gitRepoRule);


        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        // TRIGGER maven-jar#1 to record that "build-maven-jar"
        WorkflowJob multiModuleBundleProjectPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-multi-module-bundle");
        multiModuleBundleProjectPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, multiModuleBundleProjectPipeline.scheduleBuild2(0));

        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.getDao();
        if (!(dao instanceof PipelineMavenPluginH2Dao))
            throw new IllegalStateException();

        PipelineMavenPluginH2Dao h2Dao = (PipelineMavenPluginH2Dao) dao;
        List<Map<String, String>> generatedArtifacts = h2Dao.getGeneratedArtifacts(multiModuleBundleProjectPipeline.getFullName(), build.getNumber());

        /*
        [{skip_downstream_triggers=TRUE, type=pom, gav=jenkins.mvn.test.bundle:bundle-parent:0.0.1-SNAPSHOT},
        {skip_downstream_triggers=TRUE, type=bundle, gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
        {skip_downstream_triggers=TRUE, type=jar, gav=jenkins.mvn.test.bundle:print-impl:0.0.1-SNAPSHOT},
        {skip_downstream_triggers=TRUE, type=jar, gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
        {skip_downstream_triggers=TRUE, type=pom, gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
        {skip_downstream_triggers=TRUE, type=pom, gav=jenkins.mvn.test.bundle:print-impl:0.0.1-SNAPSHOT}]

         */
        System.out.println("generated artifacts" + generatedArtifacts);

        Iterable<Map<String, String>> matchingGeneratedArtifacts =Iterables.filter(generatedArtifacts, new Predicate<Map<String, String>>() {
            @Override
            public boolean apply(@Nullable Map<String, String> input) {
                return input != null &&  "jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT".equals(input.get("gav"));
            }
        });

        Iterable<String> matchingArtifactTypes = Iterables.transform(matchingGeneratedArtifacts, new Function<Map<String, String>, String>() {
            @Override
            public String apply(@Nullable Map<String, String> input) {
                return input.get("type");
            }
        });


        assertThat(matchingArtifactTypes, Matchers.containsInAnyOrder("jar", "bundle", "pom"));
    }


    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_pipeline_triggered_on_parent_pom_build() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadMavenWarProjectInGitRepo(this.downstreamArtifactRepoRule);

        String mavenJarPipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";
        String mavenWarPipelineScript = "node('master') {\n" +
                "    git($/" + downstreamArtifactRepoRule.toString() + "/$)\n" +
                "    withMaven() {\n" +
                "        sh 'mvn install'\n" +
                "    }\n" +
                "}";


        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenJarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded


        WorkflowJob mavenWarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-war");
        mavenWarPipeline.setDefinition(new CpsFlowDefinition(mavenWarPipelineScript, true));
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenWarPipelineFirstRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenWarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded
        System.out.println("mavenWarPipelineFirstRun: " + mavenWarPipelineFirstRun);

        WorkflowRun mavenJarPipelineSecondRun = jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by " + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber(), is(mavenWarPipelineFirstRun.getNumber() + 1));
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause, notNullValue());


    }
}
