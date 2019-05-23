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

import org.postgresql.util.PSQLState;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginPostgreSqlDao extends AbstractPipelineMavenPluginDao {

    public PipelineMavenPluginPostgreSqlDao(@Nonnull DataSource ds) {
        super(ds);
    }

    @Override
    public String getJdbcScheme() {
        return "postgresql";
    }

    @Override
    protected void registerJdbcDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver 'org.postgresql.Driver' not found. Please install the 'PostgreSQL Database Plugin' to install the PostgreSQL driver");
        }
    }

    @Override
    protected String getDatabaseDescription() {
        try (Connection cnn = getDataSource().getConnection()) {
            DatabaseMetaData metaData = cnn.getMetaData();
            String version = metaData. getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            try (Statement stmt = cnn.createStatement()) {
                try (ResultSet rst = stmt.executeQuery("select AURORA_VERSION()")) {
                    rst.next();
                    version += " / Amazon Aurora " + rst.getString(1);
                } catch (SQLException e) {
                    if (PSQLState.UNDEFINED_FUNCTION.getState().equals(e.getSQLState())) { // " 42883 - ERROR: function aurora_version() does not exist"
                        // not Amazon aurora, the function aurora_version() does not exist
                    } else {
                        LOGGER.log(Level.WARNING, "Exception checking Amazon Aurora version", e);
                    }
                }
            }
            return version;
        } catch (SQLException e) {
            return "#" + e.toString() + "#";
        }
    }

    @Override
    protected Long getGeneratedPrimaryKey(PreparedStatement stmt, String column) throws SQLException {
        Long jobPrimaryKey;
        try (ResultSet rst = stmt.getGeneratedKeys()) {
            if (rst.next()) {
                jobPrimaryKey = rst.getLong(column);
            } else {
                throw new IllegalStateException();
            }
        }
        return jobPrimaryKey;
    }

    @Override
    public boolean isEnoughProductionGradeForTheWorkload() {
        return true;
    }
}
