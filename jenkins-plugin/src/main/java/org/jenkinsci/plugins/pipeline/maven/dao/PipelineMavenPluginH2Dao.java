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

import org.h2.jdbcx.JdbcConnectionPool;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2Dao extends AbstractPipelineMavenPluginDao {

    private boolean requiredShutdown = false;

    public PipelineMavenPluginH2Dao(@Nonnull DataSource ds) {
        super(ds);
    }

    public PipelineMavenPluginH2Dao(@Nonnull File rootDir) {
        this(JdbcConnectionPool.create("jdbc:h2:file:" + new File(rootDir, "jenkins-jobs").getAbsolutePath() + ";" +
                "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE", "sa", "sa"));
        requiredShutdown = true;
    }

    @Override
    protected void registerJdbcDriver() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 driver 'org.h2.Driver' not found. Please install the 'H2 Database Plugin' to install the H2 driver");
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
        if (requiredShutdown) {
            try (Connection con = getDataSource().getConnection()) {
                try (Statement stmt = con.createStatement()) {
                    stmt.execute("SHUTDOWN");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to cleanly close the database", e);
            }
        }
        super.close();
    }
}
