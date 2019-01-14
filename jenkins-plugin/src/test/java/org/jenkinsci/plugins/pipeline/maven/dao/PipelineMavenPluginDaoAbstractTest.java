/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven.dao;

import hudson.model.Result;
import org.h2.jdbcx.JdbcConnectionPool;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.util.SqlTestsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class PipelineMavenPluginDaoAbstractTest {

    protected DataSource ds;

    protected AbstractPipelineMavenPluginDao dao;

    @Before
    public void before() {
        ds = before_newDataSource();
        SqlTestsUtils.silentlyDeleteTableRows(ds, "JENKINS_MASTER", "JENKINS_JOB", "JENKINS_BUILD", "MAVEN_ARTIFACT", "MAVEN_DEPENDENCY", "GENERATED_MAVEN_ARTIFACT");
        dao = before_newAbstractPipelineMavenPluginDao(ds);
    }

    @After
    public void after() throws IOException {
        if (dao instanceof Closeable) {
            ((Closeable) dao).close();
        }
        if(ds instanceof Closeable) {
            ((Closeable) ds).close();
        }
    }

    @Nonnull
    public abstract DataSource before_newDataSource();

    @Nonnull
    public abstract AbstractPipelineMavenPluginDao before_newAbstractPipelineMavenPluginDao(DataSource ds);

    @Test
    public void getOrCreateArtifactPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar", null);
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar", null);

        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        assertThat(primaryKeySecondCall, is(primaryKey));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
    }

    @Test
    public void getOrCreateArtifactPrimaryKey_jarWithDependencies() throws Exception {

        long primaryKey = dao.getOrCreateArtifactPrimaryKey("com.example", "my-bundle", "1.2.3", "jar", "jar-with-dependencies");
        System.out.println(primaryKey);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);

        long primaryKeySecondCall = dao.getOrCreateArtifactPrimaryKey("com.example", "my-bundle", "1.2.3", "jar", "jar-with-dependencies");

        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        assertThat(primaryKeySecondCall, is(primaryKey));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
    }

    @Test
    public void getOrCreateJobPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);

        assertThat(primaryKeySecondCall, is(primaryKey));

        SqlTestsUtils.dump("select * from JENKINS_BUILD", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

    }

    @Test
    public void create_job_and_2_builds() throws Exception {

        // create job and first build
        dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);
        // complete first build
        dao.updateBuildOnCompletion("my-pipeline", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 50, 11);

        // create second build
        dao.getOrCreateBuildPrimaryKey("my-pipeline", 2);
        // complete second build
        dao.updateBuildOnCompletion("my-pipeline", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 22);


        SqlTestsUtils.dump("select * from JENKINS_JOB", ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_BUILD", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB where FULL_NAME='my-pipeline' AND LAST_BUILD_NUMBER=2 AND LAST_SUCCESSFUL_BUILD_NUMBER=2", ds),
                is(1));


        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(2));

    }

    @Test
    public void create_job_and_3_builds_and_delete_builds() throws Exception {

        // create job and first build
        dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);
        // complete first build
        dao.updateBuildOnCompletion("my-pipeline", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 50, 11);

        // create second build
        dao.getOrCreateBuildPrimaryKey("my-pipeline", 2);
        // complete second build
        dao.updateBuildOnCompletion("my-pipeline", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 22);

        // create third build
        dao.getOrCreateBuildPrimaryKey("my-pipeline", 3);
        // complete third build
        dao.updateBuildOnCompletion("my-pipeline", 3, Result.UNSTABLE.ordinal, System.currentTimeMillis() - 10, 33);

        SqlTestsUtils.dump("select * from JENKINS_JOB", ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_BUILD", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(3));

        dao.deleteBuild("my-pipeline", 1);
        // AFTER DELETE FIRST
        SqlTestsUtils.dump("select * from JENKINS_JOB", ds, System.out);

        dao.deleteBuild("my-pipeline", 3);
        System.out.println("AFTER DELETE LAST BUILD");
        SqlTestsUtils.dump("select * from JENKINS_JOB", ds, System.out);

    }


    @Test
    public void record_one_dependency() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2.database", "h2", "1.4.196", "jar", "compile", false, null);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(1));

        List<MavenDependency> mavenDependencies = dao.listDependencies("my-pipeline", 1);
        assertThat(mavenDependencies.size(), is(1));
        MavenDependency dependency = mavenDependencies.get(0);

        assertThat(dependency.getGroupId(), is("com.h2.database"));
        assertThat(dependency.getArtifactId(), is("h2"));
        assertThat(dependency.getVersion(), is("1.4.196"));
        assertThat(dependency.getType(), is("jar"));
        assertThat(dependency.getScope(), is("compile"));

    }

    @Test
    public void record_one_parent_project() throws Exception {

        dao.recordParentProject("my-pipeline", 1, "org.springframework.boot", "spring-boot-starter-parent", "1.5.4.RELEASE", false);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_PARENT_PROJECT", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_PARENT_PROJECT", ds),
                is(1));
    }

    @Test
    public void rename_job() throws Exception {

        dao.recordDependency("my-pipeline-name-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);

        dao.renameJob("my-pipeline-name-1", "my-pipeline-name-2");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB WHERE FULL_NAME='my-pipeline-name-2'", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(1));
    }

    @Test
    public void delete_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        dao.deleteJob("my-pipeline");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(0));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(0));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(0));
    }

    @Test
    public void delete_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        dao.deleteBuild("my-pipeline", 2);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(2));
    }

    @Test
    public void move_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.renameJob("my-pipeline", "my-new-pipeline");

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB where full_name='my-new-pipeline'", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(1));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(1));
    }

    @Test
    public void record_two_generated_artifacts_on_the_same_build() throws Exception {

        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from GENERATED_MAVEN_ARTIFACT", ds),
                is(2));

        List<MavenArtifact> generatedArtifacts = dao.getGeneratedArtifacts("my-upstream-pipeline-1", 1);
        assertThat(
                generatedArtifacts.size(),
                is(2));
        for(MavenArtifact generatedArtifact: generatedArtifacts) {
            assertThat(generatedArtifact.getGroupId(), is("com.mycompany"));
            assertThat(generatedArtifact.getArtifactId(), is("core"));
            assertThat(generatedArtifact.getBaseVersion(), is("1.0-SNAPSHOT"));
            assertThat(generatedArtifact.getType(), Matchers.isIn(Arrays.asList("war", "jar")));
            assertThat(generatedArtifact.getExtension(), Matchers.isIn(Arrays.asList("war", "jar")));
        }

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from GENERATED_MAVEN_ARTIFACT", ds, System.out);

    }

    @Test
    public void record_two_dependencies_on_the_same_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(2));

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

    }

    @Test
    public void record_two_dependencies_on_consecutive_builds_of_the_same_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);


        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(1));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(4));



    }

    @Test
    public void record_two_dependencies_on_two_jobs() throws Exception {

        dao.recordDependency("my-pipeline-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline-2", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);


        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_JOB", ds),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds),
                is(2));

        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds),
                is(2));
        assertThat(
                SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds),
                is(4));



    }

    @Deprecated
    @Test
    public void listDownstreamJobs_upstream_jar_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);


        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));


        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
    }

    @Test
    public void listDownstreamJobsByArtifact_upstream_jar_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);


        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }
    }

    @Test
    public void listDownstreamJobsByArtifact_doesnt_return_artifacts_with_no_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "upstream-shared", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "upstream-1", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "upstream-2", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordDependency("my-upstream-pipeline-1", 1, "com.mycompany", "upstream-shared", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "upstream-1", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("upstream-1");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);
            System.out.println(downstreamJobsByArtifactForBuild1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }
    }

    @Test
    public void listDownstreamPipelinesBasedOnMavenDependencies_noBaseVersion() {
        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "dependency-1", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "dependency-1", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 2222, 22);

        SortedSet<String> downstreamJobs = dao.listDownstreamJobs("com.mycompany", "dependency-1", "1.0-SNAPSHOT", null, "jar");
        System.out.println(downstreamJobs);
        assertThat(downstreamJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));
    }

    @Test
    public void listDownstreamPipelinesBasedOnMavenDependencies_withBaseVersion() {
        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "dependency-1", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "dependency-1", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 2222, 22);

        SortedSet<String> downstreamJobs = dao.listDownstreamJobs("com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT",  "jar");
        System.out.println(downstreamJobs);
        assertThat(downstreamJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));
    }


    @Deprecated
    @Test
    public void listDownstreamJobs_upstream_pom_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pom-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pom-pipeline-1", 1, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT","pom", "1.0-SNAPSHOT", null, false, "pom", null);
        dao.updateBuildOnCompletion("my-upstream-pom-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject("my-downstream-pipeline-1", 2, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT INNER JOIN MAVEN_PARENT_PROJECT ON MAVEN_ARTIFACT.ID = MAVEN_PARENT_PROJECT.ARTIFACT_ID", ds, System.out);

        List<String> downstreamJobs = dao.listDownstreamJobs("my-upstream-pom-pipeline-1", 1);
        assertThat(downstreamJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
        System.out.println(downstreamJobs);
    }

    @Test
    public void listDownstreamJobsbyArtifact_upstream_pom_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pom-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pom-pipeline-1", 1, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT","pom", "1.0-SNAPSHOT", null, false, "pom", null);
        dao.updateBuildOnCompletion("my-upstream-pom-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject("my-downstream-pipeline-1", 2, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        SqlTestsUtils.dump("select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT INNER JOIN MAVEN_PARENT_PROJECT ON MAVEN_ARTIFACT.ID = MAVEN_PARENT_PROJECT.ARTIFACT_ID", ds, System.out);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany.pom");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pom-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(actualJobs.size(), is(1));
            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_ignoreUpstreamTriggers_activated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 111);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", true, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);


        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1, Matchers.containsInAnyOrder("my-downstream-pipeline-2"));


        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
    }

    @Test
    public void list_downstream_jobs_by_artifact_with_ignoreUpstreamTriggers_activated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 111);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", true, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-2"));

            assertThat(actualJobs.size(), is(1));
            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(actualJobs.size(), is(1));
            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_skippedDownstreamTriggersActivated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, true, "jar", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);


        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 123, 5);


        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1, Matchers.hasSize(0));


        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
    }

    @Test
    public void list_downstream_jobs_by_artifact_with_skippedDownstreamTriggersActivated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, true, "jar", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);


        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "shared", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 123, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);
            assertThat(downstreamJobsByArtifactForBuild1.size(), is(0));
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-SNAPSHOT", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
            assertThat(actualJobs.size(), is(1));
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-20170808.155524-63", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-20170808.155524-64", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);


        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-50, 22);


        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));


        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-20170808.155524-65", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-20170808.155524-66", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));
    }

    @Test
    public void list_downstream_jobs_by_artifact_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "core", "1.0-20170808.155524-63", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "service", "1.0-20170808.155524-64", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency("my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency("my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-50, 22);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-20170808.155524-63");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "core", "1.1-20170808.155524-65", "jar", "1.1-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "service", "1.1-20170808.155524-66", "war", "1.1-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency("my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency("my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-20170808.155524-65");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
            assertThat(actualJobs.size(), is(1));
        }
    }

    @Issue("JENKINS-55566")
    @Test
    public void list_downstream_jobs_by_parent_pom_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 1, "com.mycompany", "parent-pom", "1.0-20170808.155524-63", "pom", "1.0-SNAPSHOT", null, false, "pom", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordParentProject("my-downstream-pipeline-1", 1, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordParentProject("my-downstream-pipeline-2", 1, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-50, 22);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setVersion("1.0-20170808.155524-63");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1", "my-downstream-pipeline-2"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact("my-upstream-pipeline-1", 2, "com.mycompany", "parent-pom", "1.1-20170808.155524-65", "pom", "1.1-SNAPSHOT", null, false, "pom", null);
        dao.updateBuildOnCompletion("my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject("my-downstream-pipeline-1", 2, "com.mycompany", "parent-pom", "1.1-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordParentProject("my-downstream-pipeline-2", 2, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion("my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis()-20, 9);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setVersion("1.1-20170808.155524-65");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 = dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("my-downstream-pipeline-1"));

            assertThat(downstreamJobsByArtifactForBuild1.size(), is(1));
            assertThat(actualJobs.size(), is(1));
        }
    }

    @Test
    public void list_upstream_pipelines_based_on_maven_dependencies() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact("pipeline-framework", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency("pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-core", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 = dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core", 1);
        assertThat(upstreamPipelinesForBuild1.keySet(), Matchers.containsInAnyOrder("pipeline-framework"));

    }

    @Test
    public void list_upstream_pipelines_based_on_parent_project() {

        dao.getOrCreateBuildPrimaryKey("pipeline-parent-pom", 1);
        dao.recordGeneratedArtifact("pipeline-parent-pom", 1, "com.mycompany", "company-parent-pom", "1.0-SNAPSHOT", "pom", "1.0-SNAPSHOT", null, false, "pom", null);
        dao.updateBuildOnCompletion("pipeline-parent-pom", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordParentProject("pipeline-core", 1, "com.mycompany", "company-parent-pom", "1.0-SNAPSHOT", false);
        dao.recordDependency("pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-core", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 = dao.listUpstreamPipelinesBasedOnParentProjectDependencies("pipeline-core", 1);
        assertThat(upstreamPipelinesForBuild1.keySet(), Matchers.containsInAnyOrder("pipeline-parent-pom"));

    }


    @Test
    public void list_transitive_upstream_jobs() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact("pipeline-framework", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency("pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-core", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-service", 1);
        dao.recordDependency("pipeline-service", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("pipeline-service", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-service", 1, "com.mycompany", "service", "1.0-SNAPSHOT", "war", "1.0-SNAPSHOT", null, false, "war", null);
        dao.updateBuildOnCompletion("pipeline-service", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 22);


        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 = dao.listTransitiveUpstreamJobs("pipeline-service", 1);
        assertThat(upstreamPipelinesForBuild1.keySet(), Matchers.containsInAnyOrder("pipeline-framework", "pipeline-core"));
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_failed_last_build() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact("pipeline-framework", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency("pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-core", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        {
            List<String> downstreamJobs = dao.listDownstreamJobs("pipeline-framework", 1);
            assertThat(downstreamJobs, Matchers.containsInAnyOrder("pipeline-core"));

        }

        // pipeline-core#2 fails before dependencies have been tracked
        dao.getOrCreateBuildPrimaryKey("pipeline-core", 2);
        dao.updateBuildOnCompletion("pipeline-core", 2, Result.FAILURE.ordinal, System.currentTimeMillis()-100, 11);

        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);

        {
            List<String> downstreamJobs = dao.listDownstreamJobs("pipeline-framework", 1);
            assertThat(downstreamJobs, Matchers.containsInAnyOrder("pipeline-core"));

        }
    }
    @Test
    public void list_downstream_jobs_by_artifact_with_failed_last_build() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact("pipeline-framework", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.recordGeneratedArtifact("pipeline-framework", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", "sources");
        dao.updateBuildOnCompletion("pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency("pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact("pipeline-core", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "1.0-SNAPSHOT", null, false, "jar", null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis()-100, 11);

        MavenArtifact expectedMavenArtifact = new MavenArtifact();
        expectedMavenArtifact.setGroupId("com.mycompany");
        expectedMavenArtifact.setArtifactId("framework");
        expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setType("jar");
        expectedMavenArtifact.setExtension("jar");

        {
            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact = dao.listDownstreamJobsByArtifact("pipeline-framework", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifact.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("pipeline-core"));
            assertThat(downstreamJobsByArtifact.size(), is(1));

        }

        // pipeline-core#2 fails before dependencies have been tracked
        dao.getOrCreateBuildPrimaryKey("pipeline-core", 2);
        dao.updateBuildOnCompletion("pipeline-core", 2, Result.FAILURE.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);

        {
            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact = dao.listDownstreamJobsByArtifact("pipeline-framework", 1);
            SortedSet<String> actualJobs = downstreamJobsByArtifact.get(expectedMavenArtifact);
            assertThat(actualJobs, Matchers.containsInAnyOrder("pipeline-core"));
            assertThat(downstreamJobsByArtifact.size(), is(1));
        }
    }
}
