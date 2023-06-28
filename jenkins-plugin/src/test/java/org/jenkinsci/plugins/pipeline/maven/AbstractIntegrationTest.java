package org.jenkinsci.plugins.pipeline.maven;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import hudson.FilePath;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.util.MavenUtil;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Fingerprint;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Maven;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class AbstractIntegrationTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    String mavenInstallationName;

//    @Rule
//    public GenericContainer<?> sshContainerRule = new GenericContainer<>(
//            new ImageFromDockerfile("jenkins/sshd:32edfdd58111", true)
//                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/SshdContainer/Dockerfile"))
//                    .withExposedPorts(22);
//
//    @Rule
//    public GenericContainer<?> javaContainerRule = new GenericContainer<>(
//            new ImageFromDockerfile("jenkins/java:9a1fc28fe17f", true)
//                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/JavaContainer/Dockerfile"))
//                    .withExposedPorts(22);

    @Rule
    public GenericContainer<?> javaGitContainerRule = new GenericContainer<>(
            new ImageFromDockerfile("jenkins/java:f2055d7f7d61", true)
                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/JavaGitContainer/Dockerfile"))
                    .withExposedPorts(22);

    @Rule
    public GenericContainer<?> nonMavenContainerRule = new GenericContainer<>(
            new ImageFromDockerfile("jenkins/java:c64985b7a0da", true)
                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/NonMavenJavaContainer/Dockerfile"))
                    .withExposedPorts(22);

    @Rule
    public GenericContainer<?> mavenWithMavenHomeContainerRule = new GenericContainer<>(
            new ImageFromDockerfile("jenkins/java:7daff089469e", true)
                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/MavenWithMavenHomeJavaContainer/Dockerfile"))
                    .withExposedPorts(22);

    @Before
    public void setup() throws Exception {
        Maven.MavenInstallation mvn = configureDefaultMaven(MavenUtil.MAVEN_VERSION, Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-" + MavenUtil.MAVEN_VERSION, mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        mavenInstallationName = mvn.getName();

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());
    }

    public void after() throws IOException {
        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao();
        if (dao instanceof Closeable) {
            ((Closeable) dao).close();
        }
    }

    @Rule
    public GitSampleRepoRule gitRepoRule = new GitSampleRepoRule();

    protected void loadMonoDependencyMavenProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/mono_dependency_maven_jar_project/");
    }

    protected void loadMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_project/");
    }

    protected void loadMavenWarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_war_project/");
    }

    protected void loadSourceCodeInGitRepository(GitSampleRepoRule gitRepo, String name) throws Exception {
        gitRepo.init();
        Path mavenProjectRoot = Paths.get(WithMavenStepOnMasterTest.class.getResource(name).toURI());
        if (!Files.exists(mavenProjectRoot)) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot + "' not found");
        }
        GitSampleRepoRuleUtils.addFilesAndCommit(mavenProjectRoot, gitRepo);
    }

    protected Maven.MavenInstallation configureDefaultMaven(String mavenVersion, int mavenReqVersion) throws Exception {
        // first if we are running inside Maven, pick that Maven, if it meets the
        // criteria we require..
        FilePath buildDirectory = new FilePath(new File(System.getProperty("buildDirectory", "target"))); // TODO relative path
        FilePath mvnHome = new FilePath(new File(buildDirectory.getRemote(), "apache-maven-" + mavenVersion));
        if (!mvnHome.exists()) {
            FilePath mvn = buildDirectory.createTempFile("maven", "zip");
            mvn.copyFrom(Files.newInputStream(Paths.get(System.getProperty("buildDirectory", "target"), "apache-maven-"+mavenVersion+"-bin.zip")));
            mvn.unzip(buildDirectory);
        }
        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getRemote(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }

    protected void verifyFileIsFingerPrinted(WorkflowJob pipeline, WorkflowRun build, String fileName) throws java.io.IOException {
        Fingerprinter.FingerprintAction fingerprintAction = build.getAction(Fingerprinter.FingerprintAction.class);
        Map<String, String> records = fingerprintAction.getRecords();
        String jarFileMd5sum = records.get(fileName);
        assertThat(jarFileMd5sum, not(nullValue()));

        Fingerprint jarFileFingerPrint = jenkinsRule.getInstance().getFingerprintMap().get(jarFileMd5sum);
        assertThat(jarFileFingerPrint.getFileName(), is(fileName));
        assertThat(jarFileFingerPrint.getOriginal().getJob().getName(), is(pipeline.getName()));
        assertThat(jarFileFingerPrint.getOriginal().getNumber(), is(build.getNumber()));
    }
}
