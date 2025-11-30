package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runAfterMethod;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runBeforeMethod;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Fingerprint;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Maven;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.jenkinsci.plugins.pipeline.maven.util.MavenUtil;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.ExecInContainerPattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@WithJenkins
public abstract class AbstractIntegrationTest {

    public static BuildWatcher buildWatcher;

    public GitSampleRepoRule gitRepoRule;

    public JenkinsRule jenkinsRule;

    String mavenInstallationName;

    public static GenericContainer<?> createContainer(String target) {
        return new GenericContainer<>(new ImageFromDockerfile(
                                "localhost/pipeline-maven/" + target, Boolean.parseBoolean(System.getenv("CI")))
                        .withFileFromClasspath(".", "/org/jenkinsci/plugins/pipeline/maven/docker")
                        .withTarget(target))
                .withExposedPorts(22);
    }

    protected static final String SSH_CREDENTIALS_ID = "test";
    protected static final String AGENT_NAME = "remote";
    protected static final String SLAVE_BASE_PATH = "/home/test/slave";

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

        Maven.MavenInstallation mvn =
                MavenUtil.configureDefaultMaven(Jenkins.get().getRootPath());
        Maven.MavenInstallation m3 =
                new Maven.MavenInstallation("apache-maven-3", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        mavenInstallationName = mvn.getName();

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());
    }

    @AfterEach
    public void after() throws IOException {
        Objects.requireNonNull(GlobalPipelineMavenConfig.get()).getDao().close();
        runAfterMethod(gitRepoRule);
    }

    @AfterAll
    public static void stopWatcher() {
        runAfterMethod(buildWatcher);
    }

    protected void loadMonoDependencyMavenProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(
                gitRepo,
                "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/mono_dependency_maven_jar_project/");
    }

    protected void loadMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(
                gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_project/");
    }

    protected void loadMavenWarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(
                gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_war_project/");
    }

    protected void loadSourceCodeInGitRepository(GitSampleRepoRule gitRepo, String name) throws Exception {
        gitRepo.init();
        Path mavenProjectRoot =
                Paths.get(WithMavenStepOnMasterITest.class.getResource(name).toURI());
        if (!Files.exists(mavenProjectRoot)) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot + "' not found");
        }
        GitSampleRepoRuleUtils.addFilesAndCommit(mavenProjectRoot, gitRepo);
    }

    protected String exposeGitRepositoryIntoAgent(GenericContainer<?> container)
            throws UnsupportedOperationException, IOException, InterruptedException {
        String containerPath = "/tmp/gitrepo";
        String gitRepoPath = this.gitRepoRule.toString();
        container.copyFileToContainer(MountableFile.forHostPath(gitRepoPath), containerPath);
        container.execInContainer("chmod", "-R", "777", containerPath);
        System.out.println(ExecInContainerPattern.execInContainer(
                container.getDockerClient(),
                container.getContainerInfo(),
                ExecConfig.builder()
                        .user(SSH_CREDENTIALS_ID)
                        .command(new String[] {
                            "git", "config", "--global", "--add", "safe.directory", containerPath + "/.git"
                        })
                        .build()));
        return containerPath;
    }

    protected void verifyFileIsFingerPrinted(WorkflowJob pipeline, WorkflowRun build, String fileName)
            throws java.io.IOException {
        Fingerprinter.FingerprintAction fingerprintAction = build.getAction(Fingerprinter.FingerprintAction.class);
        Map<String, String> records = fingerprintAction.getRecords();
        String jarFileMd5sum = records.get(fileName);
        assertThat(jarFileMd5sum).isNotNull();

        Fingerprint jarFileFingerPrint =
                jenkinsRule.getInstance().getFingerprintMap().get(jarFileMd5sum);
        assertThat(jarFileFingerPrint.getFileName()).isEqualTo(fileName);
        assertThat(jarFileFingerPrint.getOriginal().getJob().getName()).isEqualTo(pipeline.getName());
        assertThat(jarFileFingerPrint.getOriginal().getNumber()).isEqualTo(build.getNumber());
    }

    protected void registerAgentForContainer(GenericContainer<?> container) throws Exception {
        addTestSshCredentials();
        registerAgentForSlaveContainer(container);
    }

    private void addTestSshCredentials() throws Exception {
        Credentials credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, SSH_CREDENTIALS_ID, null, SSH_CREDENTIALS_ID, SSH_CREDENTIALS_ID);

        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }

    private void registerAgentForSlaveContainer(GenericContainer<?> slaveContainer) throws Exception {
        SSHLauncher sshLauncher =
                new SSHLauncher(slaveContainer.getHost(), slaveContainer.getMappedPort(22), SSH_CREDENTIALS_ID);
        sshLauncher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());

        DumbSlave agent = new DumbSlave(AGENT_NAME, SLAVE_BASE_PATH, sshLauncher);
        agent.setNumExecutors(1);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);
        jenkinsRule.jenkins.addNode(agent);
    }
}
