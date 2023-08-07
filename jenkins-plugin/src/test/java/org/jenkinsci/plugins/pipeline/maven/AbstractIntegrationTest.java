package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runAfterMethod;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runBeforeMethod;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import hudson.FilePath;
import hudson.model.Fingerprint;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Maven;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.testcontainers.utility.DockerImageName;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Testcontainers
@WithJenkins
public abstract class AbstractIntegrationTest {

//    To build the parent image
//    sha1sum jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/SshdContainer/Dockerfile
//    docker build -f jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/SshdContainer/Dockerfile jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/ -t olamy/pipeline-maven-plugin:SshdContainer-32edfdd581112ff75ef6bbd6b0e576371f0089e3
//    docker push olamy/pipeline-maven-plugin:SshdContainer-32edfdd581112ff75ef6bbd6b0e576371f0089e3


//    To build this image
//    sha1sum jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/JavaGitContainer/Dockerfile
//    docker build -f jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/JavaGitContainer/Dockerfile jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/ -t olamy/pipeline-maven-plugin:JavaGitContainer-fe3065996fcbb067d17b078f414020db1d63afcc
//    docker push olamy/pipeline-maven-plugin:JavaGitContainer-fe3065996fcbb067d17b078f414020db1d63afcc
    @Container
    public GenericContainer<?> javaGitContainerRule =
            new GenericContainer<>(DockerImageName.parse("olamy/pipeline-maven-plugin:JavaGitContainer-fe3065996fcbb067d17b078f414020db1d63afcc"))
                .withExposedPorts(22);

//    to run from classpath file
//    @Container
//    public GenericContainer<?> javaGitContainerRule = new GenericContainer<>(new ImageFromDockerfile("jenkins/pipeline-maven-java-git", true)
//            .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/JavaGitContainer/Dockerfile")).withExposedPorts(22);

//    To build this image
//    sha1sum jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/NonMavenJavaContainer/Dockerfile
//    docker build -f jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/NonMavenJavaContainer/Dockerfile jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/ -t olamy/pipeline-maven-plugin:NonMavenJavaContainer-4810152e3b5d42b3ea25bfc7dc08cdd4da6aaf51
//    docker push olamy/pipeline-maven-plugin:NonMavenJavaContainer-4810152e3b5d42b3ea25bfc7dc08cdd4da6aaf51
    @Container
    public GenericContainer<?> nonMavenContainerRule =
            new GenericContainer<>(DockerImageName.parse("olamy/pipeline-maven-plugin:NonMavenJavaContainer-b13715c6c45cf3c01bc8a9340abd4017396e30bb"))
                    .withExposedPorts(22);
//    to run from classpath file
//    public GenericContainer<?> nonMavenContainerRule =
//            new GenericContainer<>(new ImageFromDockerfile("jenkins/pipeline-maven-non-maven", true)
//                    .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/NonMavenJavaContainer/Dockerfile")).withExposedPorts(22);


//    To build this image
//    sha1sum jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/MavenWithMavenHomeJavaContainer/Dockerfile
//    docker build -f jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/MavenWithMavenHomeJavaContainer/Dockerfile jenkins-plugin/src/test/resources/org/jenkinsci/plugins/pipeline/maven/docker/ -t olamy/pipeline-maven-plugin:MavenWithMavenHomeJavaContainer-8883e396ed7837d2b712865ffd460ef190dee600
//    docker push olamy/pipeline-maven-plugin:MavenWithMavenHomeJavaContainer-8883e396ed7837d2b712865ffd460ef190dee600

    @Container
    public GenericContainer<?> mavenWithMavenHomeContainerRule =
            new GenericContainer<>(DockerImageName.parse("olamy/pipeline-maven-plugin:MavenWithMavenHomeJavaContainer-e3e86a51761591f6036c6d5daf19d13284b5f205"))
                    .withExposedPorts(22);
//    to run from classpath file
//    @Container
//    public GenericContainer<?> mavenWithMavenHomeContainerRule = new GenericContainer<>(new ImageFromDockerfile("jenkins/pipeline-maven-java", true)
//            .withFileFromClasspath("Dockerfile", "org/jenkinsci/plugins/pipeline/maven/docker/MavenWithMavenHomeJavaContainer/Dockerfile"))
//            .withExposedPorts(22);

    public static BuildWatcher buildWatcher;

    public GitSampleRepoRule gitRepoRule;

    public JenkinsRule jenkinsRule;

    String mavenInstallationName;

    @BeforeAll
    public static void setupWatcher() {
        buildWatcher = new BuildWatcher();
        runBeforeMethod(buildWatcher);
    }

    @BeforeEach
    public void setup(JenkinsRule r) throws Exception {
        jenkinsRule = r;

        gitRepoRule = new GitSampleRepoRule();
        runBeforeMethod(gitRepoRule);

        Maven.MavenInstallation mvn = configureDefaultMaven("3.6.3", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-3.6.3", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        mavenInstallationName = mvn.getName();

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());
    }

    @AfterEach
    public void after() throws IOException {
        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao();
        if (dao instanceof Closeable) {
            dao.close();
        }

        runAfterMethod(gitRepoRule);
    }

    @AfterAll
    public static void stopWatcher() {
        runAfterMethod(buildWatcher);
    }

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
        File buildDirectory = new File(System.getProperty("buildDirectory", "target")); // TODO relative path
        File mvnHome = new File(buildDirectory, "apache-maven-" + mavenVersion);
        if (!mvnHome.exists()) {
            FilePath mvn = Jenkins.get().getRootPath().createTempFile("maven", "zip");
            mvn.copyFrom(new URL(
                    "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/" + mavenVersion + "/apache-maven-" + mavenVersion + "-bin.tar.gz"));
            mvn.untar(new FilePath(buildDirectory), FilePath.TarCompression.GZIP);
        }
        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }

    protected void verifyFileIsFingerPrinted(WorkflowJob pipeline, WorkflowRun build, String fileName) throws java.io.IOException {
        Fingerprinter.FingerprintAction fingerprintAction = build.getAction(Fingerprinter.FingerprintAction.class);
        Map<String, String> records = fingerprintAction.getRecords();
        String jarFileMd5sum = records.get(fileName);
        assertThat(jarFileMd5sum).isNotNull();

        Fingerprint jarFileFingerPrint = jenkinsRule.getInstance().getFingerprintMap().get(jarFileMd5sum);
        assertThat(jarFileFingerPrint.getFileName()).isEqualTo(fileName);
        assertThat(jarFileFingerPrint.getOriginal().getJob().getName()).isEqualTo(pipeline.getName());
        assertThat(jarFileFingerPrint.getOriginal().getNumber()).isEqualTo(build.getNumber());
    }
}
