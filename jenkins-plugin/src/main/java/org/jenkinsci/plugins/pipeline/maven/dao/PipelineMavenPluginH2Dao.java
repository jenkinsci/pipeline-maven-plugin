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

import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;
import org.jenkinsci.plugins.pipeline.maven.util.ClassUtils;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeIoException;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeSqlException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2Dao implements PipelineMavenPluginDao {

    private static Logger LOGGER = Logger.getLogger(PipelineMavenPluginH2Dao.class.getName());

    private transient JdbcConnectionPool jdbcConnectionPool;

    private transient Long jenkinsMasterPrimaryKey;

    public PipelineMavenPluginH2Dao(@Nonnull File rootDir) {
        rootDir.getClass(); // check non null

        File databaseFile = new File(rootDir, "jenkins-jobs");
        String jdbcUrl = "jdbc:h2:file:" + databaseFile.getAbsolutePath() + ";AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
        if (LOGGER.isLoggable(Level.FINEST)) {
            jdbcUrl += ";TRACE_LEVEL_SYSTEM_OUT=3";
        } else if (LOGGER.isLoggable(Level.FINE)) {
            jdbcUrl += ";TRACE_LEVEL_SYSTEM_OUT=2";
        }
        LOGGER.log(Level.INFO, "Open database {0}", jdbcUrl);
        jdbcConnectionPool = JdbcConnectionPool.create(jdbcUrl, "sa", "sa");

        initializeDatabase();
        testDatabase();
    }

    public PipelineMavenPluginH2Dao(@Nonnull JdbcConnectionPool jdbcConnectionPool) {
        jdbcConnectionPool.getClass(); // check non null

        this.jdbcConnectionPool = jdbcConnectionPool;

        initializeDatabase();
        testDatabase();
    }

    public PipelineMavenPluginH2Dao(String jdbcUrl, String username, String password) {
        jdbcUrl.getClass(); // check non null
        username.getClass(); // check non null
        password.getClass(); // check non null

        this.jdbcConnectionPool = JdbcConnectionPool.create(jdbcUrl, username, password);
        LOGGER.log(Level.FINE, "Open database {0}", jdbcUrl);

        initializeDatabase();
    }

    @Override
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope, boolean ignoreUpstreamTriggers, String classifier) {
        LOGGER.log(Level.FINE, "recordDependency({0}#{1}, {2}:{3}:{4}:{5}, {6}, ignoreUpstreamTriggers:{7}})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, version, type, classifier);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO MAVEN_DEPENDENCY(ARTIFACT_ID, BUILD_ID, SCOPE, IGNORE_UPSTREAM_TRIGGERS) VALUES (?, ?, ?, ?)")) {
                stmt.setLong(1, artifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
                stmt.setString(3, scope);
                stmt.setBoolean(4, ignoreUpstreamTriggers);
                stmt.execute();
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Nonnull
    @Override
    public List<MavenDependency> listDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDependencies({0}, {1})", new Object[]{jobFullName, buildNumber});
        String dependenciesSql = "SELECT DISTINCT MAVEN_ARTIFACT.*,  MAVEN_DEPENDENCY.scope " +
                " FROM MAVEN_ARTIFACT " +
                " INNER JOIN MAVEN_DEPENDENCY ON MAVEN_ARTIFACT.ID = MAVEN_DEPENDENCY.ARTIFACT_ID" +
                " INNER JOIN JENKINS_BUILD ON MAVEN_DEPENDENCY.BUILD_ID = JENKINS_BUILD.ID " +
                " INNER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID " +
                " WHERE " +
                "   JENKINS_JOB.FULL_NAME = ? AND" +
                "   JENKINS_BUILD.NUMBER = ? ";

        List<MavenDependency> results = new ArrayList<>();
        try (Connection cnn = this.jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(dependenciesSql)) {
                stmt.setString(1, jobFullName);
                stmt.setInt(2, buildNumber);
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        MavenDependency artifact = new MavenDependency();

                        artifact.groupId = rst.getString("maven_artifact.group_id");
                        artifact.artifactId = rst.getString("maven_artifact.artifact_id");
                        artifact.version = rst.getString("maven_artifact.version");
                        artifact.snapshot = artifact.version.endsWith("-SNAPSHOT");
                        artifact.type = rst.getString("maven_artifact.type");
                        artifact.classifier = rst.getString("maven_artifact.classifier");
                        artifact.setScope(rst.getString("maven_dependency.scope"));
                        results.add(artifact);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }

        Collections.sort(results);
        return results;
    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        LOGGER.log(Level.FINE, "recordParentProject({0}#{1}, {2}:{3} ignoreUpstreamTriggers:{5}})",
                new Object[]{jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long parentArtifactPrimaryKey = getOrCreateArtifactPrimaryKey(parentGroupId, parentArtifactId, parentVersion, "pom", null);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO MAVEN_PARENT_PROJECT(ARTIFACT_ID, BUILD_ID, IGNORE_UPSTREAM_TRIGGERS) VALUES (?, ?, ?)")) {
                stmt.setLong(1, parentArtifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
                stmt.setBoolean(3, ignoreUpstreamTriggers);
                stmt.execute();
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String baseVersion, String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        LOGGER.log(Level.FINE, "recordGeneratedArtifact({0}#{1}, {2}:{3}:{4}:{5}, version:{6}, repositoryUrl:{7}, skipDownstreamTriggers:{8})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, baseVersion, type, version, repositoryUrl, skipDownstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, baseVersion, type, classifier);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO GENERATED_MAVEN_ARTIFACT(ARTIFACT_ID, BUILD_ID, VERSION, REPOSITORY_URL, EXTENSION, SKIP_DOWNSTREAM_TRIGGERS) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setLong(1, artifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
                stmt.setString(3, version);
                stmt.setString(4, repositoryUrl);
                stmt.setString(5, extension);
                stmt.setBoolean(6, skipDownstreamTriggers);
                stmt.execute();
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        LOGGER.log(Level.FINE, "recordBuildUpstreamCause(upstreamBuild: {0}#{1}, downstreamBuild: {2}#{3})",
                new Object[]{upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            String sql = "insert into jenkins_build_upstream_cause (upstream_build_id, downstream_build_id) values (?, ?)";

            long upstreamBuildPrimaryKey = getOrCreateBuildPrimaryKey(upstreamJobName, upstreamBuildNumber);
            long downstreamBuildPrimaryKey = getOrCreateBuildPrimaryKey(downstreamJobName, downstreamBuildNumber);

            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setLong(1, upstreamBuildPrimaryKey);
                stmt.setLong(2, downstreamBuildPrimaryKey);

                int rowCount = stmt.executeUpdate();
                if (rowCount != 1) {
                    LOGGER.log(Level.INFO, "More/less ({0}) than 1 record inserted in jenkins_build_upstream_cause for upstreamBuild: {1}#{2}, downstreamBuild: {3}#{4}",
                            new Object[]{rowCount, upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber});
                }
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }

    }

    @Override
    public void renameJob(String oldFullName, String newFullName) {
        LOGGER.log(Level.FINER, "renameJob({0}, {1})", new Object[]{oldFullName, newFullName});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB SET FULL_NAME = ? WHERE FULL_NAME = ? AND JENKINS_MASTER_ID = ?")) {
                stmt.setString(1, newFullName);
                stmt.setString(2, oldFullName);
                stmt.setLong(3, getJenkinsMasterPrimaryKey(cnn));
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "renameJob({0}, {1}): {2}", new Object[]{oldFullName, newFullName, count});
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void deleteJob(String jobFullName) {
        LOGGER.log(Level.FINER, "deleteJob({0})", new Object[]{jobFullName});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("DELETE FROM JENKINS_JOB WHERE FULL_NAME = ? AND JENKINS_MASTER_ID = ?")) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "deleteJob({0}): {1}", new Object[]{jobFullName, count});
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void deleteBuild(String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "deleteBuild({0}#{1})", new Object[]{jobFullName, buildNumber});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            Long jobPrimaryKey;
            Integer lastBuildNumber;
            Integer lastSuccessfulBuildNumber;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID, LAST_BUILD_NUMBER, LAST_SUCCESSFUL_BUILD_NUMBER FROM JENKINS_JOB WHERE FULL_NAME = ? AND JENKINS_MASTER_ID = ?")) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        jobPrimaryKey = rst.getLong("ID");
                        lastBuildNumber = rst.getInt("LAST_BUILD_NUMBER");
                        lastSuccessfulBuildNumber = rst.getInt("LAST_SUCCESSFUL_BUILD_NUMBER");
                    } else {
                        jobPrimaryKey = null;
                        lastBuildNumber = null;
                        lastSuccessfulBuildNumber = null;
                    }
                }
            }
            if (jobPrimaryKey == null) {
                LOGGER.log(Level.FINE, "No record found for job {0}", new Object[]{jobFullName});
                return;
            }

            if (buildNumber == lastBuildNumber || buildNumber == lastSuccessfulBuildNumber) {
                Integer newLastBuildNumber = (lastBuildNumber == buildNumber) ? null : lastBuildNumber;
                Integer newLastSuccessfulBuildNumber = (lastSuccessfulBuildNumber == buildNumber) ? null : lastSuccessfulBuildNumber;

                try (PreparedStatement stmt = cnn.prepareStatement("SELECT JENKINS_BUILD.NUMBER, RESULT_ID FROM JENKINS_BUILD WHERE JOB_ID = ? AND NUMBER != ? ORDER BY NUMBER DESC")) {
                    stmt.setLong(1, jobPrimaryKey);
                    stmt.setInt(2, buildNumber);
                    stmt.setFetchSize(5);
                    try (ResultSet rst = stmt.executeQuery()) {
                        while (rst.next() && (newLastBuildNumber == null || newLastSuccessfulBuildNumber == null)) {
                            int currentBuildNumber = rst.getInt("JENKINS_BUILD.NUMBER");
                            int currentBuildResultId = rst.getInt("RESULT_ID");

                            if(newLastBuildNumber == null) {
                                newLastBuildNumber = currentBuildNumber;
                            }

                            if (newLastSuccessfulBuildNumber == null && Result.SUCCESS.ordinal == currentBuildResultId) {
                                newLastSuccessfulBuildNumber = currentBuildNumber;
                            }
                        }
                    }
                }

                try(PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB SET LAST_BUILD_NUMBER = ?, LAST_SUCCESSFUL_BUILD_NUMBER = ? WHERE ID = ?")) {
                    stmt.setInt(1, newLastBuildNumber);
                    stmt.setInt(2, newLastSuccessfulBuildNumber);
                    stmt.setLong(3, jobPrimaryKey);
                    stmt.execute();
                }
            }

            try (PreparedStatement stmt = cnn.prepareStatement("DELETE FROM JENKINS_BUILD WHERE JOB_ID = ? AND NUMBER = ?")) {
                stmt.setLong(1, jobPrimaryKey);
                stmt.setInt(2, buildNumber);
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "deleteJob({0}#{1}): {2}", new Object[]{jobFullName, buildNumber, count});
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void cleanup() {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            String sql = "DELETE FROM MAVEN_ARTIFACT WHERE ID NOT IN (SELECT DISTINCT ARTIFACT_ID FROM MAVEN_DEPENDENCY UNION SELECT DISTINCT ARTIFACT_ID FROM GENERATED_MAVEN_ARTIFACT)";
            try (Statement stmt = cnn.createStatement()) {
                int count = stmt.executeUpdate(sql);
                LOGGER.log(Level.FINE, "cleanup(): {0}", new Object[]{count});
            }
            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    protected long getOrCreateBuildPrimaryKey(String jobFullName, int buildNumber) {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);

            Long jobPrimaryKey = null;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM JENKINS_JOB WHERE FULL_NAME = ? AND JENKINS_MASTER_ID = ?")) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        jobPrimaryKey = rst.getLong(1);
                    }
                }
            }
            if (jobPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO JENKINS_JOB(FULL_NAME, JENKINS_MASTER_ID) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, jobFullName);
                    stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                    stmt.execute();
                    try (ResultSet rst = stmt.getGeneratedKeys()) {
                        if (rst.next()) {
                            jobPrimaryKey = rst.getLong(1);
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                }
            }
            Long buildPrimaryKey = null;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM JENKINS_BUILD WHERE JOB_ID=? AND NUMBER=?")) {
                stmt.setLong(1, jobPrimaryKey);
                stmt.setInt(2, buildNumber);
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        buildPrimaryKey = rst.getLong(1);
                    }
                }
            }

            if (buildPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO JENKINS_BUILD(JOB_ID, NUMBER) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setLong(1, jobPrimaryKey);
                    stmt.setInt(2, buildNumber);
                    stmt.execute();
                    try (ResultSet rst = stmt.getGeneratedKeys()) {
                        if (rst.next()) {
                            buildPrimaryKey = rst.getLong(1);
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                }
            }
            cnn.commit();
            return buildPrimaryKey;
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    protected long getOrCreateArtifactPrimaryKey(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nullable String classifier) {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            // get or create build record
            Long artifactPrimaryKey = null;
            if (classifier == null) {
                // For an unknown reason, "where classifier = null" does not work as expected when "where classifier is null" does
                try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM MAVEN_ARTIFACT WHERE GROUP_ID = ? AND ARTIFACT_ID = ? AND VERSION = ? AND TYPE = ? AND CLASSIFIER is NULL")) {
                    stmt.setString(1, groupId);
                    stmt.setString(2, artifactId);
                    stmt.setString(3, version);
                    stmt.setString(4, type);

                    try (ResultSet rst = stmt.executeQuery()) {
                        if (rst.next()) {
                            artifactPrimaryKey = rst.getLong(1);
                        }
                    }
                }
            } else {
                try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM MAVEN_ARTIFACT WHERE GROUP_ID = ? AND ARTIFACT_ID = ? AND VERSION = ? AND TYPE = ? AND CLASSIFIER = ?")) {
                    stmt.setString(1, groupId);
                    stmt.setString(2, artifactId);
                    stmt.setString(3, version);
                    stmt.setString(4, type);
                    stmt.setString(5, classifier);

                    try (ResultSet rst = stmt.executeQuery()) {
                        if (rst.next()) {
                            artifactPrimaryKey = rst.getLong(1);
                        }
                    }
                }
            }

            if (artifactPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO MAVEN_ARTIFACT(GROUP_ID, ARTIFACT_ID, VERSION, TYPE, CLASSIFIER) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, groupId);
                    stmt.setString(2, artifactId);
                    stmt.setString(3, version);
                    stmt.setString(4, type);
                    stmt.setString(5, classifier);

                    stmt.execute();
                    try (ResultSet rst = stmt.getGeneratedKeys()) {
                        if (rst.next()) {
                            artifactPrimaryKey = rst.getLong(1);
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                }
            }
            cnn.commit();
            return artifactPrimaryKey;
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    protected synchronized void initializeDatabase() {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            int initialSchemaVersion = getSchemaVersion(cnn);

            LOGGER.log(Level.FINE, "Initialise database. Current schema version: {0}", new Object[]{initialSchemaVersion});

            NumberFormat numberFormat = new DecimalFormat("00");
            int idx = initialSchemaVersion;
            while (true) {
                idx++;
                String sqlScriptPath = "sql/h2/" + numberFormat.format(idx) + "_migration.sql";
                InputStream sqlScriptInputStream = ClassUtils.getResourceAsStream(sqlScriptPath);
                if (sqlScriptInputStream == null) {
                    break;
                } else {
                    try (Statement stmt = cnn.createStatement()) {
                        String sqlScript = IOUtils.toString(sqlScriptInputStream);
                        LOGGER.log(Level.FINE, "Execute database migration script {0}", sqlScriptPath);
                        stmt.execute(sqlScript);
                    } catch (IOException e) {
                        throw new RuntimeIoException("Exception reading " + sqlScriptPath, e);
                    }

                    String className = "org.jenkinsci.plugins.pipeline.maven.db.migration.h2.MigrationStep" + idx;
                    try {
                        MigrationStep migrationStep = (MigrationStep) Class.forName(className).newInstance();
                        LOGGER.log(Level.FINE, "Execute database migration step {0}", migrationStep.getClass().getName());
                        migrationStep.execute(cnn, getJenkinsDetails());
                    } catch (ClassNotFoundException e) {
                        // no migration class found, just a migration script
                        LOGGER.log(Level.FINER, "Migration step {0} not found", new Object[]{className});
                    } catch (Exception e) {
                        cnn.rollback();
                        throw new RuntimeException(e);
                    }

                }
                cnn.commit();
            }
            int newSchemaVersion = getSchemaVersion(cnn);

            if (newSchemaVersion == 0) {
                // https://issues.jenkins-ci.org/browse/JENKINS-46577
                throw new IllegalStateException("Failure to load database DDL files. " +
                        "Files 'sql/h2/xxx_migration.sql' NOT found in the Thread Context Class Loader. " +
                        " Pipeline Maven Plugin may be installed in an unsupported manner " +
                        "(thread.contextClassLoader: " + Thread.currentThread().getContextClassLoader() + ", "
                        + "classLoader: " + ClassUtils.class.getClassLoader() + ")");
            } else if (newSchemaVersion == initialSchemaVersion) {
                // no migration was needed
            } else {
                LOGGER.log(Level.INFO, "Database successfully migrated from version {0} to version {1}", new Object[]{initialSchemaVersion, newSchemaVersion});
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    protected int getSchemaVersion(Connection cnn) throws SQLException {
        int schemaVersion;
        try (Statement stmt = cnn.createStatement()) {
            try (ResultSet rst = stmt.executeQuery("SELECT * FROM VERSION")) {
                if (rst.next()) {
                    schemaVersion = rst.getInt(1);
                } else {
                    schemaVersion = 0;
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1) {
                    schemaVersion = 0;
                } else {
                    throw new RuntimeSqlException(e);
                }
            }
        }
        return schemaVersion;
    }

    /**
     * Basic tests to ensure that the database is not corrupted
     */
    protected synchronized void testDatabase() throws RuntimeSqlException {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            List<String> tables = Arrays.asList("MAVEN_ARTIFACT", "JENKINS_JOB", "JENKINS_BUILD", "MAVEN_DEPENDENCY", "GENERATED_MAVEN_ARTIFACT", "MAVEN_PARENT_PROJECT");
            for (String table : tables) {
                try (Statement stmt = cnn.createStatement()) {
                    try (ResultSet rst = stmt.executeQuery("SELECT count(*) FROM " + table)) {
                        if (rst.next()) {
                            int count = rst.getInt(1);
                            LOGGER.log(Level.FINE, "Table {0}: {1} rows", new Object[]{table, count});
                        } else {
                            throw new IllegalStateException("Exception testing table '" + table + "'");
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeSqlException("Exception testing table '" + table + "' on " + cnn.toString(), e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Nonnull
    @Override
    @Deprecated
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        List<String> downstreamJobs = listDownstreamPipelinesBasedOnMavenDependencies(jobFullName, buildNumber);
        downstreamJobs.addAll(listDownstreamPipelinesBasedOnParentProjectDependencies(jobFullName, buildNumber));

        // JENKINS-50507 Don't return the passed job in case of pipelines consuming the artifacts they produce
        downstreamJobs.remove(jobFullName);
        return downstreamJobs;
    }

    @Nonnull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@Nonnull String jobFullName, int buildNumber) {
        Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactBasedOnMavenDependencies = listDownstreamJobsByArtifactBasedOnMavenDependencies(jobFullName, buildNumber);
        Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifactBasedOnParentProjectDependencies = listDownstreamJobsByArtifactBasedOnParentProjectDependencies(jobFullName, buildNumber);


        Map<MavenArtifact, SortedSet<String>> results = new HashMap<>();
        results.putAll(downstreamJobsByArtifactBasedOnMavenDependencies);

        for(Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamJobsByArtifactBasedOnParentProjectDependencies.entrySet()) {
            MavenArtifact mavenArtifact = entry.getKey();
            if (results.containsKey(mavenArtifact)) {
                results.get(mavenArtifact).addAll(entry.getValue());
            } else {
                results.put(mavenArtifact, new TreeSet<>(entry.getValue()));
            }
        }
        // JENKINS-50507 Don't return the passed job in case of pipelines consuming the artifacts they produce
        for (Iterator<Entry<MavenArtifact, SortedSet<String>>> it = results.entrySet().iterator(); it.hasNext();) {
            Map.Entry<MavenArtifact, SortedSet<String>> entry = it.next();
            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> jobs = entry.getValue();
            boolean removed = jobs.remove(jobFullName);
            if (removed) {
                LOGGER.log(Level.FINER, "Remove {0} from downstreamJobs of artifact {1}", new Object[]{jobFullName, mavenArtifact});
                if (jobs.isEmpty()) {
                    it.remove();
                }
            }
        }

        return results;
    }

    @Deprecated
    protected List<String> listDownstreamPipelinesBasedOnMavenDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamJobs({0}, {1})", new Object[]{jobFullName, buildNumber});

        String sql = "select distinct downstream_job.full_name \n" +
                "from jenkins_job as upstream_job \n" +
                "inner join jenkins_build as upstream_build on upstream_job.id = upstream_build.job_id \n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id and generated_maven_artifact.skip_downstream_triggers = false) \n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id \n" +
                "inner join maven_dependency on (maven_dependency.artifact_id = maven_artifact.id and maven_dependency.ignore_upstream_triggers = false) \n" +
                "inner join jenkins_build as downstream_build on maven_dependency.build_id = downstream_build.id \n" +
                "inner join jenkins_job as downstream_job on (downstream_build.number = downstream_job.last_successful_build_number and downstream_build.job_id = downstream_job.id) \n" +
                "where upstream_job.full_name = ? and upstream_job.jenkins_master_id = ? and upstream_build.number = ? and downstream_job.jenkins_master_id = ?";

        List<String> downstreamJobsFullNames = new ArrayList<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, buildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        downstreamJobsFullNames.add(rst.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listDownstreamJobs({0}, {1}): {2}", new Object[]{jobFullName, buildNumber, downstreamJobsFullNames});

        return downstreamJobsFullNames;
    }

    protected Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifactBasedOnMavenDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamJobsByArtifactBasedOnMavenDependencies({0}, {1})", new Object[]{jobFullName, buildNumber});


        String sql = "select distinct downstream_job.full_name, \n " +
                "   maven_artifact.group_id, maven_artifact.artifact_id, maven_artifact.version, maven_artifact.type, maven_artifact.classifier, \n" +
                "   generated_maven_artifact.version, generated_maven_artifact.extension \n" +
                "from jenkins_job as upstream_job \n" +
                "inner join jenkins_build as upstream_build on upstream_job.id = upstream_build.job_id \n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id and generated_maven_artifact.skip_downstream_triggers = false) \n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id \n" +
                "inner join maven_dependency on (maven_dependency.artifact_id = maven_artifact.id and maven_dependency.ignore_upstream_triggers = false) \n" +
                "inner join jenkins_build as downstream_build on maven_dependency.build_id = downstream_build.id \n" +
                "inner join jenkins_job as downstream_job on (downstream_build.number = downstream_job.last_successful_build_number and downstream_build.job_id = downstream_job.id) \n" +
                "where upstream_job.full_name = ? and upstream_job.jenkins_master_id = ? and upstream_build.number = ? and downstream_job.jenkins_master_id = ?";

        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});
        Map<MavenArtifact, SortedSet<String>> results = new HashMap<>();

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, buildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        MavenArtifact artifact = new MavenArtifact();
                        artifact.groupId = rst.getString("group_id");
                        artifact.artifactId = rst.getString("artifact_id");
                        artifact.version = rst.getString("generated_maven_artifact.version");
                        artifact.baseVersion = rst.getString("maven_artifact.version");
                        artifact.type = rst.getString("type");
                        artifact.classifier = rst.getString("classifier");
                        artifact.extension = rst.getString("extension");
                        String downstreamJobFullName = rst.getString("full_name");

                        if(results.containsKey(artifact)) {
                            results.get(artifact).add(downstreamJobFullName);
                        } else {
                            results.put(artifact, new TreeSet<>(Collections.singleton(downstreamJobFullName)));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listDownstreamJobsByArtifactBasedOnMavenDependencies({0}, {1}): {2}", new Object[]{jobFullName, buildNumber, results});

        return results;
    }


    @Deprecated
    protected List<String> listDownstreamPipelinesBasedOnParentProjectDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamPipelinesBasedOnParentProjectDependencies({0}, {1})", new Object[]{jobFullName, buildNumber});
        String sql = "select distinct downstream_job.full_name \n" +
                "from jenkins_job as upstream_job \n" +
                "inner join jenkins_build as upstream_build on upstream_job.id = upstream_build.job_id \n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id and generated_maven_artifact.skip_downstream_triggers = false) \n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id \n" +
                "inner join maven_parent_project on (maven_parent_project.artifact_id = maven_artifact.id and maven_parent_project.ignore_upstream_triggers = false) \n" +
                "inner join jenkins_build as downstream_build on maven_parent_project.build_id = downstream_build.id \n" +
                "inner join jenkins_job as downstream_job on (downstream_build.number = downstream_job.last_successful_build_number and downstream_build.job_id = downstream_job.id) \n" +
                "where upstream_job.full_name = ? and upstream_job.jenkins_master_id = ? and upstream_build.number = ? and downstream_job.jenkins_master_id = ?";

        List<String> downstreamJobsFullNames = new ArrayList<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, buildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        downstreamJobsFullNames.add(rst.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listDownstreamPipelinesBasedOnParentProjectDependencies({0}, {1}): {2}", new Object[]{jobFullName, buildNumber, downstreamJobsFullNames});

        return downstreamJobsFullNames;
    }


    protected Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifactBasedOnParentProjectDependencies(String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamPipelinesBasedOnParentProjectDependencies({0}, {1})", new Object[]{jobFullName, buildNumber});
        String sql = "select distinct downstream_job.full_name, \n" +
                "   maven_artifact.group_id, maven_artifact.artifact_id, maven_artifact.version, maven_artifact.type, maven_artifact.classifier, \n" +
                "   generated_maven_artifact.version, generated_maven_artifact.extension \n" +
                "from jenkins_job as upstream_job \n" +
                "inner join jenkins_build as upstream_build on upstream_job.id = upstream_build.job_id \n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id and generated_maven_artifact.skip_downstream_triggers = false) \n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id \n" +
                "inner join maven_parent_project on (maven_parent_project.artifact_id = maven_artifact.id and maven_parent_project.ignore_upstream_triggers = false) \n" +
                "inner join jenkins_build as downstream_build on maven_parent_project.build_id = downstream_build.id \n" +
                "inner join jenkins_job as downstream_job on (downstream_build.number = downstream_job.last_successful_build_number and downstream_build.job_id = downstream_job.id) \n" +
                "where upstream_job.full_name = ? and upstream_job.jenkins_master_id = ? and upstream_build.number = ? and downstream_job.jenkins_master_id = ?";

        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});

        Map<MavenArtifact, SortedSet<String>> results = new HashMap<>();

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, buildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        MavenArtifact artifact = new MavenArtifact();
                        artifact.groupId = rst.getString("group_id");
                        artifact.artifactId = rst.getString("artifact_id");
                        artifact.version = rst.getString("generated_maven_artifact.version");
                        artifact.baseVersion = rst.getString("maven_artifact.version");
                        artifact.type = rst.getString("type");
                        artifact.classifier = rst.getString("classifier");
                        artifact.extension = rst.getString("extension");
                        String downstreamJobFullName = rst.getString("full_name");

                        if(results.containsKey(artifact)) {
                            results.get(artifact).add(jobFullName);
                        } else {
                            results.put(artifact, new TreeSet<>(Collections.singleton(downstreamJobFullName)));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listDownstreamJobsByArtifactBasedOnParentProjectDependencies({0}, {1}): {2}", new Object[]{jobFullName, buildNumber, results});

        return results;
    }

    @Nonnull
    @Override
    public Map<String, Integer> listUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        Map<String, Integer> upstreamJobs = listUpstreamPipelinesBasedOnMavenDependencies(jobFullName, buildNumber);
        upstreamJobs.putAll(listUpstreamPipelinesBasedOnParentProjectDependencies(jobFullName, buildNumber));

        // JENKINS-50507 Don't return the passed job in case of pipelines consuming the artifacts they produce
        upstreamJobs.remove(jobFullName);

        return upstreamJobs;
    }

    /**
     *
     * @param downstreamJobFullName
     * @param downstreamBuildNumber
     * @return
     */
    protected Map<String, Integer> listUpstreamPipelinesBasedOnMavenDependencies(@Nonnull String downstreamJobFullName, int downstreamBuildNumber) {
        LOGGER.log(Level.FINER, "listUpstreamPipelinesBasedOnMavenDependencies({0}, {1})", new Object[]{downstreamJobFullName, downstreamBuildNumber});

        String sql = "select  upstream_job.full_name, upstream_build.number\n" +
                "from jenkins_job as upstream_job\n" +
                "inner join jenkins_build as upstream_build on (upstream_job.id = upstream_build.job_id and upstream_job.last_successful_build_number = upstream_build.number)\n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id  and generated_maven_artifact.skip_downstream_triggers = false)\n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id\n" +
                "inner join maven_dependency on (maven_dependency.artifact_id = maven_artifact.id and maven_dependency.ignore_upstream_triggers = false)\n" +
                "inner join jenkins_build as downstream_build on maven_dependency.build_id = downstream_build.id\n" +
                "inner join jenkins_job as downstream_job on downstream_build.job_id = downstream_job.id\n" +
                "where downstream_job.full_name = ? and downstream_job.jenkins_master_id = ? and  downstream_build.number = ? and upstream_job.jenkins_master_id = ?";

        Map<String, Integer> upstreamJobsFullNames = new HashMap<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, downstreamJobFullName, downstreamBuildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, downstreamJobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, downstreamBuildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        upstreamJobsFullNames.put(rst.getString(1), rst.getInt(2));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listUpstreamPipelinesBasedOnMavenDependencies({0}, {1}): {2}", new Object[]{downstreamJobFullName, downstreamBuildNumber, upstreamJobsFullNames});

        return upstreamJobsFullNames;
    }

    protected Map<String, Integer> listUpstreamPipelinesBasedOnParentProjectDependencies(@Nonnull String downstreamJobFullName, int downstreamBuildNumber) {
        LOGGER.log(Level.FINER, "listUpstreamPipelinesBasedOnParentProjectDependencies({0}, {1})", new Object[]{downstreamJobFullName, downstreamBuildNumber});

        String sql = "select  upstream_job.full_name, upstream_build.number\n" +
                "from jenkins_job as upstream_job\n" +
                "inner join jenkins_build as upstream_build on (upstream_job.id = upstream_build.job_id and upstream_job.last_successful_build_number = upstream_build.number)\n" +
                "inner join generated_maven_artifact on (upstream_build.id = generated_maven_artifact.build_id  and generated_maven_artifact.skip_downstream_triggers = false)\n" +
                "inner join maven_artifact on generated_maven_artifact.artifact_id = maven_artifact.id\n" +
                "inner join maven_parent_project on (maven_parent_project.artifact_id = maven_artifact.id and maven_parent_project.ignore_upstream_triggers = false)\n" +
                "inner join jenkins_build as downstream_build on maven_parent_project.build_id = downstream_build.id\n" +
                "inner join jenkins_job as downstream_job on downstream_build.job_id = downstream_job.id\n" +
                "where downstream_job.full_name = ? and downstream_job.jenkins_master_id = ? and  downstream_build.number = ? and upstream_job.jenkins_master_id = ?";


        Map<String, Integer> upstreamJobsFullNames = new HashMap<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, downstreamJobFullName, downstreamBuildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, downstreamJobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, downstreamBuildNumber);
                stmt.setLong(4, getJenkinsMasterPrimaryKey(cnn));
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        upstreamJobsFullNames.put(rst.getString(1), rst.getInt(2));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        LOGGER.log(Level.FINE, "listUpstreamPipelinesBasedOnParentProjectDependencies({0}, {1}): {2}", new Object[]{downstreamJobFullName, downstreamBuildNumber, upstreamJobsFullNames});

        return upstreamJobsFullNames;
    }

    @Nonnull
    public Map<String, Integer> listTransitiveUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return listTransitiveUpstreamJobs(jobFullName, buildNumber, new HashMap<String, Integer>());
    }

    private Map<String, Integer> listTransitiveUpstreamJobs(@Nonnull String jobFullName, int buildNumber, Map<String, Integer> transitiveUpstreamBuilds) {
        Map<String, Integer> upstreamBuilds = listUpstreamJobs(jobFullName, buildNumber);
        for (Entry<String, Integer> upstreamBuild : upstreamBuilds.entrySet()) {
            String upstreamJobFullName = upstreamBuild.getKey();
            Integer upstreamBuildNumber = upstreamBuild.getValue();
            if (transitiveUpstreamBuilds.containsKey(upstreamJobFullName)) {
                // job has already been visited, skip
            } else {
                transitiveUpstreamBuilds.put(upstreamJobFullName, upstreamBuildNumber);
                listTransitiveUpstreamJobs(upstreamJobFullName, upstreamBuildNumber, transitiveUpstreamBuilds);
            }
        }
        return transitiveUpstreamBuilds;
    }

    /**
     * List the artifacts generated by the given build
     *
     * @param jobFullName see {@link Item#getFullName()}
     * @param buildNumber see {@link Run#getNumber()}
     * @return list of artifact details stored as maps ("gav", "type", "skip_downstream_triggers")
     */
    @Nonnull
    public List<MavenArtifact> getGeneratedArtifacts(@Nonnull String jobFullName, @Nonnull int buildNumber) {
        LOGGER.log(Level.FINER, "getGeneratedArtifacts({0}, {1})", new Object[]{jobFullName, buildNumber});
        String generatedArtifactsSql = "SELECT DISTINCT MAVEN_ARTIFACT.*,  GENERATED_MAVEN_ARTIFACT.* " +
                " FROM MAVEN_ARTIFACT " +
                " INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID" +
                " INNER JOIN JENKINS_BUILD AS UPSTREAM_BUILD ON GENERATED_MAVEN_ARTIFACT.BUILD_ID = UPSTREAM_BUILD.ID " +
                " INNER JOIN JENKINS_JOB AS UPSTREAM_JOB ON UPSTREAM_BUILD.JOB_ID = UPSTREAM_JOB.ID " +
                " WHERE " +
                "   UPSTREAM_JOB.FULL_NAME = ? AND" +
                "   UPSTREAM_JOB.JENKINS_MASTER_ID = ? AND" +
                "   UPSTREAM_BUILD.NUMBER = ? ";

        List<MavenArtifact> results = new ArrayList<>();
        try (Connection cnn = this.jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(generatedArtifactsSql)) {
                stmt.setString(1, jobFullName);
                stmt.setLong(2, getJenkinsMasterPrimaryKey(cnn));
                stmt.setInt(3, buildNumber);
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        MavenArtifact artifact = new MavenArtifact();

                        artifact.groupId = rst.getString("maven_artifact.group_id");
                        artifact.artifactId = rst.getString("maven_artifact.artifact_id");
                        artifact.version = rst.getString("maven_artifact.version");
                        artifact.type = rst.getString("maven_artifact.type");
                        artifact.classifier = rst.getString("maven_artifact.classifier");

                        artifact.baseVersion = rst.getString("generated_maven_artifact.version");
                        artifact.repositoryUrl = rst.getString("generated_maven_artifact.repository_url");
                        artifact.extension = rst.getString("generated_maven_artifact.extension");
                        artifact.snapshot = artifact.version.endsWith("-SNAPSHOT");

                        // artifact.put("skip_downstream_triggers", rst.getString("generated_maven_artifact.skip_downstream_triggers"));
                        results.add(artifact);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }

        Collections.sort(results);
        return results;
    }

    @Nonnull
    public synchronized Long getJenkinsMasterPrimaryKey(Connection cnn) throws SQLException {
        if (this.jenkinsMasterPrimaryKey == null) {
            String jenkinsMasterLegacyInstanceId = getJenkinsDetails().getMasterLegacyInstanceId();
            String jenkinsMasterUrl = getJenkinsDetails().getMasterRootUrl();

            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM JENKINS_MASTER WHERE LEGACY_INSTANCE_ID=?")) {
                stmt.setString(1, jenkinsMasterLegacyInstanceId);
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        this.jenkinsMasterPrimaryKey = rst.getLong("ID");
                    }
                }
            }
            if (this.jenkinsMasterPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO JENKINS_MASTER(LEGACY_INSTANCE_ID, URL) values (?, ?)")) {
                    stmt.setString(1, jenkinsMasterLegacyInstanceId);
                    stmt.setString(2, jenkinsMasterUrl);
                    stmt.execute();
                    try (ResultSet rst = stmt.getGeneratedKeys()) {
                        if (rst.next()) {
                            this.jenkinsMasterPrimaryKey = rst.getLong(1);
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                }
            }
        }
        return this.jenkinsMasterPrimaryKey;
    }

    /**
     * for mocking
     */
    protected MigrationStep.JenkinsDetails getJenkinsDetails() {
        return new MigrationStep.JenkinsDetails();
    }

    @Override
    public String toPrettyString() {
        List<String> prettyStrings = new ArrayList<>();
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            prettyStrings.add("jdbc.url: " + cnn.getMetaData().getURL());
            List<String> tables = Arrays.asList("JENKINS_MASTER", "MAVEN_ARTIFACT", "JENKINS_JOB", "JENKINS_BUILD",
                    "MAVEN_DEPENDENCY", "GENERATED_MAVEN_ARTIFACT", "MAVEN_PARENT_PROJECT", "JENKINS_BUILD_UPSTREAM_CAUSE");
            for (String table : tables) {
                try (Statement stmt = cnn.createStatement()) {
                    try (ResultSet rst = stmt.executeQuery("SELECT count(*) FROM " + table)) {
                        if (rst.next()) {
                            int count = rst.getInt(1);
                            prettyStrings.add("Table " + table + ": " + count + " rows");
                        } else {
                            prettyStrings.add("Table " + table + ": #IllegalStateException 'select count(*)' didn't return any row#");
                        }
                    }
                } catch (SQLException e) {
                    prettyStrings.add("Table " + table + ": " + e);
                    LOGGER.log(Level.WARNING, "SQLException counting rows on " + table, e);
                }
            }
        } catch (SQLException e) {
            prettyStrings.add("SQLException getting a connection to " + jdbcConnectionPool + ": " + e);
            LOGGER.log(Level.WARNING, "SQLException getting a connection to " + jdbcConnectionPool, e);
        }

        StringBuilder result = new StringBuilder("PipelineMavenPluginH2Dao ");
        for (String prettyString : prettyStrings) {
            result.append("\r\n\t" + prettyString);
        }
        return result.toString();
    }

    @Override
    public void updateBuildOnCompletion(@Nonnull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        LOGGER.log(Level.FINE, "updateBuildOnCompletion({0}, {1}, result: {2}, startTime): {3}, duration: {4}",
                new Object[]{jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_BUILD " +
                    "SET RESULT_ID = ?, START_TIME = ?, DURATION_IN_MILLIS = ? " +
                    "WHERE ID = ?")) {
                stmt.setInt(1, buildResultOrdinal);
                stmt.setTimestamp(2, new Timestamp(startTimeInMillis));
                stmt.setLong(3, durationInMillis);
                stmt.setLong(4, buildPrimaryKey);
                int count = stmt.executeUpdate();
                if (count != 1) {
                    LOGGER.log(Level.WARNING, "updateBuildOnCompletion - more/less than 1 jenkins_build record updated (" +
                            count + ") for " + jobFullName + "#" + buildNumber);
                }
            }

            if (Result.SUCCESS.ordinal == buildResultOrdinal) {
                try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB set LAST_BUILD_NUMBER = ?, LAST_SUCCESSFUL_BUILD_NUMBER = ? where FULL_NAME = ?")) {
                    stmt.setInt(1, buildNumber);
                    stmt.setInt(2, buildNumber);
                    stmt.setString(3, jobFullName);
                    int count = stmt.executeUpdate();
                    if (count != 1) {
                        LOGGER.log(Level.WARNING, "updateBuildOnCompletion - more/less than 1 jenkins_job record updated (" +
                                count + ") for " + jobFullName + "#" + buildNumber);
                    }
                }
            } else {
                try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB set LAST_BUILD_NUMBER = ? where FULL_NAME = ?")) {
                    stmt.setInt(1, buildNumber);
                    stmt.setString(2, jobFullName);
                    int count = stmt.executeUpdate();
                    if (count != 1) {
                        LOGGER.log(Level.WARNING, "updateBuildOnCompletion - more/less than 1 jenkins_job record updated (" +
                                count + ") for " + jobFullName + "#" + buildNumber);
                    }
                }
            }

            cnn.commit();
        } catch (SQLException e) {
            throw new RuntimeSqlException("Exception updating build " + jobFullName + "#" + buildNumber + " with result " + buildResultOrdinal, e);
        }
    }
}
