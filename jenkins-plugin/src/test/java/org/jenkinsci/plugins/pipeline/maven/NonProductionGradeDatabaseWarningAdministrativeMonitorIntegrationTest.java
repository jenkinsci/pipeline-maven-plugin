package org.jenkinsci.plugins.pipeline.maven;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.acegisecurity.Authentication;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;

public class NonProductionGradeDatabaseWarningAdministrativeMonitorIntegrationTest {

    @ClassRule
    public static MySQLContainer<?> MYSQL_DB = new MySQLContainer<>(MySQLContainer.NAME).withUsername("aUser")
            .withPassword("aPass");

    @ClassRule
    public static PostgreSQLContainer<?> POSTGRE_DB = new PostgreSQLContainer<>(PostgreSQLContainer.IMAGE)
            .withUsername("aUser").withPassword("aPass");

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private static class FakeCredentialsProvider extends CredentialsProvider {
        public FakeCredentialsProvider() {
        }

        @Override
        public boolean isEnabled(Object context) {
            return true;
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup,
                Authentication authentication, List<DomainRequirement> domainRequirements) {
            return (List<C>) asList(new UsernamePasswordCredentialsImpl(GLOBAL, "credsId", "", "aUser", "aPass"));
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup,
                Authentication authentication) {
            return getCredentials(type, itemGroup, authentication, null);
        }
    }

    @Test
    public void shouldMitigateComputationWithH2Database() throws Exception {
        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.jenkins
                .getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);
        assertThat(monitor, notNullValue());

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(false));
        assertThat(monitor.isActivated(), is(false));

        GlobalPipelineMavenConfig.get().setJdbcUrl("jdbc:h2:file:/tmp/some.file");

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(false));
        assertThat(monitor.isActivated(), is(true));

        GlobalPipelineMavenConfig.get().setJdbcUrl(null);

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(false));
        assertThat(monitor.isActivated(), is(false));

        GlobalPipelineMavenConfig.get().getDao();

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(true));
        assertThat(monitor.isActivated(), is(false));

        for (int i = 1; i <= 101; i++) {
            GlobalPipelineMavenConfig.get().getDao().recordGeneratedArtifact("a job", i, "a.group.id", "anArtifactId",
                    "1.0.0-SNAPSHOT", "jar", "1.0.0-SNAPSHOT", null, false, "jar", null);
        }

        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(true));
        assertThat(monitor.isActivated(), is(true));
    }

    @Test
    public void shouldNotDisplayWarningWithMysqlDatabase() throws Exception {
        ExtensionList<CredentialsProvider> extensionList = Jenkins.getInstance()
                .getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        GlobalPipelineMavenConfig.get().setJdbcUrl(MYSQL_DB.getJdbcUrl());
        GlobalPipelineMavenConfig.get().setJdbcCredentialsId("credsId");

        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.jenkins
                .getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);

        assertThat(monitor, notNullValue());
        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(false));
        assertThat(monitor.isActivated(), is(false));
    }

    @Test
    public void shouldNotDisplayWarningWithPostgresDatabase() throws Exception {
        ExtensionList<CredentialsProvider> extensionList = Jenkins.getInstance()
                .getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        GlobalPipelineMavenConfig.get().setJdbcUrl(POSTGRE_DB.getJdbcUrl());
        GlobalPipelineMavenConfig.get().setJdbcCredentialsId("credsId");

        NonProductionGradeDatabaseWarningAdministrativeMonitor monitor = j.jenkins
                .getExtensionList(AdministrativeMonitor.class)
                .get(NonProductionGradeDatabaseWarningAdministrativeMonitor.class);

        assertThat(monitor, notNullValue());
        assertThat(GlobalPipelineMavenConfig.get().isDaoInitialized(), is(false));
        assertThat(monitor.isActivated(), is(false));
    }
}
