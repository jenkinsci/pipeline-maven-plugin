package org.jenkinsci.plugins.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.jenkinsci.plugins.maven.WithMaven.LifecycleThreshold;
import org.jenkinsci.plugins.maven.WithMaven.PublisherStrategy;
import org.jenkinsci.plugins.maven.WithMaven.QualityGateCriticality;
import org.jenkinsci.plugins.maven.WithMaven.QualityGateType;
import org.jenkinsci.plugins.maven.WithMaven.SourceCodeRetention;
import org.jenkinsci.plugins.maven.WithMaven.TrendChartType;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.config_file_provider.ConfigFileProvider;
import org.jenkinsci.test.acceptance.plugins.config_file_provider.MavenSettingsConfig;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.po.JdkInstallation;
import org.jenkinsci.test.acceptance.po.ToolInstallation;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@WithPlugins("pipeline-maven")
public class JobSnippetGeneratorUiTest extends AbstractJUnitTest {

    private WorkflowJob job;

    @Before
    public void createWorkflowJobOnceAndGoToJenkinsHome() {
        if (job == null) {
            job = jenkins.getJobs().create(WorkflowJob.class);
            job.save();
        }
        jenkins.open();
    }

    @Test
    public void defaultConfigurationTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven();

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultConfigurationExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator
                .selectWithMaven()
                .setMaven("--- Use system default Maven ---")
                .setJDK("--- Use system default JDK ---")
                .setTempBinDir("")
                .setMavenSettingsConfig("")
                .setMavenSettingsFilePath("")
                .setGlobalMavenSettingsConfig("")
                .setGlobalMavenSettingsFilePath("")
                .setMavenOpts("")
                .setTraceability(true)
                .setMavenLocalRepo("")
                .setPublisherStrategy(PublisherStrategy.IMPLICIT);

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitMavenTest() {
        MavenInstallation.installMaven(jenkins, MavenInstallation.DEFAULT_MAVEN_ID, "3.6.3");
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setMaven(MavenInstallation.DEFAULT_MAVEN_ID);

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(maven: '%s', traceability: true) {
    // some block
}"""
                        .formatted(MavenInstallation.DEFAULT_MAVEN_ID));
    }

    @Test
    public void explicitJDKTest() throws InterruptedException {
        JdkInstallation jdk = ToolInstallation.addTool(jenkins, JdkInstallation.class);
        jdk.name.set("preinstalled");
        jdk.useNative();
        jdk.getPage().save();

        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();
        snippetGenerator.selectWithMaven().setJDK("preinstalled");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(jdk: 'preinstalled', traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitTempBinDirTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setTempBinDir("/path/to/tmp/dir");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(tempBinDir: '/path/to/tmp/dir', traceability: true) {
    // some block
}""");
    }

    @Test
    @Ignore("MavenSettingsConfig maps both Global settings and none Global settings")
    public void explicitMavenSettingsConfigTest() {
        MavenSettingsConfig mvnConfig = new ConfigFileProvider(jenkins).addFile(MavenSettingsConfig.class);
        mvnConfig.name("mvn settings");
        mvnConfig.content("some maven settings");
        mvnConfig.save();
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setMavenSettingsConfig("mvn settings");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(mavenSettingsConfig: '%s', traceability: true) {
    // some block
}"""
                        .formatted(mvnConfig.id()));
    }

    @Test
    public void explicitMavenSettingsFilePathTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setMavenSettingsFilePath("settings path");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(mavenSettingsFilePath: 'settings path', traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitGLobalMavenSettingsConfigTest() {
        GlobalMavenSettingsConfig mvnConfig = new ConfigFileProvider(jenkins).addFile(GlobalMavenSettingsConfig.class);
        mvnConfig.name("global mvn settings");
        mvnConfig.content("some global maven settings");
        mvnConfig.save();
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setGlobalMavenSettingsConfig("global mvn settings");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(globalMavenSettingsConfig: '%s', traceability: true) {
    // some block
}"""
                        .formatted(mvnConfig.id()));
    }

    @Test
    public void explicitGlobalMavenSettingsFilePathTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setGlobalMavenSettingsFilePath("global settings path");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(globalMavenSettingsFilePath: 'global settings path', traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitMavenOptsTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setMavenOpts("some maven options");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(mavenOpts: 'some maven options', traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitTraceabilityTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setTraceability(true);

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(traceability: true) {
    // some block
}""");

        snippetGenerator.selectWithMaven().setTraceability(false);

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(traceability: false) {
    // some block
}""");
    }

    @Test
    public void explicitMavenLocalRepoTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setMavenLocalRepo("/path/to/m2/repo");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(mavenLocalRepo: '/path/to/m2/repo', traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitPublisherStrategyTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().setPublisherStrategy(PublisherStrategy.EXPLICIT);

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(publisherStrategy: 'EXPLICIT', traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultConcordionPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Concordion Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [concordionPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultConcordionPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Concordion Publisher", p -> p.setDisabled(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [concordionPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitConcordionPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Concordion Publisher", p -> p.setDisabled(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [concordionPublisher(disabled: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultCoveragePublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Coverage Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [coveragePublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultCoveragePublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Coverage Publisher", p -> p.setDisabled(false)
                .setSourceCodeRetention(SourceCodeRetention.MODIFIED)
                .setCoberturaExtraPattern(null)
                .setJacocoExtraPattern(null));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [coveragePublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitCoveragePublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Coverage Publisher", p -> p.setDisabled(true)
                .setSourceCodeRetention(SourceCodeRetention.NEVER)
                .setCoberturaExtraPattern("extra cobertura pattern")
                .setJacocoExtraPattern("extra jacoco pattern"));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [coveragePublisher(coberturaExtraPattern: 'extra cobertura pattern', disabled: true, jacocoExtraPattern: 'extra jacoco pattern', sourceCodeRetention: 'NEVER')], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultDependenciesFingerprintPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Dependencies Fingerprint Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [dependenciesFingerprintPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultDependenciesFingerprintPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Dependencies Fingerprint Publisher", p -> p.setDisabled(false)
                .setIncludeSnapshotVersions(true)
                .setIncludeReleaseVersions(false)
                .setIncludeScopeCompile(true)
                .setIncludeScopeRuntime(true)
                .setIncludeScopeProvided(true)
                .setIncludeScopeTest(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [dependenciesFingerprintPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitDependenciesFingerprintPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Dependencies Fingerprint Publisher", p -> p.setDisabled(true)
                .setIncludeSnapshotVersions(false)
                .setIncludeReleaseVersions(true)
                .setIncludeScopeCompile(false)
                .setIncludeScopeRuntime(false)
                .setIncludeScopeProvided(false)
                .setIncludeScopeTest(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [dependenciesFingerprintPublisher(disabled: true, includeReleaseVersions: true, includeScopeCompile: false, includeScopeProvided: false, includeScopeRuntime: false, includeScopeTest: true, includeSnapshotVersions: false)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultGeneratedArtifactsPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Generated Artifacts Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [artifactsPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultGeneratedArtifactsPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Generated Artifacts Publisher", p -> p.setDisabled(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [artifactsPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitGeneratedArtifactsPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Generated Artifacts Publisher", p -> p.setDisabled(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [artifactsPublisher(disabled: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultInvokerPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Invoker Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [invokerPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultInvokerPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Invoker Publisher", p -> p.setDisabled(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [invokerPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitInvokerPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Invoker Publisher", p -> p.setDisabled(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [invokerPublisher(disabled: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultJGivenPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("JGiven Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [jgivenPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultJGivenPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("JGiven Publisher", p -> p.setDisabled(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo("""
withMaven(options: [jgivenPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitJGivenPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("JGiven Publisher", p -> p.setDisabled(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [jgivenPublisher(disabled: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultJunitPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Junit Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [junitPublisher(healthScaleFactor: 1.0)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultJunitPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Junit Publisher", p -> p.setDisabled(false)
                .setIgnoreAttachments(false)
                .setHealthScaleFactor("1.0")
                .setKeepLongStdio(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [junitPublisher(healthScaleFactor: 1.0)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitJunitPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Junit Publisher", p -> p.setDisabled(true)
                .setIgnoreAttachments(true)
                .setHealthScaleFactor("5.0")
                .setKeepLongStdio(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [junitPublisher(disabled: true, healthScaleFactor: 5.0, ignoreAttachments: true, keepLongStdio: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultMavenLinkerPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Maven Linker Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [mavenLinkerPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultMavenLinkerPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Maven Linker Publisher", p -> p.setDisabled(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [mavenLinkerPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitMavenLinkerPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Maven Linker Publisher", p -> p.setDisabled(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [mavenLinkerPublisher(disabled: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultPipelineGraphPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Pipeline Graph Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [pipelineGraphPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultPipelineGraphPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Pipeline Graph Publisher", p -> p.setDisabled(false)
                .setIncludeSnapshotVersions(true)
                .setIncludeReleaseVersions(false)
                .setIncludeScopeCompile(true)
                .setIncludeScopeRuntime(true)
                .setIncludeScopeProvided(true)
                .setIncludeScopeTest(false)
                .setLifecycleThreshold(LifecycleThreshold.DEPLOY)
                .setSkipDownstreamTriggers(false)
                .setIgnoreUpstreamTriggers(false));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [pipelineGraphPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitPipelineGraphPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Pipeline Graph Publisher", p -> p.setDisabled(true)
                .setIncludeSnapshotVersions(false)
                .setIncludeReleaseVersions(true)
                .setIncludeScopeCompile(false)
                .setIncludeScopeRuntime(false)
                .setIncludeScopeProvided(false)
                .setIncludeScopeTest(true)
                .setLifecycleThreshold(LifecycleThreshold.PACKAGE)
                .setSkipDownstreamTriggers(true)
                .setIgnoreUpstreamTriggers(true));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [pipelineGraphPublisher(disabled: true, ignoreUpstreamTriggers: true, includeReleaseVersions: true, includeScopeCompile: false, includeScopeProvided: false, includeScopeRuntime: false, includeScopeTest: true, includeSnapshotVersions: false, lifecycleThreshold: 'package', skipDownstreamTriggers: true)], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultWarningsPublisherTest() throws InterruptedException {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Warnings Publisher");

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [warningsPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void defaultWarningsPublisherExplicitTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Warnings Publisher", p -> p.setDisabled(false)
                .setSourceCodeEncoding("UTF-8")
                .setIsEnabledForFailure(true)
                .setIsBlameDisabled(true)
                .setTrendChartType(TrendChartType.TOOLS_ONLY)
                .setQualityGateThreshold(1)
                .setQualityGateType(QualityGateType.NEW)
                .setQualityGateCriticality(QualityGateCriticality.UNSTABLE)
                .setJavaIgnorePatterns("")
                .setHighPriorityTaskIdentifiers("FIXME")
                .setNormalPriorityTaskIdentifiers("TODO")
                .setTasksIncludePattern("**/*.java")
                .setTasksExcludePattern("**/target/**"));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [warningsPublisher()], traceability: true) {
    // some block
}""");
    }

    @Test
    public void explicitWarningsPublisherTest() {
        WithMavenSnippetGenerator snippetGenerator = createSnippetGenerator();

        snippetGenerator.selectWithMaven().addPublisher("Warnings Publisher", p -> p.setDisabled(true)
                .setSourceCodeEncoding("ISO-8859-1")
                .setIsEnabledForFailure(false)
                .setIsBlameDisabled(false)
                .setTrendChartType(TrendChartType.NONE)
                .setQualityGateThreshold(2)
                .setQualityGateType(QualityGateType.DELTA)
                .setQualityGateCriticality(QualityGateCriticality.FAILURE)
                .setJavaIgnorePatterns("**/*Test.java")
                .setHighPriorityTaskIdentifiers("FIX")
                .setNormalPriorityTaskIdentifiers("TO-DO")
                .setTasksIncludePattern("*.java")
                .setTasksExcludePattern("target"));

        assertThat(snippetGenerator.generateScript())
                .isEqualTo(
                        """
withMaven(options: [warningsPublisher(disabled: true, enabledForFailure: false, highPriorityTaskIdentifiers: 'FIX', javaIgnorePatterns: '**/*Test.java', normalPriorityTaskIdentifiers: 'TO-DO', qualityGateCriticality: 'FAILURE', qualityGateThreshold: 2, qualityGateType: 'DELTA', skipBlames: false, sourceCodeEncoding: 'ISO-8859-1', tasksExcludePattern: 'target', tasksIncludePattern: '*.java', trendChartType: 'NONE')], traceability: true) {
    // some block
}""");
    }

    private WithMavenSnippetGenerator createSnippetGenerator() {
        WithMavenSnippetGenerator snippetGenerator = new WithMavenSnippetGenerator(job);
        snippetGenerator.open();
        elasticSleep(2000);

        return snippetGenerator;
    }
}
