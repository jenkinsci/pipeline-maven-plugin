package org.jenkinsci.plugins.pipeline.maven;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.ItemGroup;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
public class NonProductionGradeDatabaseWarningAdministrativeMonitorIntegrationTest {

    @Container
    public static MySQLContainer<?> MYSQL_DB = new MySQLContainer<>(MySQLContainer.NAME).withUsername("aUser").withPassword("aPass");

    @Container
    public static PostgreSQLContainer<?> POSTGRE_DB = new PostgreSQLContainer<>(PostgreSQLContainer.IMAGE).withUsername("aUser").withPassword("aPass");

    private static class FakeCredentialsProvider extends CredentialsProvider {
        public FakeCredentialsProvider() {
        }

        @Override
        public boolean isEnabled(Object context) {
            return true;
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup, Authentication authentication,
                List<DomainRequirement> domainRequirements) {
            return (List<C>) asList(new UsernamePasswordCredentialsImpl(GLOBAL, "credsId", "", "aUser", "aPass"));
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup, Authentication authentication) {
            return getCredentials(type, itemGroup, authentication, null);
        }
    }

    @Test
    public void shouldMitigateComputationWithH2Database(JenkinsRule j) throws Exception {
        ExtensionList.lookupSingleton(GlobalPipelineMavenConfig.class).setDaoClass(PipelineMavenPluginH2Dao.class.getName());
        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.getInstance().getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);
        assertThat(monitor).isNotNull();

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isFalse();
        assertThat(monitor.isActivated()).isFalse();

        GlobalPipelineMavenConfig.get().setJdbcUrl("jdbc:h2:file:/tmp/some.file");

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isFalse();
        assertThat(monitor.isActivated()).isTrue();

        GlobalPipelineMavenConfig.get().setJdbcUrl(null);

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isFalse();
        assertThat(monitor.isActivated()).isFalse();

        GlobalPipelineMavenConfig.get().getDao();

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isTrue();
        assertThat(monitor.isActivated()).isFalse();

        for (int i = 1; i <= 101; i++) {
            GlobalPipelineMavenConfig.get().getDao().recordGeneratedArtifact("a job", i, "a.group.id", "anArtifactId", "1.0.0-SNAPSHOT", "jar",
                    "1.0.0-SNAPSHOT", null, false, "jar", null);
        }

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isTrue();

        assertThat(monitor.isActivated()).isTrue();
    }

    @Test
    public void shouldNotDisplayWarningWithMysqlDatabase(JenkinsRule j) throws Exception {
        ExtensionList<CredentialsProvider> extensionList = j.getInstance().getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        GlobalPipelineMavenConfig.get().setJdbcUrl(MYSQL_DB.getJdbcUrl());
        GlobalPipelineMavenConfig.get().setJdbcCredentialsId("credsId");

        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);

        assertThat(monitor).isNotNull();
        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isFalse();
        assertThat(monitor.isActivated()).isFalse();
    }

    @Test
    public void shouldNotDisplayWarningWithPostgresDatabase(JenkinsRule j) throws Exception {
        ExtensionList<CredentialsProvider> extensionList = j.getInstance().getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        GlobalPipelineMavenConfig.get().setJdbcUrl(POSTGRE_DB.getJdbcUrl());
        GlobalPipelineMavenConfig.get().setJdbcCredentialsId("credsId");

        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);

        assertThat(monitor).isNotNull();
        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized()).isFalse();
        assertThat(monitor.isActivated()).isFalse();
    }
}
