package org.jenkinsci.plugins.pipeline.maven;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.tasks.Maven;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class AbstractIntegrationTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    String mavenInstallationName;

    @Before
    public void setup() throws Exception {

        Maven.MavenInstallation mvn = configureDefaultMaven("3.6.3", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-3.6.3", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
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

    protected void loadMavenPomProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_pom_project/");
    }

    protected void loadMavenJarWithParentPomProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_parent_pom_project/");
    }

    protected void loadMavenJarWithJacocoInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_jacoco_project/");
    }

    protected void loadMavenWarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_war_project/");
    }

    protected void loadMavenJarWithFlattenPomProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_jar_with_flatten_pom_project/");
    }

    protected void loadOsgiBundleProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/multi_module_bundle_project/");
    }

    protected void loadJenkinsPluginProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_hpi_project/");
    }

    protected void loadMultiModuleProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/multi_module_maven_project/");
    }

    protected void loadNbmBaseMavenProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_nbm_base_project/");
    }

    protected void loadNbmDependencyMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_nbm_dependency_project/");
    }

    protected void loadDockerBaseMavenProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_docker_base_project/");
    }

    protected void loadDockerDependencyMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_docker_dependency_project/");
    }

    protected void loadDeployFileBaseMavenProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_deployfile_base_project/");
    }

    protected void loadDeployFileDependencyMavenJarProjectInGitRepo(GitSampleRepoRule gitRepo) throws Exception {
        loadSourceCodeInGitRepository(gitRepo, "/org/jenkinsci/plugins/pipeline/maven/test/test_maven_projects/maven_deployfile_dependency_project/");
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
            FilePath mvn = Jenkins.getInstance().getRootPath().createTempFile("maven", "zip");
            mvn.copyFrom(new URL("https://dlcdn.apache.org/maven/maven-3/" + mavenVersion + "/binaries/apache-maven-" + mavenVersion + "-bin.tar.gz"));
            mvn.untar(new FilePath(buildDirectory), FilePath.TarCompression.GZIP);
        }
        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }
}
