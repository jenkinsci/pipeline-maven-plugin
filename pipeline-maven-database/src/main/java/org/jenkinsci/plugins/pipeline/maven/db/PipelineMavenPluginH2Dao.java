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

import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import javax.sql.DataSource;
import jenkins.model.Jenkins;
import org.h2.jdbcx.JdbcConnectionPool;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class PipelineMavenPluginH2Dao extends AbstractPipelineMavenPluginDao {

    public PipelineMavenPluginH2Dao() {
        super();
    }

    public PipelineMavenPluginH2Dao(@NonNull DataSource ds) {
        super(ds);
    }

    @Override
    protected boolean acceptNoCredentials() {
        return true;
    }

    @Override
    public String getDescription() {
        return Messages.dao_h2_description();
    }

    public PipelineMavenPluginH2Dao(@NonNull File rootDir) {
        this(JdbcConnectionPool.create(
                "jdbc:h2:file:" + new File(rootDir, "jenkins-jobs").getAbsolutePath() + ";"
                        + "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE",
                "sa",
                "sa"));
    }

    @Override
    protected void registerJdbcDriver() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "H2 driver 'org.h2.Driver' not found. Please install the 'H2 Database Plugin' to install the H2 driver");
        }
    }

    @Override
    public String getJdbcScheme() {
        return "h2";
    }

    @Override
    public boolean isEnoughProductionGradeForTheWorkload() {
        try (Connection cnn = getDataSource().getConnection()) {
            try (Statement stmt = cnn.createStatement()) {
                try (ResultSet rst = stmt.executeQuery("select count(*) from MAVEN_DEPENDENCY")) {
                    rst.next();
                    int count = rst.getInt(1);
                    if (count > 100) {
                        return false;
                    }
                }
            }
            try (Statement stmt = cnn.createStatement()) {
                try (ResultSet rst = stmt.executeQuery("select count(*) from GENERATED_MAVEN_ARTIFACT")) {
                    rst.next();
                    int count = rst.getInt(1);
                    if (count > 100) {
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.INFO, "Exception counting rows", e);
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        if (isClosed()) {
            LOGGER.log(Level.FINE, "DataSource already closed, cancelling DAO closing");
        } else {
            LOGGER.log(Level.INFO, "Termination of the DAO Requested");
            boolean requireShutdown = false;
            try (Connection conn = getDataSource().getConnection()) {
                String url = conn.getMetaData().getURL();
                // if we are using a file based URL - so the DB needs to be closed.
                requireShutdown = url.startsWith("jdbc:h2:file:");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to determin if the DB needs to be shutdown.", e);
            }
            if (requireShutdown) {
                LOGGER.log(Level.FINE, "Termination of the DAO Requested and shutdown is required");
                try (Connection con = getDataSource().getConnection()) {
                    try (Statement stmt = con.createStatement()) {
                        stmt.execute("SHUTDOWN");
                    }
                } catch (SQLException e) {
                    if (e.getErrorCode() == 90121) {
                        // DATABASE_CALLED_AT_SHUTDOWN (the JVM shutdown hooks are running already :-o )
                        LOGGER.log(Level.FINE, "Failed to close the database as it is already closed", e);
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to cleanly close the database", e);
                    }
                }
            } else {
                LOGGER.log(Level.FINE, "Termination of the DAO Requested not required, as requireShutdown is false");
            }
        }
        super.close();
    }

    @Override
    public String getDefaultJdbcUrl() {
        File databaseRootDir = new File(Jenkins.get().getRootDir(), "jenkins-jobs");
        if (!databaseRootDir.exists()) {
            boolean created = databaseRootDir.mkdirs();
            if (!created) {
                throw new IllegalStateException("Failure to create database root dir " + databaseRootDir);
            }
        }
        return "jdbc:h2:file:" + new File(databaseRootDir, "jenkins-jobs").getAbsolutePath() + ";"
                + "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
    }

    private boolean isClosed() {
        DataSource ds = getDataSource();
        if (ds instanceof HikariDataSource) {
            return ((HikariDataSource) ds).isClosed();
        }
        try (Connection connection = ds.getConnection()) {
            return connection == null || connection.isClosed();
        } catch (Exception ex) {
            return true;
        }
    }
}
