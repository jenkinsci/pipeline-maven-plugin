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

import com.mysql.cj.exceptions.MysqlErrorNumbers;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginMySqlDao extends AbstractPipelineMavenPluginDao {
    public PipelineMavenPluginMySqlDao(@Nonnull DataSource ds) {
        super(ds);
    }

    @Override
    public String getJdbcScheme() {
        return "mysql";
    }

    @Override
    protected boolean isIgnoreSqlLoadingException(SQLException e) {
        if ( MysqlErrorNumbers.SQL_STATE_ILLEGAL_ARGUMENT.equals(e.getSQLState())) {
            // empty string
            return true;
        } else if (MysqlErrorNumbers.ER_EMPTY_QUERY == e.getErrorCode()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void registerJdbcDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySql driver 'com.mysql.cj.jdbc.Driver' not found. Please install the 'MySQL Database Plugin' to install the MySql driver");
        }
    }
}
