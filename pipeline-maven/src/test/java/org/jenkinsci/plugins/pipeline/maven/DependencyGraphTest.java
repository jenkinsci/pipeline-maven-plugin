package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runAfterMethod;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runBeforeMethod;
import static org.junit.jupiter.api.condition.OS.LINUX;

import hudson.ExtensionList;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Result;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.trigger.WorkflowJobDependencyTrigger;
import org.jenkinsci.plugins.pipeline.maven.util.WorkflowMultibranchProjectTestsUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@EnabledOnOs(
        value = LINUX,
        disabledReason = "'fatal error: aux_index does not match even or odd indices' on Windows JDK 19")
public class DependencyGraphTest extends AbstractIntegrationTest {

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

    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_simple_pipeline_trigger() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadMavenWarProjectInGitRepo(this.downstreamArtifactRepoRule);

        // @formatter:off
        String mavenJarPipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        String mavenWarPipelineScript = "node() {\n" +
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

        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenJarPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenWarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-war");
        mavenWarPipeline.setDefinition(new CpsFlowDefinition(mavenWarPipelineScript, true));
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenWarPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenWarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded
        System.out.println("mavenWarPipelineFirstRun: " + mavenWarPipelineFirstRun);

        WorkflowRun mavenJarPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipeline.getLastBuild();
        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by "
                + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber()).isEqualTo(mavenWarPipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
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

        // @formatter:off
        String script = "node() {\n" +
            "    checkout scm\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on
        gitRepoRule.write("Jenkinsfile", script);
        gitRepoRule.git("add", "Jenkinsfile");
        gitRepoRule.git("commit", "--message=jenkinsfile");

        downstreamArtifactRepoRule.write("Jenkinsfile", script);
        downstreamArtifactRepoRule.git("add", "Jenkinsfile");
        downstreamArtifactRepoRule.git("commit", "--message=jenkinsfile");

        // TRIGGER maven-jar#1 to record that "build-maven-jar" generates this jar and
        // install this maven jar in the local maven repo
        WorkflowMultiBranchProject mavenJarPipeline =
                jenkinsRule.createProject(WorkflowMultiBranchProject.class, "build-maven-jar");
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        mavenJarPipeline.getSourcesList().add(new BranchSource(buildGitSCMSource(gitRepoRule.toString())));
        System.out.println("trigger maven-jar#1...");
        WorkflowJob mavenJarPipelineMasterPipeline =
                WorkflowMultibranchProjectTestsUtils.scheduleAndFindBranchProject(mavenJarPipeline, "master");
        assertThat(mavenJarPipeline.getItems()).hasSize(1);
        System.out.println("wait for maven-jar#1...");
        jenkinsRule.waitUntilNoActivity();

        assertThat(mavenJarPipelineMasterPipeline.getLastBuild().getNumber()).isEqualTo(1);
        // TODO check in DB that the generated artifact is recorded

        // TRIGGER maven-war#1 to record that "build-maven-war" has a dependency on
        // "build-maven-jar"
        WorkflowMultiBranchProject mavenWarPipeline =
                jenkinsRule.createProject(WorkflowMultiBranchProject.class, "build-maven-war");
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        mavenWarPipeline
                .getSourcesList()
                .add(new BranchSource(buildGitSCMSource(downstreamArtifactRepoRule.toString())));
        System.out.println("trigger maven-war#1...");
        WorkflowJob mavenWarPipelineMasterPipeline =
                WorkflowMultibranchProjectTestsUtils.scheduleAndFindBranchProject(mavenWarPipeline, "master");
        assertThat(mavenWarPipeline.getItems()).hasSize(1);
        System.out.println("wait for maven-war#1...");
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun mavenWarPipelineFirstRun = mavenWarPipelineMasterPipeline.getLastBuild();

        // TODO check in DB that the dependency on the war project is recorded

        // TRIGGER maven-jar#2 so that it triggers "maven-war" and creates maven-war#2
        System.out.println("trigger maven-jar#2...");
        Future<WorkflowRun> mavenJarPipelineMasterPipelineSecondRunFuture =
                mavenJarPipelineMasterPipeline.scheduleBuild2(
                        0, new CauseAction(new Cause.RemoteCause("127.0.0.1", "junit test")));
        System.out.println("wait for maven-jar#2...");
        mavenJarPipelineMasterPipelineSecondRunFuture.get();
        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipelineMasterPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by "
                + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber()).isEqualTo(mavenWarPipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }

    @Test
    public void verify_osgi_bundle_recorded_as_bundle_and_as_jar() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/multi_module_bundle_project/");

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

        // TRIGGER maven-jar#1 to record that "build-maven-jar"
        WorkflowJob multiModuleBundleProjectPipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-multi-module-bundle");
        multiModuleBundleProjectPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, multiModuleBundleProjectPipeline.scheduleBuild2(0));

        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao();
        List<MavenArtifact> generatedArtifacts =
                dao.getGeneratedArtifacts(multiModuleBundleProjectPipeline.getFullName(), build.getNumber());

        /*
         * [{skip_downstream_triggers=TRUE, type=pom,
         * gav=jenkins.mvn.test.bundle:bundle-parent:0.0.1-SNAPSHOT},
         * {skip_downstream_triggers=TRUE, type=bundle,
         * gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
         * {skip_downstream_triggers=TRUE, type=jar,
         * gav=jenkins.mvn.test.bundle:print-impl:0.0.1-SNAPSHOT},
         * {skip_downstream_triggers=TRUE, type=jar,
         * gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
         * {skip_downstream_triggers=TRUE, type=pom,
         * gav=jenkins.mvn.test.bundle:print-api:0.0.1-SNAPSHOT},
         * {skip_downstream_triggers=TRUE, type=pom,
         * gav=jenkins.mvn.test.bundle:print-impl:0.0.1-SNAPSHOT}]
         */

        System.out.println("generated artifacts" + generatedArtifacts);

        Iterable<String> matchingArtifactTypes = generatedArtifacts.stream()
                .filter(input -> input != null
                        && input.getGroupId().equals("jenkins.mvn.test.bundle")
                        && input.getArtifactId().equals("print-api")
                        && input.getVersion().equals("0.0.1-SNAPSHOT"))
                .map(MavenArtifact::getType)
                .collect(Collectors.toList());

        assertThat(matchingArtifactTypes).contains("jar", "bundle", "pom");
    }

    @Test
    public void verify_downstream_pipeline_triggered_on_parent_pom_build() throws Exception {
        loadSourceCodeInGitRepository(
                this.gitRepoRule, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_pom_project/");

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

        // TRIGGER maven-jar#1 to record that "build-maven-jar"
        WorkflowJob multiModuleBundleProjectPipeline =
                jenkinsRule.createProject(WorkflowJob.class, "build-multi-module-bundle");
        multiModuleBundleProjectPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, multiModuleBundleProjectPipeline.scheduleBuild2(0));

        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao();
        List<MavenArtifact> generatedArtifacts =
                dao.getGeneratedArtifacts(multiModuleBundleProjectPipeline.getFullName(), build.getNumber());

        Iterable<String> matchingArtifactTypes = generatedArtifacts.stream()
                .filter(input -> input != null
                        && input.getGroupId().equals("com.example")
                        && input.getArtifactId().equals("my-pom")
                        && input.getVersion().equals("0.1-SNAPSHOT"))
                .map(MavenArtifact::getType)
                .collect(Collectors.toList());

        assertThat(matchingArtifactTypes).contains("pom");
    }

    @Test
    public void verify_downstream_pipeline_triggered_on_jar_having_parent_pom_dependency() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadSourceCodeInGitRepository(
                this.gitRepoRule, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_pom_project/");
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadSourceCodeInGitRepository(
                this.downstreamArtifactRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_parent_pom_project/");

        // @formatter:off
        String mavenParentPipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        String mavenJarPipelineScript = "node() {\n" +
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

        WorkflowJob mavenParentPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-parent");
        mavenParentPipeline.setDefinition(new CpsFlowDefinition(mavenParentPipelineScript, true));
        mavenParentPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenParentPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenParentPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenJarPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded

        WorkflowRun mavenParentPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenParentPipeline.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenJarPipelineLastRun = mavenJarPipeline.getLastBuild();

        assertThat(mavenJarPipelineLastRun.getNumber()).isEqualTo(mavenJarPipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenJarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }

    /**
     * The maven-war-app has a dependency on the maven-jar-app
     */
    @Test
    public void verify_downstream_pipeline_triggered_on_war_having_jar_dependency() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadMavenJarProjectInGitRepo(this.gitRepoRule);
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadMavenWarProjectInGitRepo(this.downstreamArtifactRepoRule);

        // @formatter:off
        String mavenJarPipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        String mavenWarPipelineScript = "node() {\n" +
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

        WorkflowJob mavenJarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-jar");
        mavenJarPipeline.setDefinition(new CpsFlowDefinition(mavenJarPipelineScript, true));
        mavenJarPipeline.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenJarPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenWarPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-maven-war");
        mavenWarPipeline.setDefinition(new CpsFlowDefinition(mavenWarPipelineScript, true));
        mavenWarPipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenWarPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenWarPipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded
        System.out.println("mavenWarPipelineFirstRun: " + mavenWarPipelineFirstRun);

        WorkflowRun mavenJarPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenJarPipeline.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenWarPipelineLastRun = mavenWarPipeline.getLastBuild();

        System.out.println("mavenWarPipelineLastBuild: " + mavenWarPipelineLastRun + " caused by "
                + mavenWarPipelineLastRun.getCauses());

        assertThat(mavenWarPipelineLastRun.getNumber()).isEqualTo(mavenWarPipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenWarPipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }

    @Test
    public void verify_nbm_downstream_simple_pipeline_trigger() throws Exception {
        System.out.println("gitRepoRule: " + gitRepoRule);
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_nbm_dependency_project/");
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadSourceCodeInGitRepository(
                this.downstreamArtifactRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_nbm_base_project/");

        // @formatter:off
        String mavenNbmDependencyPipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install'\n" +
            "        } else {\n" +
            "            bat 'mvn install'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        String mavenNbmBasePipelineScript = "node() {\n" +
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

        WorkflowJob mavenNbmDependency = jenkinsRule.createProject(WorkflowJob.class, "build-nbm-dependency");
        mavenNbmDependency.setDefinition(new CpsFlowDefinition(mavenNbmDependencyPipelineScript, true));
        mavenNbmDependency.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenNbmDependencyPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenNbmDependency.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenNbmBasePipeline = jenkinsRule.createProject(WorkflowJob.class, "build-nbm-base");
        mavenNbmBasePipeline.setDefinition(new CpsFlowDefinition(mavenNbmBasePipelineScript, true));
        mavenNbmBasePipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenNbmBasePipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenNbmBasePipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the war project is recorded
        System.out.println("build-nbm-dependencyFirstRun: " + mavenNbmBasePipelineFirstRun);

        WorkflowRun mavenNbmDependencyPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenNbmDependency.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenNbmBasePipelineLastRun = mavenNbmBasePipeline.getLastBuild();

        System.out.println("build-nbm-baseLastBuild: " + mavenNbmBasePipelineLastRun + " caused by "
                + mavenNbmBasePipelineLastRun.getCauses());

        assertThat(mavenNbmBasePipelineLastRun.getNumber()).isEqualTo(mavenNbmBasePipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenNbmBasePipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }

    public boolean checkIsLinuxAndDockerSocketExists() {
        return OS.current() == OS.LINUX && Files.exists(Paths.get("/var/run/docker.sock"));
    }

    @Test
    @EnabledIf(
            value = "checkIsLinuxAndDockerSocketExists",
            disabledReason = "Needs Docker and Docker does not work on Windows 2019 servers CI agents")
    public void verify_docker_downstream_simple_pipeline_trigger() throws Exception {
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

    @Test
    public void verify_deployfile_downstream_simple_pipeline_trigger() throws Exception {
        PipelineGraphPublisher publisher = GlobalPipelineMavenConfig.get().getPublisherOptions().stream()
                .filter(p -> PipelineGraphPublisher.class.isInstance(p))
                .findFirst()
                .map(p -> (PipelineGraphPublisher) p)
                .get();
        publisher.setLifecycleThreshold("deploy");

        System.out.println("gitRepoRule: " + gitRepoRule);
        loadSourceCodeInGitRepository(
                this.gitRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_deployfile_dependency_project/");
        System.out.println("downstreamArtifactRepoRule: " + downstreamArtifactRepoRule);
        loadSourceCodeInGitRepository(
                this.downstreamArtifactRepoRule,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_deployfile_base_project/");

        // @formatter:off
        String mavenDeployFileDependencyPipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven() {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn install deploy:deploy-file@deploy-file'\n" +
            "        } else {\n" +
            "            bat 'mvn install deploy:deploy-file@deploy-file'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        String mavenDeployFileBasePipelineScript = "node() {\n" +
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

        WorkflowJob mavenDeployFileDependency =
                jenkinsRule.createProject(WorkflowJob.class, "build-deployfile-dependency");
        mavenDeployFileDependency.setDefinition(new CpsFlowDefinition(mavenDeployFileDependencyPipelineScript, true));
        mavenDeployFileDependency.addTrigger(new WorkflowJobDependencyTrigger());

        WorkflowRun mavenDeployFileDependencyPipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDeployFileDependency.scheduleBuild2(0));
        // TODO check in DB that the generated artifact is recorded

        WorkflowJob mavenDeployFileBasePipeline = jenkinsRule.createProject(WorkflowJob.class, "build-deployfile-base");
        mavenDeployFileBasePipeline.setDefinition(new CpsFlowDefinition(mavenDeployFileBasePipelineScript, true));
        mavenDeployFileBasePipeline.addTrigger(new WorkflowJobDependencyTrigger());
        WorkflowRun mavenDeployFileBasePipelineFirstRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDeployFileBasePipeline.scheduleBuild2(0));
        // TODO check in DB that the dependency on the jar project is recorded
        System.out.println("build-deployfile-dependencyFirstRun: " + mavenDeployFileBasePipelineFirstRun);

        WorkflowRun mavenDeployFileDependencyPipelineSecondRun =
                jenkinsRule.assertBuildStatus(Result.SUCCESS, mavenDeployFileDependency.scheduleBuild2(0));

        jenkinsRule.waitUntilNoActivity();

        WorkflowRun mavenDeployFileBasePipelineLastRun = mavenDeployFileBasePipeline.getLastBuild();

        System.out.println("build-deployfile-baseLastBuild: " + mavenDeployFileBasePipelineLastRun + " caused by "
                + mavenDeployFileBasePipelineLastRun.getCauses());

        assertThat(mavenDeployFileBasePipelineLastRun.getNumber())
                .isEqualTo(mavenDeployFileBasePipelineFirstRun.getNumber() + 1);
        Cause.UpstreamCause upstreamCause = mavenDeployFileBasePipelineLastRun.getCause(Cause.UpstreamCause.class);
        assertThat(upstreamCause).isNotNull();
    }

    private GitSCMSource buildGitSCMSource(String remote) {
        GitSCMSource gitSCMSource = new GitSCMSource(remote);
        List<SCMSourceTrait> traits = new ArrayList();
        traits.add(new BranchDiscoveryTrait());
        gitSCMSource.setTraits(traits);
        return gitSCMSource;
    }
}
