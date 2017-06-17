package org.jenkinsci.plugins.pipeline.maven.dao;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.TestUtils;
import org.jenkinsci.plugins.pipeline.maven.util.SqlTestsUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2DaoTest {

    private JdbcConnectionPool jdbcConnectionPool;

    private PipelineMavenPluginH2Dao dao;

    @Before
    public void before() {
        jdbcConnectionPool = JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");
        SqlTestsUtils.silentlyDeleteTableRows(jdbcConnectionPool, "JENKINS_BUILD", "MAVEN_ARTIFACT", "MAVEN_DEPENDENCY", "GENERATED_MAVEN_ARTIFACT");
        dao = new PipelineMavenPluginH2Dao(jdbcConnectionPool);

    }

    @Test
    public void getOrCreateArtifactPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar");
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar");

        assertThat(primaryKeySecondCall, is(primaryKey));

        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(1));
    }

    @Test
    public void getOrCreateJobPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);

        assertThat(primaryKeySecondCall, is(primaryKey));

        SqlTestsUtils.dump("select * from JENKINS_BUILD", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

    }

    @Test
    public void record_one_dependency() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(1));

        SqlTestsUtils.dump("select * from JENKINS_BUILD", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

    }

    @Test
    public void record_two_dependencies() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(2));

        SqlTestsUtils.dump("select * from JENKINS_BUILD", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

    }
}
