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

package org.jenkinsci.plugins.pipeline.maven.db;

import static org.assertj.core.api.Assertions.assertThat;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.db.util.SqlTestsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class PipelineMavenPluginDaoAbstractTest {

    protected DataSource ds;

    protected AbstractPipelineMavenPluginDao dao;

    @BeforeEach
    public void before() throws Exception {
        ds = before_newDataSource();
        SqlTestsUtils.silentlyDeleteTableRows(
                ds,
                "JENKINS_MASTER",
                "JENKINS_JOB",
                "JENKINS_BUILD",
                "MAVEN_ARTIFACT",
                "MAVEN_DEPENDENCY",
                "GENERATED_MAVEN_ARTIFACT");
        dao = before_newAbstractPipelineMavenPluginDao(ds);
    }

    @AfterEach
    public void after() throws IOException {
        if (dao instanceof Closeable) {
            ((Closeable) dao).close();
        }
        if (ds instanceof Closeable) {
            ((Closeable) ds).close();
        }
    }

    @NonNull
    public abstract DataSource before_newDataSource() throws Exception;

    @NonNull
    public abstract AbstractPipelineMavenPluginDao before_newAbstractPipelineMavenPluginDao(DataSource ds);

    @Test
    public void getOrCreateArtifactPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar", null);
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateArtifactPrimaryKey("com.h2database", "h2", "1.4.196", "jar", null);

        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        assertThat(primaryKeySecondCall).isEqualTo(primaryKey);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
    }

    @Test
    public void getOrCreateArtifactPrimaryKey_jarWithDependencies() throws Exception {

        long primaryKey =
                dao.getOrCreateArtifactPrimaryKey("com.example", "my-bundle", "1.2.3", "jar", "jar-with-dependencies");
        System.out.println(primaryKey);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);

        long primaryKeySecondCall =
                dao.getOrCreateArtifactPrimaryKey("com.example", "my-bundle", "1.2.3", "jar", "jar-with-dependencies");

        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        assertThat(primaryKeySecondCall).isEqualTo(primaryKey);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
    }

    @Test
    public void getOrCreateJobPrimaryKey() throws Exception {

        long primaryKey = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);
        System.out.println(primaryKey);

        long primaryKeySecondCall = dao.getOrCreateBuildPrimaryKey("my-pipeline", 1);

        assertThat(primaryKeySecondCall).isEqualTo(primaryKey);

        SqlTestsUtils.dump("select * from JENKINS_BUILD", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);
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

        assertThat(SqlTestsUtils.countRows(
                        "select * from JENKINS_JOB where FULL_NAME='my-pipeline' AND LAST_BUILD_NUMBER=2 AND LAST_SUCCESSFUL_BUILD_NUMBER=2",
                        ds))
                .isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(2);
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

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(3);

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

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(1);

        List<MavenDependency> mavenDependencies = dao.listDependencies("my-pipeline", 1);
        assertThat(mavenDependencies).hasSize(1);
        MavenDependency dependency = mavenDependencies.get(0);

        assertThat(dependency.getGroupId()).isEqualTo("com.h2.database");
        assertThat(dependency.getArtifactId()).isEqualTo("h2");
        assertThat(dependency.getVersion()).isEqualTo("1.4.196");
        assertThat(dependency.getType()).isEqualTo("jar");
        assertThat(dependency.getScope()).isEqualTo("compile");
    }

    @Test
    public void record_one_parent_project() throws Exception {

        dao.recordParentProject(
                "my-pipeline", 1, "org.springframework.boot", "spring-boot-starter-parent", "1.5.4.RELEASE", false);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_PARENT_PROJECT", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_PARENT_PROJECT", ds))
                .isEqualTo(1);
    }

    @Test
    public void rename_job() throws Exception {

        dao.recordDependency("my-pipeline-name-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);

        dao.renameJob("my-pipeline-name-1", "my-pipeline-name-2");

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB WHERE FULL_NAME='my-pipeline-name-2'", ds))
                .isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(1);
    }

    @Test
    public void delete_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        dao.deleteJob("my-pipeline");

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(0);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(0);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(0);
    }

    @Test
    public void delete_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        dao.deleteBuild("my-pipeline", 2);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(2);
    }

    @Test
    public void move_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.renameJob("my-pipeline", "my-new-pipeline");

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);
        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB where full_name='my-new-pipeline'", ds))
                .isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(1);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(1);
    }

    @Test
    public void record_two_generated_artifacts_on_the_same_build() throws Exception {

        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from GENERATED_MAVEN_ARTIFACT", ds))
                .isEqualTo(2);

        List<MavenArtifact> generatedArtifacts = dao.getGeneratedArtifacts("my-upstream-pipeline-1", 1);
        assertThat(generatedArtifacts).hasSize(2);
        for (MavenArtifact generatedArtifact : generatedArtifacts) {
            assertThat(generatedArtifact.getGroupId()).isEqualTo("com.mycompany");
            assertThat(generatedArtifact.getArtifactId()).isEqualTo("core");
            assertThat(generatedArtifact.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
            assertThat(generatedArtifact.getType()).isIn("war", "jar");
            assertThat(generatedArtifact.getExtension()).isIn("war", "jar");
        }

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from GENERATED_MAVEN_ARTIFACT", ds, System.out);
    }

    @Test
    public void record_two_dependencies_on_the_same_build() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(2);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);
    }

    @Test
    public void record_two_dependencies_on_consecutive_builds_of_the_same_job() throws Exception {

        dao.recordDependency("my-pipeline", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(1);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(2);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(4);
    }

    @Test
    public void record_two_dependencies_on_two_jobs() throws Exception {

        dao.recordDependency("my-pipeline-1", 1, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency(
                "my-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency("my-pipeline-2", 2, "com.h2database", "h2", "1.4.196", "jar", "compile", false, null);
        dao.recordDependency(
                "my-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump("select * from MAVEN_ARTIFACT", ds, System.out);
        SqlTestsUtils.dump("select * from MAVEN_DEPENDENCY", ds, System.out);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_JOB", ds)).isEqualTo(2);

        assertThat(SqlTestsUtils.countRows("select * from JENKINS_BUILD", ds)).isEqualTo(2);

        assertThat(SqlTestsUtils.countRows("select * from MAVEN_ARTIFACT", ds)).isEqualTo(2);
        assertThat(SqlTestsUtils.countRows("select * from MAVEN_DEPENDENCY", ds))
                .isEqualTo(4);
    }

    @Deprecated
    @Test
    public void listDownstreamJobs_upstream_jar_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2).contains("my-downstream-pipeline-1");
    }

    @Test
    public void listDownstreamJobsByArtifact_upstream_jar_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }
    }

    @Test
    public void listDownstreamJobsByArtifact_upstream_jar_with_classifier_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "aType",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "anotherType",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                "aClassifier");
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "aType",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "aType",
                "compile",
                false,
                "whatever");
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-3", 1);
        dao.recordDependency(
                "my-downstream-pipeline-3",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "whatever",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-3", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-4", 1);
        dao.recordDependency(
                "my-downstream-pipeline-4",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "anotherType",
                "compile",
                false,
                "aClassifier");
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-4", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);
        System.out.println(downstreamJobsByArtifactForBuild1);

        assertThat(downstreamJobsByArtifactForBuild1).hasSize(2);

        MavenArtifact expectedMavenArtifact = new MavenArtifact();
        expectedMavenArtifact.setGroupId("com.mycompany");
        expectedMavenArtifact.setArtifactId("upstream-1");
        expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setExtension("jar");

        expectedMavenArtifact.setType("aType");
        expectedMavenArtifact.setClassifier(null);
        SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
        assertThat(actualJobs).contains("my-downstream-pipeline-1");

        expectedMavenArtifact.setType("anotherType");
        expectedMavenArtifact.setClassifier("aClassifier");
        actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
        assertThat(actualJobs).contains("my-downstream-pipeline-4");
    }

    @Test
    public void listDownstreamJobsByArtifact_doesnt_return_artifacts_with_no_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-shared",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-2",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordDependency(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-shared",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "upstream-1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("upstream-1");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);
            System.out.println(downstreamJobsByArtifactForBuild1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }
    }

    @Test
    public void listDownstreamPipelinesBasedOnMavenDependencies_noBaseVersion() {
        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 2222, 22);

        SortedSet<String> downstreamJobs =
                dao.listDownstreamJobs("com.mycompany", "dependency-1", "1.0-SNAPSHOT", null, "jar");
        System.out.println(downstreamJobs);
        assertThat(downstreamJobs).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");
    }

    @Test
    public void listDownstreamPipelinesBasedOnMavenDependencies_withBaseVersion() {
        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 2222, 22);

        SortedSet<String> downstreamJobs =
                dao.listDownstreamJobs("com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT", "jar");
        System.out.println(downstreamJobs);
        assertThat(downstreamJobs).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");
    }

    @Test
    public void listDownstreamPipelinesBasedOnMavenDependencies_withClassifier() {
        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "aType",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "dependency-1",
                "1.0-SNAPSHOT",
                "anotherType",
                "compile",
                false,
                "aClassifier");
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 2222, 22);

        SortedSet<String> downstreamJobs = dao.listDownstreamJobs(
                "com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT", "aType");
        System.out.println(downstreamJobs);
        assertThat(downstreamJobs).contains("my-downstream-pipeline-1");

        downstreamJobs = dao.listDownstreamJobs(
                "com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT", "aType", "whatever");
        assertThat(downstreamJobs).isEmpty();

        downstreamJobs = dao.listDownstreamJobs(
                "com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT", "whatever");
        assertThat(downstreamJobs).isEmpty();

        downstreamJobs = dao.listDownstreamJobs(
                "com.mycompany", "dependency-1", "1.0-20180318.225603-3", "1.0-SNAPSHOT", "whatever", "aClassifier");
        assertThat(downstreamJobs).isEmpty();
    }

    @Deprecated
    @Test
    public void listDownstreamJobs_upstream_pom_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pom-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pom-pipeline-1",
                1,
                "com.mycompany.pom",
                "parent-pom",
                "1.0-SNAPSHOT",
                "pom",
                "1.0-SNAPSHOT",
                null,
                false,
                "pom",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pom-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject(
                "my-downstream-pipeline-1", 2, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump(
                "select * from MAVEN_ARTIFACT INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID",
                ds,
                System.out);
        SqlTestsUtils.dump(
                "select * from MAVEN_ARTIFACT INNER JOIN MAVEN_PARENT_PROJECT ON MAVEN_ARTIFACT.ID = MAVEN_PARENT_PROJECT.ARTIFACT_ID",
                ds,
                System.out);

        List<String> downstreamJobs = dao.listDownstreamJobs("my-upstream-pom-pipeline-1", 1);
        assertThat(downstreamJobs).contains("my-downstream-pipeline-1");
        System.out.println(downstreamJobs);
    }

    @Test
    public void listDownstreamJobsbyArtifact_upstream_pom_triggers_downstream_pipelines() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pom-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pom-pipeline-1",
                1,
                "com.mycompany.pom",
                "parent-pom",
                "1.0-SNAPSHOT",
                "pom",
                "1.0-SNAPSHOT",
                null,
                false,
                "pom",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pom-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject(
                "my-downstream-pipeline-1", 2, "com.mycompany.pom", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        SqlTestsUtils.dump(
                "select * from JENKINS_BUILD LEFT OUTER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID",
                ds,
                System.out);
        SqlTestsUtils.dump(
                "select * from MAVEN_ARTIFACT INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID",
                ds,
                System.out);
        SqlTestsUtils.dump(
                "select * from MAVEN_ARTIFACT INNER JOIN MAVEN_PARENT_PROJECT ON MAVEN_ARTIFACT.ID = MAVEN_PARENT_PROJECT.ARTIFACT_ID",
                ds,
                System.out);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany.pom");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pom-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(actualJobs).hasSize(1);
            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_ignoreUpstreamTriggers_activated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 111);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", true, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1).contains("my-downstream-pipeline-2");

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2).contains("my-downstream-pipeline-1");
    }

    @Test
    public void list_downstream_jobs_by_artifact_with_ignoreUpstreamTriggers_activated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 111);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", true, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 111, 11);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-2");

            assertThat(actualJobs).hasSize(1);
            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(actualJobs).hasSize(1);
            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_skippedDownstreamTriggersActivated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                true,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 123, 5);

        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1).isEmpty();

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2).contains("my-downstream-pipeline-1");
    }

    @Test
    public void list_downstream_jobs_by_artifact_with_skippedDownstreamTriggersActivated() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                true,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 1111, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 555, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2",
                1,
                "com.mycompany",
                "shared",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 123, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);
            assertThat(downstreamJobsByArtifactForBuild1).hasSize(0);
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-SNAPSHOT",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-SNAPSHOT",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 11, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 9, 5);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
            assertThat(actualJobs).hasSize(1);
        }
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-20170808.155524-63",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-20170808.155524-64",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 50, 22);

        List<String> downstreamPipelinesForBuild1 = dao.listDownstreamJobs("my-upstream-pipeline-1", 1);
        assertThat(downstreamPipelinesForBuild1).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-20170808.155524-65",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-20170808.155524-66",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        List<String> downstreamPipelinesForBuild2 = dao.listDownstreamJobs("my-upstream-pipeline-1", 2);
        assertThat(downstreamPipelinesForBuild2).contains("my-downstream-pipeline-1");
    }

    @Test
    public void list_downstream_jobs_by_artifact_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-20170808.155524-63",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "service",
                "1.0-20170808.155524-64",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordDependency(
                "my-downstream-pipeline-1", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordDependency(
                "my-downstream-pipeline-2", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 50, 22);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.0-20170808.155524-63");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "core",
                "1.1-20170808.155524-65",
                "jar",
                "1.1-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "service",
                "1.1-20170808.155524-66",
                "war",
                "1.1-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordDependency(
                "my-downstream-pipeline-1", 2, "com.mycompany", "core", "1.1-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordDependency(
                "my-downstream-pipeline-2", 2, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("core");
            expectedMavenArtifact.setVersion("1.1-20170808.155524-65");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("jar");
            expectedMavenArtifact.setExtension("jar");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
            assertThat(actualJobs).hasSize(1);
        }
    }

    @Issue("JENKINS-57332")
    @Test
    public void get_generated_artifacts_with_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-20170808.155524-63",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        List<MavenArtifact> generatedArtifacts = dao.getGeneratedArtifacts("my-upstream-pipeline-1", 1);
        System.out.println("GeneratedArtifacts "
                + generatedArtifacts.stream()
                        .map(mavenArtifact -> mavenArtifact.getId() + ", version: " + mavenArtifact.getVersion()
                                + ", baseVersion: " + mavenArtifact.getBaseVersion())
                        .collect(Collectors.joining(", ")));

        assertThat(generatedArtifacts).hasSize(1);
        MavenArtifact jar = generatedArtifacts.get(0);
        assertThat(jar.getId()).isEqualTo("com.mycompany:core:jar:1.0-SNAPSHOT");
        assertThat(jar.getVersion()).isEqualTo("1.0-20170808.155524-63");
        assertThat(jar.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
    }

    @Issue("JENKINS-57332")
    @Test
    public void get_generated_artifacts_with_non_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        List<MavenArtifact> generatedArtifacts = dao.getGeneratedArtifacts("my-upstream-pipeline-1", 1);
        System.out.println("GeneratedArtifacts "
                + generatedArtifacts.stream()
                        .map(mavenArtifact -> mavenArtifact.getId() + ", version: " + mavenArtifact.getVersion()
                                + ", baseVersion: " + mavenArtifact.getBaseVersion())
                        .collect(Collectors.joining(", ")));

        assertThat(generatedArtifacts).hasSize(1);
        MavenArtifact jar = generatedArtifacts.get(0);
        assertThat(jar.getId()).isEqualTo("com.mycompany:core:jar:1.0-SNAPSHOT");
        assertThat(jar.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(jar.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
    }

    /**
     * Verify backward compatibility: some old entries have
     * `generated_artifact.version == null`
     */
    @Issue("JENKINS-57332")
    @Test
    public void get_generated_artifacts_with_null_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "core",
                null,
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        List<MavenArtifact> generatedArtifacts = dao.getGeneratedArtifacts("my-upstream-pipeline-1", 1);
        System.out.println("GeneratedArtifacts "
                + generatedArtifacts.stream()
                        .map(mavenArtifact -> mavenArtifact.getId() + ", version: " + mavenArtifact.getVersion()
                                + ", baseVersion: " + mavenArtifact.getBaseVersion())
                        .collect(Collectors.joining(", ")));

        assertThat(generatedArtifacts).hasSize(1);
        MavenArtifact jar = generatedArtifacts.get(0);
        assertThat(jar.getId()).isEqualTo("com.mycompany:core:jar:1.0-SNAPSHOT");
        assertThat(jar.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(jar.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
    }

    @Issue("JENKINS-55566")
    @Test
    public void list_downstream_jobs_by_parent_pom_timestamped_snapshot_version() {

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 1);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                1,
                "com.mycompany",
                "parent-pom",
                "1.0-20170808.155524-63",
                "pom",
                "1.0-SNAPSHOT",
                null,
                false,
                "pom",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 1);
        dao.recordParentProject("my-downstream-pipeline-1", 1, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 70, 22);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 1);
        dao.recordParentProject("my-downstream-pipeline-2", 1, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 50, 22);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setVersion("1.0-20170808.155524-63");
            expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1", "my-downstream-pipeline-2");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
        }

        dao.getOrCreateBuildPrimaryKey("my-upstream-pipeline-1", 2);
        dao.recordGeneratedArtifact(
                "my-upstream-pipeline-1",
                2,
                "com.mycompany",
                "parent-pom",
                "1.1-20170808.155524-65",
                "pom",
                "1.1-SNAPSHOT",
                null,
                false,
                "pom",
                null);
        dao.updateBuildOnCompletion(
                "my-upstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-1", 2);
        dao.recordParentProject("my-downstream-pipeline-1", 2, "com.mycompany", "parent-pom", "1.1-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-1", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        dao.getOrCreateBuildPrimaryKey("my-downstream-pipeline-2", 2);
        dao.recordParentProject("my-downstream-pipeline-2", 2, "com.mycompany", "parent-pom", "1.0-SNAPSHOT", false);
        dao.updateBuildOnCompletion(
                "my-downstream-pipeline-2", 2, Result.SUCCESS.ordinal, System.currentTimeMillis() - 20, 9);

        {
            MavenArtifact expectedMavenArtifact = new MavenArtifact();
            expectedMavenArtifact.setGroupId("com.mycompany");
            expectedMavenArtifact.setArtifactId("parent-pom");
            expectedMavenArtifact.setVersion("1.1-20170808.155524-65");
            expectedMavenArtifact.setBaseVersion("1.1-SNAPSHOT");
            expectedMavenArtifact.setType("pom");
            expectedMavenArtifact.setExtension("pom");

            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactForBuild1 =
                    dao.listDownstreamJobsByArtifact("my-upstream-pipeline-1", 2);

            SortedSet<String> actualJobs = downstreamJobsByArtifactForBuild1.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("my-downstream-pipeline-1");

            assertThat(downstreamJobsByArtifactForBuild1).hasSize(1);
            assertThat(actualJobs).hasSize(1);
        }
    }

    @Test
    public void list_upstream_pipelines_based_on_maven_dependencies() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency(
                "pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-core",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core", 1);
        assertThat(upstreamPipelinesForBuild1.keySet()).contains("pipeline-framework");
    }

    @Test
    public void list_upstream_pipelines_based_on_maven_dependencies_with_classifier() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "aType",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "anotherType",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                "aClassifier");
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core1", 1);
        dao.recordDependency(
                "pipeline-core1", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "aType", "compile", false, null);
        dao.updateBuildOnCompletion("pipeline-core1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core2", 1);
        dao.recordDependency(
                "pipeline-core2",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "aType",
                "compile",
                false,
                "whatever");
        dao.updateBuildOnCompletion("pipeline-core2", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core3", 1);
        dao.recordDependency(
                "pipeline-core3", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "whatever", "compile", false, null);
        dao.updateBuildOnCompletion("pipeline-core3", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core4", 1);
        dao.recordDependency(
                "pipeline-core4",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "whatever",
                "compile",
                false,
                "aClassifier");
        dao.updateBuildOnCompletion("pipeline-core4", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core5", 1);
        dao.recordDependency(
                "pipeline-core5",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "anotherType",
                "compile",
                false,
                "aClassifier");
        dao.updateBuildOnCompletion("pipeline-core5", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);

        Map<String, Integer> upstreamPipelinesForBuild1 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core1", 1);
        assertThat(upstreamPipelinesForBuild1.keySet()).contains("pipeline-framework");

        Map<String, Integer> upstreamPipelinesForBuild2 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core2", 1);
        assertThat(upstreamPipelinesForBuild2.keySet()).isEmpty();

        Map<String, Integer> upstreamPipelinesForBuild3 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core3", 1);
        assertThat(upstreamPipelinesForBuild3.keySet()).isEmpty();

        Map<String, Integer> upstreamPipelinesForBuild4 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core4", 1);
        assertThat(upstreamPipelinesForBuild4.keySet()).isEmpty();

        Map<String, Integer> upstreamPipelinesForBuild5 =
                dao.listUpstreamPipelinesBasedOnMavenDependencies("pipeline-core5", 1);
        assertThat(upstreamPipelinesForBuild5.keySet()).contains("pipeline-framework");
    }

    @Test
    public void list_upstream_pipelines_based_on_parent_project() {

        dao.getOrCreateBuildPrimaryKey("pipeline-parent-pom", 1);
        dao.recordGeneratedArtifact(
                "pipeline-parent-pom",
                1,
                "com.mycompany",
                "company-parent-pom",
                "1.0-SNAPSHOT",
                "pom",
                "1.0-SNAPSHOT",
                null,
                false,
                "pom",
                null);
        dao.updateBuildOnCompletion(
                "pipeline-parent-pom", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordParentProject("pipeline-core", 1, "com.mycompany", "company-parent-pom", "1.0-SNAPSHOT", false);
        dao.recordDependency(
                "pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-core",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 =
                dao.listUpstreamPipelinesBasedOnParentProjectDependencies("pipeline-core", 1);
        assertThat(upstreamPipelinesForBuild1.keySet()).contains("pipeline-parent-pom");
    }

    @Test
    public void list_transitive_upstream_jobs() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency(
                "pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-core",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-service", 1);
        dao.recordDependency(
                "pipeline-service", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordDependency(
                "pipeline-service", 1, "com.mycompany", "core", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-service",
                1,
                "com.mycompany",
                "service",
                "1.0-SNAPSHOT",
                "war",
                "1.0-SNAPSHOT",
                null,
                false,
                "war",
                null);
        dao.updateBuildOnCompletion(
                "pipeline-service", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 22);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        Map<String, Integer> upstreamPipelinesForBuild1 = dao.listTransitiveUpstreamJobs("pipeline-service", 1);
        assertThat(upstreamPipelinesForBuild1.keySet()).contains("pipeline-framework", "pipeline-core");
    }

    @Test
    public void list_transitive_upstream_jobs_with_classifier() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "aType",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                "aClassifier");
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core1", 1);
        dao.recordDependency(
                "pipeline-core1",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "aType",
                "compile",
                false,
                "aClassifier");
        dao.recordGeneratedArtifact(
                "pipeline-core1",
                1,
                "com.mycompany",
                "core1",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                "aClassifier");
        dao.updateBuildOnCompletion("pipeline-core1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-service1", 1);
        dao.recordDependency(
                "pipeline-service1",
                1,
                "com.mycompany",
                "core1",
                "1.0-SNAPSHOT",
                "jar",
                "compile",
                false,
                "aClassifier");
        dao.updateBuildOnCompletion(
                "pipeline-service1", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 22);

        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);

        assertThat(dao.listTransitiveUpstreamJobs("pipeline-service1", 1).keySet())
                .contains("pipeline-framework", "pipeline-core1");
    }

    @Deprecated
    @Test
    public void list_downstream_jobs_with_failed_last_build() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency(
                "pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-core",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        {
            List<String> downstreamJobs = dao.listDownstreamJobs("pipeline-framework", 1);
            assertThat(downstreamJobs).contains("pipeline-core");
        }

        // pipeline-core#2 fails before dependencies have been tracked
        dao.getOrCreateBuildPrimaryKey("pipeline-core", 2);
        dao.updateBuildOnCompletion("pipeline-core", 2, Result.FAILURE.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);

        {
            List<String> downstreamJobs = dao.listDownstreamJobs("pipeline-framework", 1);
            assertThat(downstreamJobs).contains("pipeline-core");
        }
    }

    @Test
    public void list_downstream_jobs_by_artifact_with_failed_last_build() {

        dao.getOrCreateBuildPrimaryKey("pipeline-framework", 1);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.recordGeneratedArtifact(
                "pipeline-framework",
                1,
                "com.mycompany",
                "framework",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                "sources");
        dao.updateBuildOnCompletion(
                "pipeline-framework", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        dao.getOrCreateBuildPrimaryKey("pipeline-core", 1);
        dao.recordDependency(
                "pipeline-core", 1, "com.mycompany", "framework", "1.0-SNAPSHOT", "jar", "compile", false, null);
        dao.recordGeneratedArtifact(
                "pipeline-core",
                1,
                "com.mycompany",
                "core",
                "1.0-SNAPSHOT",
                "jar",
                "1.0-SNAPSHOT",
                null,
                false,
                "jar",
                null);
        dao.updateBuildOnCompletion("pipeline-core", 1, Result.SUCCESS.ordinal, System.currentTimeMillis() - 100, 11);

        MavenArtifact expectedMavenArtifact = new MavenArtifact();
        expectedMavenArtifact.setGroupId("com.mycompany");
        expectedMavenArtifact.setArtifactId("framework");
        expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setBaseVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setType("jar");
        expectedMavenArtifact.setExtension("jar");

        {
            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact =
                    dao.listDownstreamJobsByArtifact("pipeline-framework", 1);

            SortedSet<String> actualJobs = downstreamJobsByArtifact.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("pipeline-core");
            assertThat(downstreamJobsByArtifact).hasSize(1);
        }

        // pipeline-core#2 fails before dependencies have been tracked
        dao.getOrCreateBuildPrimaryKey("pipeline-core", 2);
        dao.updateBuildOnCompletion("pipeline-core", 2, Result.FAILURE.ordinal, System.currentTimeMillis() - 100, 11);

        SqlTestsUtils.dump("select * from JENKINS_JOB", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_GENERATED_ARTIFACTS", this.ds, System.out);
        SqlTestsUtils.dump("select * from JOB_DEPENDENCIES", this.ds, System.out);

        {
            Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact =
                    dao.listDownstreamJobsByArtifact("pipeline-framework", 1);
            SortedSet<String> actualJobs = downstreamJobsByArtifact.get(expectedMavenArtifact);
            assertThat(actualJobs).contains("pipeline-core");
            assertThat(downstreamJobsByArtifact).hasSize(1);
        }
    }
}
