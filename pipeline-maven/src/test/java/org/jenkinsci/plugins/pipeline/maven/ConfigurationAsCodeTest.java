package org.jenkinsci.plugins.pipeline.maven;

import static io.jenkins.plugins.casc.misc.Util.getToolRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.prism.SourceCodeRetention;
import org.jenkinsci.plugins.pipeline.maven.publishers.ConcordionTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.CoveragePublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesFingerprintPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.InvokerRunsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JGivenTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.SpotBugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.TasksScannerPublisher;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class ConfigurationAsCodeTest {

    @Test
    public void should_support_default_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get()
                .configure(singletonList(getClass()
                        .getResource("configuration-as-code_default.yml")
                        .toExternalForm()));

        GlobalPipelineMavenConfig config =
                r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.isGlobalTraceability()).isFalse();
        assertThat(config.getJdbcUrl()).isNull();
        assertThat(config.getJdbcCredentialsId()).isNull();
        assertThat(config.getProperties()).isNull();
        assertThat(config.isTriggerDownstreamUponResultAborted()).isFalse();
        assertThat(config.isTriggerDownstreamUponResultFailure()).isFalse();
        assertThat(config.isTriggerDownstreamUponResultNotBuilt()).isFalse();
        assertThat(config.isTriggerDownstreamUponResultUnstable()).isFalse();
        assertThat(config.isTriggerDownstreamUponResultSuccess()).isTrue();
        assertThat(config.getPublisherOptions()).isNull();

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_default.yml");

        assertThat(exported).isEqualTo(expected);
    }

    @Test
    public void should_support_global_traceability(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get()
                .configure(singletonList(getClass()
                        .getResource("configuration-as-code_traceability.yml")
                        .toExternalForm()));

        GlobalPipelineMavenConfig config =
                r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.isGlobalTraceability()).isTrue();

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_traceability.yml");

        assertThat(exported).isEqualTo(expected);
    }

    @Test
    public void should_support_triggers_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get()
                .configure(singletonList(getClass()
                        .getResource("configuration-as-code_triggers.yml")
                        .toExternalForm()));

        GlobalPipelineMavenConfig config =
                r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.isTriggerDownstreamUponResultAborted()).isTrue();
        assertThat(config.isTriggerDownstreamUponResultFailure()).isTrue();
        assertThat(config.isTriggerDownstreamUponResultNotBuilt()).isTrue();
        assertThat(config.isTriggerDownstreamUponResultUnstable()).isTrue();
        assertThat(config.isTriggerDownstreamUponResultSuccess()).isFalse();

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_triggers.yml");

        assertThat(exported).isEqualTo(expected);
    }

    @Test
    public void should_support_postgresql_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get()
                .configure(singletonList(getClass()
                        .getResource("configuration-as-code_postgresql.yml")
                        .toExternalForm()));

        GlobalPipelineMavenConfig config =
                r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getJdbcUrl()).isEqualTo("theJdbcUrl");
        assertThat(config.getJdbcCredentialsId()).isEqualTo("credsId");

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_postgresql.yml");

        assertThat(exported).isEqualTo(expected);
    }

    @Test
    public void should_support_publishers_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get()
                .configure(singletonList(getClass()
                        .getResource("configuration-as-code_publishers.yml")
                        .toExternalForm()));

        GlobalPipelineMavenConfig config =
                r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getPublisherOptions()).hasSize(12);

        assertThat(config.getPublisherOptions().get(0)).isInstanceOf(ConcordionTestsPublisher.class);
        ConcordionTestsPublisher concordionPublisher =
                (ConcordionTestsPublisher) config.getPublisherOptions().get(0);
        assertThat(concordionPublisher.isDisabled()).isTrue();

        assertThat(config.getPublisherOptions().get(1)).isInstanceOf(CoveragePublisher.class);
        CoveragePublisher coveragePublisher =
                (CoveragePublisher) config.getPublisherOptions().get(1);
        assertThat(coveragePublisher.isDisabled()).isTrue();
        assertThat(coveragePublisher.getCoberturaExtraPattern()).isEqualTo("coberturaPatterns");
        assertThat(coveragePublisher.getJacocoExtraPattern()).isEqualTo("jacocoPatterns");
        assertThat(coveragePublisher.getSourceCodeRetention()).isEqualTo(SourceCodeRetention.NEVER);

        assertThat(config.getPublisherOptions().get(2)).isInstanceOf(DependenciesFingerprintPublisher.class);
        DependenciesFingerprintPublisher dependenciesFingerprintPublisher =
                (DependenciesFingerprintPublisher) config.getPublisherOptions().get(2);
        assertThat(dependenciesFingerprintPublisher.isDisabled()).isTrue();
        assertThat(dependenciesFingerprintPublisher.isIncludeReleaseVersions()).isTrue();
        assertThat(dependenciesFingerprintPublisher.isIncludeScopeCompile()).isFalse();
        assertThat(dependenciesFingerprintPublisher.isIncludeScopeProvided()).isTrue();
        assertThat(dependenciesFingerprintPublisher.isIncludeScopeRuntime()).isTrue();
        assertThat(dependenciesFingerprintPublisher.isIncludeScopeTest()).isTrue();

        assertThat(config.getPublisherOptions().get(3)).isInstanceOf(FindbugsAnalysisPublisher.class);
        FindbugsAnalysisPublisher findBugsPublisher =
                (FindbugsAnalysisPublisher) config.getPublisherOptions().get(3);
        assertThat(findBugsPublisher.isDisabled()).isTrue();
        assertThat(findBugsPublisher.getHealthy()).isEqualTo("5");
        assertThat(findBugsPublisher.getThresholdLimit()).isEqualTo("high");
        assertThat(findBugsPublisher.getUnHealthy()).isEqualTo("15");

        assertThat(config.getPublisherOptions().get(4)).isInstanceOf(GeneratedArtifactsPublisher.class);
        GeneratedArtifactsPublisher generatedArtifactsPublisher =
                (GeneratedArtifactsPublisher) config.getPublisherOptions().get(4);
        assertThat(generatedArtifactsPublisher.isDisabled()).isTrue();

        assertThat(config.getPublisherOptions().get(5)).isInstanceOf(InvokerRunsPublisher.class);
        InvokerRunsPublisher invokerRunsPublisher =
                (InvokerRunsPublisher) config.getPublisherOptions().get(5);
        assertThat(invokerRunsPublisher.isDisabled()).isTrue();

        assertThat(config.getPublisherOptions().get(6)).isInstanceOf(JGivenTestsPublisher.class);
        JGivenTestsPublisher jGivenTestsPublisher =
                (JGivenTestsPublisher) config.getPublisherOptions().get(6);
        assertThat(jGivenTestsPublisher.isDisabled()).isTrue();

        assertThat(config.getPublisherOptions().get(7)).isInstanceOf(JunitTestsPublisher.class);
        JunitTestsPublisher junitTestsPublisher =
                (JunitTestsPublisher) config.getPublisherOptions().get(7);
        assertThat(junitTestsPublisher.isDisabled()).isTrue();
        assertThat(junitTestsPublisher.getHealthScaleFactor()).isEqualTo(5.0);
        assertThat(junitTestsPublisher.getIgnoreAttachments()).isTrue();
        assertThat(junitTestsPublisher.isKeepLongStdio()).isTrue();

        assertThat(config.getPublisherOptions().get(8)).isInstanceOf(MavenLinkerPublisher2.class);
        MavenLinkerPublisher2 mavenLinkerPublisher =
                (MavenLinkerPublisher2) config.getPublisherOptions().get(8);
        assertThat(mavenLinkerPublisher.isDisabled()).isTrue();

        assertThat(config.getPublisherOptions().get(9)).isInstanceOf(TasksScannerPublisher.class);
        TasksScannerPublisher tasksScannerPublisher =
                (TasksScannerPublisher) config.getPublisherOptions().get(9);
        assertThat(tasksScannerPublisher.isDisabled()).isTrue();
        assertThat(tasksScannerPublisher.isAsRegexp()).isTrue();
        assertThat(tasksScannerPublisher.getExcludePattern()).isEqualTo("**/*.xml");
        assertThat(tasksScannerPublisher.getHealthy()).isEqualTo("5");
        assertThat(tasksScannerPublisher.getHighPriorityTaskIdentifiers()).isEqualTo("task1,task2");
        assertThat(tasksScannerPublisher.isIgnoreCase()).isTrue();
        assertThat(tasksScannerPublisher.getLowPriorityTaskIdentifiers()).isEqualTo("task4");
        assertThat(tasksScannerPublisher.getNormalPriorityTaskIdentifiers()).isEqualTo("task3");
        assertThat(tasksScannerPublisher.getPattern()).isEqualTo("**/*.java");
        assertThat(tasksScannerPublisher.getThresholdLimit()).isEqualTo("normal");
        assertThat(tasksScannerPublisher.getUnHealthy()).isEqualTo("15");

        assertThat(config.getPublisherOptions().get(10)).isInstanceOf(PipelineGraphPublisher.class);
        PipelineGraphPublisher pipelineGraphPublisher =
                (PipelineGraphPublisher) config.getPublisherOptions().get(10);
        assertThat(pipelineGraphPublisher.isDisabled()).isTrue();
        assertThat(pipelineGraphPublisher.isIgnoreUpstreamTriggers()).isTrue();
        assertThat(pipelineGraphPublisher.isIncludeReleaseVersions()).isTrue();
        assertThat(pipelineGraphPublisher.isIncludeScopeCompile()).isTrue();
        assertThat(pipelineGraphPublisher.isIncludeScopeProvided()).isTrue();
        assertThat(pipelineGraphPublisher.isIncludeScopeRuntime()).isTrue();
        assertThat(pipelineGraphPublisher.isIncludeScopeTest()).isTrue();
        assertThat(pipelineGraphPublisher.getLifecycleThreshold()).isEqualTo("install");
        assertThat(pipelineGraphPublisher.isSkipDownstreamTriggers()).isTrue();

        assertThat(config.getPublisherOptions().get(11)).isInstanceOf(SpotBugsAnalysisPublisher.class);
        SpotBugsAnalysisPublisher spotBugsAnalysisPublisher =
                (SpotBugsAnalysisPublisher) config.getPublisherOptions().get(11);
        assertThat(spotBugsAnalysisPublisher.isDisabled()).isTrue();
        assertThat(spotBugsAnalysisPublisher.getHealthy()).isEqualTo("5");
        assertThat(spotBugsAnalysisPublisher.getThresholdLimit()).isEqualTo("high");
        assertThat(spotBugsAnalysisPublisher.getUnHealthy()).isEqualTo("15");

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_publishers.yml");

        assertThat(exported).isEqualTo(expected);
    }
}
