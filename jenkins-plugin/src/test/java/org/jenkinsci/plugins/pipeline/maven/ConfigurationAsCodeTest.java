package org.jenkinsci.plugins.pipeline.maven;

import static io.jenkins.plugins.casc.misc.Util.getToolRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;

@WithJenkins
public class ConfigurationAsCodeTest {

    @Test
    public void should_support_default_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_default.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

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
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_traceability.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.isGlobalTraceability()).isTrue();

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_traceability.yml");

        assertThat(exported).isEqualTo(expected);
    }

    @Test
    public void should_support_triggers_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_triggers.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

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
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_postgresql.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

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
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_publishers.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getPublisherOptions()).hasSize(1);
        assertThat(config.getPublisherOptions().get(0)).isInstanceOf(MavenLinkerPublisher2.class);
        MavenLinkerPublisher2 publisher = (MavenLinkerPublisher2) config.getPublisherOptions().get(0);
        assertThat(publisher.isDisabled()).isTrue();

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_publishers.yml");

        assertThat(exported).isEqualTo(expected);
    }
}
