package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.dao.MonitoringPipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginMySqlDao;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.jenkins.plugins.casc.misc.Util.*;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
public class ConfigurationAsCodeNeedDockerTest {

    @Container
    public static MySQLContainer<?> MYSQL_DB = new MySQLContainer<>(MySQLContainer.NAME).withUsername("aUser").withPassword("aPass");

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

}
