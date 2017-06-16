package org.jenkinsci.plugins.pipeline.maven.dao;

import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeSqlException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2Dao implements PipelineMavenPluginDao, Closeable {

    private static Logger LOGGER = Logger.getLogger(PipelineMavenPluginH2Dao.class.getName());

    private transient JdbcConnectionPool jdbcConnectionPool;

    public PipelineMavenPluginH2Dao(File rootDir) {
        rootDir.getClass(); // check non null

        File databaseFile = new File(rootDir, "pipeline-maven-plugin");
        String jdbcUrl = "jdbc:h2:" + databaseFile.getAbsolutePath() + ";AUTO_SERVER=TRUE";
        jdbcConnectionPool = JdbcConnectionPool.create(jdbcUrl, "sa", "sa");
        LOGGER.log(Level.FINE, "Open database {0}", jdbcUrl);

        initializeDatabase();
    }

    @Override
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope) {
        LOGGER.log(Level.FINE, "recordDependency({0}#{1}, {2}:{3}:{4}:{5}, {6}})", new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type, scope});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, version, type);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO DEPENDENCY(ARTIFACT_ID, BUILD_ID, SCOPE) VALUES (?, ?, ?)")) {
                stmt.setLong(1, artifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
                stmt.setString(3, scope);
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type) {
        LOGGER.log(Level.FINE, "recordGeneratedArtifact({0}#{1}, {2}:{3}:{4}:{5}})", new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type});
        long buildPrimaryKey = getOrCreateBuildPrimaryKey(jobFullName, buildNumber);
        long artifactPrimaryKey = getOrCreateArtifactPrimaryKey(groupId, artifactId, version, type);

        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO GENERATED(ARTIFACT_ID, BUILD_ID) VALUES (?, ?)")) {
                stmt.setLong(1, artifactPrimaryKey);
                stmt.setLong(2, buildPrimaryKey);
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void renameJob(String oldFullName, String newFullName) {
        LOGGER.log(Level.FINER, "renameJob({0}, {1})", new Object[]{oldFullName, newFullName});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement("UPDATE BUILD SET JOB_FULL_NAME = ? WHERE JOB_FULL_NAME = ?")) {
                stmt.setString(1, newFullName);
                stmt.setString(2, oldFullName);
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "renameJob({0}, {1}): {2}", new Object[]{oldFullName, newFullName, count});
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void deleteJob(String jobFullName) {
        LOGGER.log(Level.FINER, "deleteJob({0})", new Object[]{jobFullName});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement("DELETE FROM BUILD WHERE JOB_FULL_NAME = ?")) {
                stmt.setString(1, jobFullName);
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "deleteJob({0}): {1}", new Object[]{jobFullName, count});
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void deleteBuild(String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINER, "deleteBuild({0}#{1})", new Object[]{jobFullName, buildNumber});
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            try (PreparedStatement stmt = cnn.prepareStatement("DELETE FROM BUILD WHERE JOB_FULL_NAME = ? AND BUILD_NUMBER = ?")) {
                stmt.setString(1, jobFullName);
                stmt.setInt(1, buildNumber);
                int count = stmt.executeUpdate();
                LOGGER.log(Level.FINE, "deleteJob({0}#{1}): {2}", new Object[]{jobFullName, buildNumber, count});
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void cleanup() {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            String sql = "DELETE FROM ARTIFACT WHERE ID NOT IN (SELECT DISTINCT ARTIFACT_ID FROM DEPENDENCY UNION SELECT DISTINCT ARTIFACT_ID FROM GENERATED)";
            try (Statement stmt = cnn.createStatement()) {
                int count = stmt.executeUpdate(sql);
                LOGGER.log(Level.FINE, "cleanup(): {0}", new Object[]{count});
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    private long getOrCreateBuildPrimaryKey(String jobFullName, int buildNumber) {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            // get or create build record
            Long buildPrimaryKey = null;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM BUILD WHERE JOB_FULL_NAME = ? AND BUILD_NUMBER = ?")) {
                stmt.setString(1, jobFullName);
                stmt.setInt(2, buildNumber);
                try (ResultSet rst = stmt.executeQuery()) {
                    if (rst.next()) {
                        buildPrimaryKey = rst.getLong(1);
                    }
                }
            }
            if (buildPrimaryKey == null) {
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO BUILD(JOB_FULL_NAME, BUILD_NUMBER) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, jobFullName);
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
            return buildPrimaryKey;
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    private long getOrCreateArtifactPrimaryKey(String groupId, String artifactId, String version, String type) {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            // get or create build record
            Long artifactPrimaryKey = null;
            try (PreparedStatement stmt = cnn.prepareStatement("SELECT ID FROM ARTIFACT WHERE GROUP_ID = ? AND ARTIFACT_ID = ? AND VERSION = ? AND TYPE = ?")) {
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
                try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO ARTIFACT(GROUP_ID, ARTIFACT_ID, VERSION, TYPE) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
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
            return artifactPrimaryKey;
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    private void initializeDatabase() {
    }

    @Override
    public void close() throws IOException {
        jdbcConnectionPool.dispose();
    }
}
