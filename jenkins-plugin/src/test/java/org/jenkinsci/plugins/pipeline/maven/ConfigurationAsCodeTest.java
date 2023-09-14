package org.jenkinsci.plugins.pipeline.maven;

import static io.jenkins.plugins.casc.misc.Util.getToolRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.ExtensionList;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.dao.MonitoringPipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Files;
import java.nio.file.Path;

@WithJenkins
public class ConfigurationAsCodeTest {

    @Container
    public static MySQLContainer<?> MYSQL_DB = new MySQLContainer<>(MySQLContainer.NAME).withUsername("aUser").withPassword("aPass");

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
    public void should_support_mysql_configuration(JenkinsRule r) throws Exception {

        try {
            MYSQL_DB.start();
            ExtensionList<CredentialsProvider> extensionList = r.jenkins.getExtensionList(CredentialsProvider.class);
            extensionList.add(extensionList.size(), new GlobalPipelineMavenConfigTest.FakeCredentialsProvider());
            String jdbcUrl = MYSQL_DB.getJdbcUrl();

            String yamlContent = toStringFromYamlFile(this, "configuration-as-code_mysql.yml");
            yamlContent = StringUtils.replace(yamlContent, "theJdbcUrl", jdbcUrl);

            Path tmpYml = Files.createTempFile("pipeline-maven-plugin-test", "yml");
            Files.write(tmpYml, yamlContent.getBytes());

            ConfigurationAsCode.get().configure(singletonList(tmpYml.toFile().getAbsolutePath()));

            GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

            assertThat(config.getJdbcUrl()).isEqualTo(jdbcUrl);
            assertThat(config.getProperties()).isEqualTo("dataSource.cachePrepStmts=true\ndataSource.prepStmtCacheSize=250\n");
            assertThat(config.getDaoClass()).isEqualTo(PipelineMavenPluginMySqlDao.class.getName());

            // we can't really test the PipelineMavenPluginMySqlDao is used as it is plenty of layers
            // which doesn't expose the real implementation
            assertThat(config.getDao().getClass()).isNotEqualTo(PipelineMavenPluginMySqlDao.class);
            assertThat(config.getDao().getClass()).isNotEqualTo(PipelineMavenPluginNullDao.class);
            assertThat(config.getDao().getClass()).isEqualTo(MonitoringPipelineMavenPluginDaoDecorator.class);

            ConfiguratorRegistry registry = ConfiguratorRegistry.get();
            ConfigurationContext context = new ConfigurationContext(registry);
            String exported = toYamlString(getToolRoot(context).get("pipelineMaven"));
            String expected = toStringFromYamlFile(this, "expected_output_mysql.yml");
            expected = StringUtils.replace(expected, "theJdbcUrl", jdbcUrl);
            assertThat(exported).isEqualTo(expected);
        } finally {
            MYSQL_DB.stop();
        }
    }

    @Test
    public void should_support_postgresql_configuration(JenkinsRule r) throws Exception {
        ConfigurationAsCode.get().configure(singletonList(getClass().getResource("configuration-as-code_postgresql.yml").toExternalForm()));

        GlobalPipelineMavenConfig config = r.jenkins.getExtensionList(GlobalPipelineMavenConfig.class).get(0);

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://dbserver/jenkinsdb");
        assertThat(config.getJdbcCredentialsId()).isEqualTo("pg-creds");

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
