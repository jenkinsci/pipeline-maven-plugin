package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.mysql.cj.conf.RuntimeProperty;
import com.mysql.cj.jdbc.ConnectionImpl;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import hudson.ExtensionList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.dao.CustomTypePipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.MonitoringPipelineMavenPluginDaoDecorator;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.db.PipelineMavenPluginPostgreSqlDao;
import org.jenkinsci.plugins.pipeline.maven.util.FakeCredentialsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true) // Testcontainers does not support docker on Windows 2019 servers
@WithJenkins
public class GlobalPipelineMavenConfigTest {

    @Container
    public static MySQLContainer<?> MYSQL_DB =
            new MySQLContainer<>(MySQLContainer.NAME).withUsername("aUser").withPassword("aPass");

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
