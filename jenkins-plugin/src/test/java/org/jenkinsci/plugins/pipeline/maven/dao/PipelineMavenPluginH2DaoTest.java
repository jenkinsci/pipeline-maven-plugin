package org.jenkinsci.plugins.pipeline.maven.dao;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.h2.jdbcx.JdbcConnectionPool;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.util.SqlTestsUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

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

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(1));
    }


    @Test
    public void rename_job() throws Exception {

        dao.recordDependency("my-pipeline-name-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");

        dao.renameJob("my-pipeline-name-1", "my-pipeline-name-2");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB WHERE FULL_NAME='my-pipeline-name-2'", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(1));
    }

    @Test
    public void delete_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        dao.deleteJob("my-pipeline");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(0));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(0));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(0));
    }

    @Test
    public void delete_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        dao.deleteBuild("my-pipeline", 2);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(2));
    }

    @Test
    public void move_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.renameJob("my-pipeline", "my-new-pipeline");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB where full_name='my-new-pipeline'", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(1));
    }

    @Test
    public void record_two_generated_artifacts_on_the_same_build() throws Exception {

        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar");
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "war");

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from GENERATED_MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from GENERATED_MAVEN_ARTIFACT", jdbcConnectionPool, System.out);

    }

    @Test
    public void record_two_dependencies_on_the_same_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(2));

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);

    }

    @Test
    public void record_two_dependencies_on_consecutive_builds_of_the_same_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);


        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(4));



    }

    @Test
    public void record_two_dependencies_on_two_jobs() throws Exception {

        dao.recordDependency("my-pipeline-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");
        dao.recordDependency("my-pipeline-2", 2, "com.h2database", "h2", "1.4.196", "jar", "compile");
        dao.recordDependency("my-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", jdbcConnectionPool, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", jdbcConnectionPool, System.out);


        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", jdbcConnectionPool),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", jdbcConnectionPool),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", jdbcConnectionPool),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", jdbcConnectionPool),
                is(4));



    }

    @Test
    public void list_downstream_jobs() {

        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar");
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war");

        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");


        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));


        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar");
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war");

        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile");
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile");

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
    }
}
