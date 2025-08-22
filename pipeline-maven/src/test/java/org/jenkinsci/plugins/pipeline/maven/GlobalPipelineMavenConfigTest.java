package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.mysql.cj.conf.RuntimeProperty;
import com.mysql.cj.jdbc.ConnectionImpl;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import hudson.ExtensionList;
import io.jenkins.plugins.prism.SourceCodeRetention;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.dao.CustomTypePipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.MonitoringPipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginPostgreSqlDao;
import org.jenkinsci.plugins.pipeline.maven.publishers.ConcordionTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.CoveragePublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesFingerprintPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.InvokerRunsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JGivenTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.WarningsPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.FakeCredentialsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
public class GlobalPipelineMavenConfigTest {

    @Container
    public static MySQLContainer<?> MYSQL_DB =
            new MySQLContainer<>("mysql:8.2.0").withUsername("aUser").withPassword("aPass");

    @Container
    public static PostgreSQLContainer<?> POSTGRE_DB = new PostgreSQLContainer<>(PostgreSQLContainer.IMAGE)
            .withUsername("aUser")
            .withPassword("aPass");

    @BeforeAll
    public static void setup(JenkinsRule r) {
        j = r;
    }

    private static JenkinsRule j;

    private GlobalPipelineMavenConfig config = new GlobalPipelineMavenConfig();

    @Test
    public void configRoundTrip() throws Exception {
        GlobalPipelineMavenConfig c = GlobalPipelineMavenConfig.get();
        c.setGlobalTraceability(true);
        c.setTriggerDownstreamUponResultAborted(true);
        c.setTriggerDownstreamUponResultFailure(true);
        c.setTriggerDownstreamUponResultNotBuilt(true);
        c.setTriggerDownstreamUponResultSuccess(false);
        c.setTriggerDownstreamUponResultUnstable(true);

        ConcordionTestsPublisher concordionTestsPublisher = new ConcordionTestsPublisher();
        concordionTestsPublisher.setDisabled(true);

        CoveragePublisher coveragePublisher = new CoveragePublisher();
        coveragePublisher.setDisabled(true);
        coveragePublisher.setCoberturaExtraPattern("coberturaPatterns");
        coveragePublisher.setJacocoExtraPattern("jacocoPatterns");
        coveragePublisher.setSourceCodeRetention(SourceCodeRetention.NEVER);

        DependenciesFingerprintPublisher dependenciesFingerprintPublisher = new DependenciesFingerprintPublisher();
        dependenciesFingerprintPublisher.setDisabled(true);
        dependenciesFingerprintPublisher.setIncludeReleaseVersions(true);
        dependenciesFingerprintPublisher.setIncludeScopeCompile(false);
        dependenciesFingerprintPublisher.setIncludeScopeProvided(true);
        dependenciesFingerprintPublisher.setIncludeScopeRuntime(true);
        dependenciesFingerprintPublisher.setIncludeScopeTest(true);

        GeneratedArtifactsPublisher generatedArtifactsPublisher = new GeneratedArtifactsPublisher();
        generatedArtifactsPublisher.setDisabled(true);

        InvokerRunsPublisher invokerRunsPublisher = new InvokerRunsPublisher();
        invokerRunsPublisher.setDisabled(true);

        JGivenTestsPublisher jGivenTestsPublisher = new JGivenTestsPublisher();
        jGivenTestsPublisher.setDisabled(true);

        JunitTestsPublisher junitTestsPublisher = new JunitTestsPublisher();
        junitTestsPublisher.setDisabled(true);
        junitTestsPublisher.setHealthScaleFactor(5.0);
        junitTestsPublisher.setIgnoreAttachments(true);
        junitTestsPublisher.setKeepLongStdio(true);

        MavenLinkerPublisher2 mavenLinkerPublisher = new MavenLinkerPublisher2();
        mavenLinkerPublisher.setDisabled(true);

        PipelineGraphPublisher pipelineGraphPublisher = new PipelineGraphPublisher();
        pipelineGraphPublisher.setDisabled(true);
        pipelineGraphPublisher.setIgnoreUpstreamTriggers(true);
        pipelineGraphPublisher.setIncludeReleaseVersions(true);
        pipelineGraphPublisher.setIncludeScopeCompile(true);
        pipelineGraphPublisher.setIncludeScopeProvided(true);
        pipelineGraphPublisher.setIncludeScopeRuntime(true);
        pipelineGraphPublisher.setIncludeScopeTest(true);
        pipelineGraphPublisher.setLifecycleThreshold("install");
        pipelineGraphPublisher.setSkipDownstreamTriggers(true);

        WarningsPublisher warningsPublisher = new WarningsPublisher();
        warningsPublisher.setDisabled(true);

        c.setPublisherOptions(List.of(
                concordionTestsPublisher,
                coveragePublisher,
                dependenciesFingerprintPublisher,
                generatedArtifactsPublisher,
                invokerRunsPublisher,
                jGivenTestsPublisher,
                junitTestsPublisher,
                mavenLinkerPublisher,
                pipelineGraphPublisher,
                warningsPublisher));

        j.configRoundtrip();

        j.assertEqualDataBoundBeans(c, GlobalPipelineMavenConfig.get());
    }

    @Test
    public void shouldBuildH2Dao() throws Exception {
        config.setDaoClass(PipelineMavenPluginH2Dao.class.getName());
        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao).isInstanceOf(MonitoringPipelineMavenPluginDaoDecorator.class);
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao).isInstanceOf(CustomTypePipelineMavenPluginDaoDecorator.class);
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao).isInstanceOf(PipelineMavenPluginH2Dao.class);
    }

    @Test
    public void shouldBuildMysqlDao() throws Exception {
        config.setDaoClass(PipelineMavenPluginMySqlDao.class.getName());
        ExtensionList<CredentialsProvider> extensionList =
                Jenkins.getInstance().getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider("credsId", "aUser", "aPass", false));
        config.setJdbcUrl(MYSQL_DB.getJdbcUrl());
        config.setJdbcCredentialsId("credsId");
        config.setProperties("maxLifetime=42000");

        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao).isInstanceOf(MonitoringPipelineMavenPluginDaoDecorator.class);
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao).isInstanceOf(CustomTypePipelineMavenPluginDaoDecorator.class);
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao).isInstanceOf(PipelineMavenPluginMySqlDao.class);
        Object ds = getField(innerDao, "ds");
        assertThat(ds).isInstanceOf(HikariDataSource.class);
        HikariDataSource datasource = (HikariDataSource) ds;
        assertThat(datasource.getJdbcUrl()).isEqualTo(MYSQL_DB.getJdbcUrl());
        assertThat(datasource.getUsername()).isEqualTo("aUser");
        assertThat(datasource.getPassword()).isEqualTo("aPass");
        assertThat(datasource.getMaxLifetime()).isEqualTo(42000L);
        assertThat(datasource.getDataSourceProperties()).containsKey("dataSource.cachePrepStmts");
        assertThat(datasource.getDataSourceProperties().getProperty("dataSource.cachePrepStmts"))
                .isEqualTo("true");
        assertThat(datasource.getDataSourceProperties()).containsKey("dataSource.prepStmtCacheSize");
        assertThat(datasource.getDataSourceProperties().getProperty("dataSource.prepStmtCacheSize"))
                .isEqualTo("250");
        Connection connection = datasource.getConnection();
        assertThat(connection).isInstanceOf(HikariProxyConnection.class);
        connection = (Connection) getField(connection, "delegate");
        assertThat(connection).isInstanceOf(ConnectionImpl.class);
        RuntimeProperty<Boolean> cachePrepStmts = (RuntimeProperty<Boolean>) getField(connection, "cachePrepStmts");
        assertThat(cachePrepStmts.getValue()).isTrue();
    }

    @Test
    public void shouldBuildPostgresqlDao() throws Exception {
        config.setDaoClass(PipelineMavenPluginPostgreSqlDao.class.getName());
        ExtensionList<CredentialsProvider> extensionList =
                Jenkins.getInstance().getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider("credsId", "aUser", "aPass", false));
        config.setJdbcUrl(POSTGRE_DB.getJdbcUrl());
        config.setJdbcCredentialsId("credsId");

        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao).isInstanceOf(MonitoringPipelineMavenPluginDaoDecorator.class);
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao).isInstanceOf(CustomTypePipelineMavenPluginDaoDecorator.class);
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao).isInstanceOf(PipelineMavenPluginPostgreSqlDao.class);
        Object ds = getField(innerDao, "ds");
        assertThat(ds).isInstanceOf(HikariDataSource.class);
        HikariDataSource datasource = (HikariDataSource) ds;
        assertThat(datasource.getJdbcUrl()).isEqualTo(POSTGRE_DB.getJdbcUrl());
        assertThat(datasource.getUsername()).isEqualTo("aUser");
        assertThat(datasource.getPassword()).isEqualTo("aPass");
    }

    private Object getField(Object targetObject, String name) throws Exception {
        Class<?> targetClass = targetObject.getClass();
        Field field = findField(targetClass, name, null);
        makeAccessible(field);
        return field.get(targetObject);
    }

    private Field findField(Class<?> clazz, String name, Class<?> type) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                if ((name == null || name.equals(field.getName())) && (type == null || type.equals(field.getType()))) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    private void makeAccessible(Field field) {
        if ((!Modifier.isPublic(field.getModifiers())
                        || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
                        || Modifier.isFinal(field.getModifiers()))
                && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }
}
