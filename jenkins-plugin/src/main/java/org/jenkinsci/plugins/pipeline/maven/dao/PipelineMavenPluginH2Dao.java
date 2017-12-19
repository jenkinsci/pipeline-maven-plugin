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
import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.util.ClassUtils;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeIoException;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeSqlException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2Dao implements PipelineMavenPluginDao {

    private static Logger LOGGER = Logger.getLogger(PipelineMavenPluginH2Dao.class.getName());

    private transient JdbcConnectionPool jdbcConnectionPool;

    public PipelineMavenPluginH2Dao(File rootDir) {
        rootDir.getClass(); // check non null

        File databaseFile = new File(rootDir, "jenkins-jobs");
        String jdbcUrl = "jdbc:h2:file:" + databaseFile.getAbsolutePath() + ";AUTO_SERVER=TRUE;MULTI_THREADED=1";
        if(LOGGER.isLoggable(Level.FINEST)) {
            jdbcUrl += ";TRACE_LEVEL_SYSTEM_OUT=3";
        } else if (LOGGER.isLoggable(Level.FINE)) {
            jdbcUrl += ";TRACE_LEVEL_SYSTEM_OUT=2";
        }
        LOGGER.log(Level.INFO, "Open database {0}", jdbcUrl);
        jdbcConnectionPool = JdbcConnectionPool.create(jdbcUrl, "sa", "sa");

        initializeDatabase();
        testDatabase();
    }

    public PipelineMavenPluginH2Dao(JdbcConnectionPool jdbcConnectionPool) {
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
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope, boolean ignoreUpstreamTriggers) {
        LOGGER.log(Level.FINE, "recordDependency({0}#{1}, {2}:{3}:{4}:{5}, {6}, ignoreUpstreamTriggers:{7}})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, version, type);

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

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        LOGGER.log(Level.FINE, "recordParentProject({0}#{1}, {2}:{3} ignoreUpstreamTriggers:{5}})",
                new Object[]{jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long parentArtifactPrimaryKey = getOrCreateArtifactPrimaryKey(parentGroupId, parentArtifactId, parentVersion, "pom");

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
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String baseVersion, boolean skipDownstreamTriggers) {
        LOGGER.log(Level.FINE, "recordGeneratedArtifact({0}#{1}, {2}:{3}:{4}:{5}, version:{6}, skipDownstreamTriggers:{7})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, baseVersion, type, version, skipDownstreamTriggers});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, baseVersion, type);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO GENERATED_MAVEN_ARTIFACT(ARTIFACT_ID, BUILD_ID, VERSION, SKIP_DOWNSTREAM_TRIGGERS) VALUES (?, ?, ?, ?)")) {
                stmt.setLong(1, artifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
                stmt.setString(3, version);
                stmt.setBoolean(4, skipDownstreamTriggers);
                stmt.execute();
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
            try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB SET FULL_NAME = ? WHERE FULL_NAME = ?")) {
                stmt.setString(1, newFullName);
                stmt.setString(2, oldFullName);
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
            try (PreparedStatement stmt = cnn.prepareStatement("DELETE FROM JENKINS_JOB WHERE FULL_NAME = ?")) {
                stmt.setString(1, jobFullName);
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
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM JENKINS_JOB WHERE FULL_NAME = ?")) {
                stmt.setString(1, jobFullName);
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        jobPrimaryKey = rst.getLong(1);
                    } else {
                        jobPrimaryKey = null;
                    }
                }
            }
            if (jobPrimaryKey == null) {
                LOGGER.log(Level.FINE, "No record found for job {0}", new Object[]{jobFullName});
                return;
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
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM JENKINS_JOB WHERE FULL_NAME=?")) {
                stmt.setString(1, jobFullName);
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        jobPrimaryKey = rst.getLong(1);
                    }
                }
            }
            if (jobPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO JENKINS_JOB(FULL_NAME) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, jobFullName);
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

    protected long getOrCreateArtifactPrimaryKey(String groupId, String artifactId, String version, String type) {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            cnn.setAutoCommit(false);
            // get or create build record
            Long artifactPrimaryKey = null;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM MAVEN_ARTIFACT WHERE GROUP_ID = ? AND ARTIFACT_ID = ? AND VERSION = ? AND TYPE = ?")) {
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
            if (artifactPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO MAVEN_ARTIFACT(GROUP_ID, ARTIFACT_ID, VERSION, TYPE) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, groupId);
                    stmt.setString(2, artifactId);
                    stmt.setString(3, version);
                    stmt.setString(4, type);
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
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        List<String> downstreamJobs = listDownstreamPipelinesBasedOnMavenDependencies(jobFullName, buildNumber);
        downstreamJobs.addAll(listDownstreamPipelinesBasedOnParentProjectDependencies(jobFullName, buildNumber));
        return downstreamJobs;
    }

    protected List<String> listDownstreamPipelinesBasedOnMavenDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamJobs({0}, {1})", new Object[]{jobFullName, buildNumber});
        String generatedArtifactsSql = "SELECT DISTINCT GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID " +
                " FROM GENERATED_MAVEN_ARTIFACT " +
                " INNER JOIN JENKINS_BUILD AS UPSTREAM_BUILD ON GENERATED_MAVEN_ARTIFACT.BUILD_ID = UPSTREAM_BUILD.ID " +
                " INNER JOIN JENKINS_JOB AS UPSTREAM_JOB ON UPSTREAM_BUILD.JOB_ID = UPSTREAM_JOB.ID " +
                " WHERE " +
                "   UPSTREAM_JOB.FULL_NAME = ? AND" +
                "   UPSTREAM_BUILD.NUMBER = ? AND " +
                "   GENERATED_MAVEN_ARTIFACT.SKIP_DOWNSTREAM_TRIGGERS = FALSE";

        String sql = "SELECT DISTINCT DOWNSTREAM_JOB.FULL_NAME " +
                " FROM JENKINS_JOB AS DOWNSTREAM_JOB" +
                " INNER JOIN JENKINS_BUILD AS DOWNSTREAM_BUILD ON DOWNSTREAM_JOB.ID = DOWNSTREAM_BUILD.JOB_ID " +
                " INNER JOIN MAVEN_DEPENDENCY ON DOWNSTREAM_BUILD.ID = MAVEN_DEPENDENCY.BUILD_ID" +
                " WHERE " +
                "   MAVEN_DEPENDENCY.ARTIFACT_ID IN (" + generatedArtifactsSql + ") AND " +
                "   MAVEN_DEPENDENCY.IGNORE_UPSTREAM_TRIGGERS = FALSE AND " +
                "   DOWNSTREAM_BUILD.NUMBER in (SELECT MAX(JENKINS_BUILD.NUMBER) FROM JENKINS_BUILD WHERE DOWNSTREAM_JOB.ID = JENKINS_BUILD.JOB_ID)" +
                " ORDER BY DOWNSTREAM_JOB.FULL_NAME";

        List<String> downstreamJobsFullNames = new ArrayList<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setInt(2, buildNumber);
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

    protected List<String> listDownstreamPipelinesBasedOnParentProjectDependencies(@Nonnull String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "listDownstreamPipelinesBasedOnParentProjectDependencies({0}, {1})", new Object[]{jobFullName, buildNumber});
        String generatedArtifactsSql = "SELECT DISTINCT GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID " +
                " FROM GENERATED_MAVEN_ARTIFACT " +
                " INNER JOIN JENKINS_BUILD AS UPSTREAM_BUILD ON GENERATED_MAVEN_ARTIFACT.BUILD_ID = UPSTREAM_BUILD.ID " +
                " INNER JOIN JENKINS_JOB AS UPSTREAM_JOB ON UPSTREAM_BUILD.JOB_ID = UPSTREAM_JOB.ID " +
                " WHERE " +
                "   UPSTREAM_JOB.FULL_NAME = ? AND" +
                "   UPSTREAM_BUILD.NUMBER = ? AND " +
                "   GENERATED_MAVEN_ARTIFACT.SKIP_DOWNSTREAM_TRIGGERS = FALSE";

        String sql = "SELECT DISTINCT DOWNSTREAM_JOB.FULL_NAME " +
                " FROM JENKINS_JOB AS DOWNSTREAM_JOB" +
                " INNER JOIN JENKINS_BUILD AS DOWNSTREAM_BUILD ON DOWNSTREAM_JOB.ID = DOWNSTREAM_BUILD.JOB_ID " +
                " INNER JOIN MAVEN_PARENT_PROJECT ON DOWNSTREAM_BUILD.ID = MAVEN_PARENT_PROJECT.BUILD_ID" +
                " WHERE " +
                "   MAVEN_PARENT_PROJECT.ARTIFACT_ID IN (" + generatedArtifactsSql + ") AND " +
                "   MAVEN_PARENT_PROJECT.IGNORE_UPSTREAM_TRIGGERS = FALSE AND " +
                "   DOWNSTREAM_BUILD.NUMBER in (SELECT MAX(JENKINS_BUILD.NUMBER) FROM JENKINS_BUILD WHERE DOWNSTREAM_JOB.ID = JENKINS_BUILD.JOB_ID)" +
                " ORDER BY DOWNSTREAM_JOB.FULL_NAME";

        List<String> downstreamJobsFullNames = new ArrayList<>();
        LOGGER.log(Level.FINER, "sql: {0}, jobFullName:{1}, buildNumber: {2}", new Object[]{sql, jobFullName, buildNumber});

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                stmt.setString(1, jobFullName);
                stmt.setInt(2, buildNumber);
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

    /**
     * List the artifacts generated by the given build
     *
     * @param jobFullName see {@link Item#getFullName()}
     * @param buildNumber see {@link Run#getNumber()}
     * @return list of artifact details stored as maps ("gav", "type", "skip_downstream_triggers")
     */
    @Nonnull
    public List<Map<String, String>> getGeneratedArtifacts(@Nonnull String jobFullName, @Nonnull int buildNumber) {
        LOGGER.log(Level.FINER, "getGeneratedArtifacts({0}, {1})", new Object[]{jobFullName, buildNumber});
        String generatedArtifactsSql = "SELECT DISTINCT MAVEN_ARTIFACT.*,  GENERATED_MAVEN_ARTIFACT.* " +
                " FROM MAVEN_ARTIFACT " +
                " INNER JOIN GENERATED_MAVEN_ARTIFACT ON MAVEN_ARTIFACT.ID = GENERATED_MAVEN_ARTIFACT.ARTIFACT_ID" +
                " INNER JOIN JENKINS_BUILD AS UPSTREAM_BUILD ON GENERATED_MAVEN_ARTIFACT.BUILD_ID = UPSTREAM_BUILD.ID " +
                " INNER JOIN JENKINS_JOB AS UPSTREAM_JOB ON UPSTREAM_BUILD.JOB_ID = UPSTREAM_JOB.ID " +
                " WHERE " +
                "   UPSTREAM_JOB.FULL_NAME = ? AND" +
                "   UPSTREAM_BUILD.NUMBER = ? ";

        List<Map<String, String>> results = new ArrayList<>();
        try (Connection cnn = this.jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement(generatedArtifactsSql)) {
                stmt.setString(1, jobFullName);
                stmt.setInt(2, buildNumber);
                try (ResultSet rst = stmt.executeQuery()) {
                    while (rst.next()) {
                        Map<String, String> artifact = new HashMap<>();

                        String gav =
                                rst.getString("maven_artifact.group_id") + ":" +
                                        rst.getString("maven_artifact.artifact_id") + ":" +
                                        rst.getString("maven_artifact.version");
                        artifact.put("gav", gav);
                        artifact.put("type", rst.getString("maven_artifact.type"));
                        artifact.put("skip_downstream_triggers", rst.getString("generated_maven_artifact.skip_downstream_triggers"));
                        results.add(artifact);
                    }
                }
            }
        } catch(SQLException e) {
            throw new RuntimeSqlException(e);
        }

        return results;
    }

    @Override
    public String toPrettyString() {
        List<String> prettyStrings = new ArrayList<>();
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            prettyStrings.add("jdbc.url: " + cnn.getMetaData().getURL());
            List<String> tables = Arrays.asList("MAVEN_ARTIFACT", "JENKINS_JOB", "JENKINS_BUILD", "MAVEN_DEPENDENCY", "GENERATED_MAVEN_ARTIFACT", "MAVEN_PARENT_PROJECT");
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
                    prettyStrings.add("Table " + table + ": "+ e);
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
}
