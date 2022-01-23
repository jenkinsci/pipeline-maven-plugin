package org.jenkinsci.plugins.pipeline.maven;

import static io.jenkins.plugins.casc.misc.Util.getToolRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsNull.nullValue;

import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.junit.Rule;
import org.junit.Test;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;

public class ConfigurationAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code_default.yml")
    public void should_support_default_configuration() throws Exception {
        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getJdbcUrl(), nullValue());
        assertThat(config.getJdbcCredentialsId(), nullValue());
        assertThat(config.getProperties(), nullValue());
        assertThat(config.isTriggerDownstreamUponResultAborted(), is(false));
        assertThat(config.isTriggerDownstreamUponResultFailure(), is(false));
        assertThat(config.isTriggerDownstreamUponResultNotBuilt(), is(false));
        assertThat(config.isTriggerDownstreamUponResultUnstable(), is(false));
        assertThat(config.isTriggerDownstreamUponResultSuccess(), is(true));
        assertThat(config.getPublisherOptions(), nullValue());

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_default.yml");

        assertThat(exported, is(expected));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code_triggers.yml")
    public void should_support_triggers_configuration() throws Exception {
        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.isTriggerDownstreamUponResultAborted(), is(true));
        assertThat(config.isTriggerDownstreamUponResultFailure(), is(true));
        assertThat(config.isTriggerDownstreamUponResultNotBuilt(), is(true));
        assertThat(config.isTriggerDownstreamUponResultUnstable(), is(true));
        assertThat(config.isTriggerDownstreamUponResultSuccess(), is(false));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_triggers.yml");

        assertThat(exported, is(expected));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code_mysql.yml")
    public void should_support_mysql_configuration() throws Exception {
        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getJdbcUrl(), is("jdbc:mysql://dbserver/jenkinsdb"));
        assertThat(config.getProperties(), is("dataSource.cachePrepStmts=true\ndataSource.prepStmtCacheSize=250\n"));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_mysql.yml");

        assertThat(exported, is(expected));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code_postgresql.yml")
    public void should_support_postgresql_configuration() throws Exception {
        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getJdbcUrl(), is("jdbc:postgresql://dbserver/jenkinsdb"));
        assertThat(config.getJdbcCredentialsId(), is("pg-creds"));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_postgresql.yml");

        assertThat(exported, is(expected));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code_publishers.yml")
    public void should_support_publishers_configuration() throws Exception {
        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getPublisherOptions(), hasSize(1));
        assertThat(config.getPublisherOptions().get(0), isA(MavenLinkerPublisher2.class));
        MavenLinkerPublisher2 publisher = (MavenLinkerPublisher2) config.getPublisherOptions().get(0);
        assertThat(publisher.isDisabled(), is(true));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
        String expected = toStringFromYamlFile(this, "expected_output_publishers.yml");

        assertThat(exported, is(expected));
    }
}
