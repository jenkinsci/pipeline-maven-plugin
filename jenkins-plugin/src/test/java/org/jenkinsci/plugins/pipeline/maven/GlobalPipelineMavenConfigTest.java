package org.jenkinsci.plugins.pipeline.maven;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.List;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pipeline.maven.dao.CustomTypePipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.MonitoringPipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginPostgreSqlDao;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.mysql.cj.conf.RuntimeProperty;
import com.mysql.cj.jdbc.ConnectionImpl;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariProxyConnection;

import hudson.ExtensionList;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;

public class GlobalPipelineMavenConfigTest {

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

    private GlobalPipelineMavenConfig config = new GlobalPipelineMavenConfig();

    @Test
    public void shouldBuildH2Dao() throws Exception {
        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao, instanceOf(MonitoringPipelineMavenPluginDaoDecorator.class));
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao, instanceOf(CustomTypePipelineMavenPluginDaoDecorator.class));
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao, instanceOf(PipelineMavenPluginH2Dao.class));
    }

    @Test
    public void shouldBuildMysqlDao() throws Exception {
        ExtensionList<CredentialsProvider> extensionList = Jenkins.getInstance()
                .getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        config.setJdbcUrl(MYSQL_DB.getJdbcUrl());
        config.setJdbcCredentialsId("credsId");
        config.setProperties("maxLifetime=42000");

        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao, instanceOf(MonitoringPipelineMavenPluginDaoDecorator.class));
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao, instanceOf(CustomTypePipelineMavenPluginDaoDecorator.class));
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao, instanceOf(PipelineMavenPluginMySqlDao.class));
        Object ds = getField(innerDao, "ds");
        assertThat(ds, instanceOf(HikariDataSource.class));
        HikariDataSource datasource = (HikariDataSource) ds;
        assertThat(datasource.getJdbcUrl(), equalTo(MYSQL_DB.getJdbcUrl()));
        assertThat(datasource.getUsername(), equalTo("aUser"));
        assertThat(datasource.getPassword(), equalTo("aPass"));
        assertThat(datasource.getMaxLifetime(), is(42000L));
        assertThat(datasource.getDataSourceProperties().containsKey("dataSource.cachePrepStmts"), is(true));
        assertThat(datasource.getDataSourceProperties().getProperty("dataSource.cachePrepStmts"), is("true"));
        assertThat(datasource.getDataSourceProperties().containsKey("dataSource.prepStmtCacheSize"), is(true));
        assertThat(datasource.getDataSourceProperties().getProperty("dataSource.prepStmtCacheSize"), is("250"));
        Connection connection = datasource.getConnection();
        assertThat(connection, instanceOf(HikariProxyConnection.class));
        connection = (Connection) getField(connection, "delegate");
        assertThat(connection, instanceOf(ConnectionImpl.class));
        RuntimeProperty<Boolean> cachePrepStmts = (RuntimeProperty<Boolean>) getField(connection, "cachePrepStmts");
        assertThat(cachePrepStmts.getValue(), is(true));
    }

    @Test
    public void shouldBuildPostgresqlDao() throws Exception {
        ExtensionList<CredentialsProvider> extensionList = Jenkins.getInstance()
                .getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider());
        config.setJdbcUrl(POSTGRE_DB.getJdbcUrl());
        config.setJdbcCredentialsId("credsId");

        PipelineMavenPluginDao dao = config.getDao();

        assertThat(dao, instanceOf(MonitoringPipelineMavenPluginDaoDecorator.class));
        Object innerDao = getField(dao, "delegate");
        assertThat(innerDao, instanceOf(CustomTypePipelineMavenPluginDaoDecorator.class));
        innerDao = getField(innerDao, "delegate");
        assertThat(innerDao, instanceOf(PipelineMavenPluginPostgreSqlDao.class));
        Object ds = getField(innerDao, "ds");
        assertThat(ds, instanceOf(HikariDataSource.class));
        HikariDataSource datasource = (HikariDataSource) ds;
        assertThat(datasource.getJdbcUrl(), equalTo(POSTGRE_DB.getJdbcUrl()));
        assertThat(datasource.getUsername(), equalTo("aUser"));
        assertThat(datasource.getPassword(), equalTo("aPass"));
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
        if ((!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
                || Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }
}
