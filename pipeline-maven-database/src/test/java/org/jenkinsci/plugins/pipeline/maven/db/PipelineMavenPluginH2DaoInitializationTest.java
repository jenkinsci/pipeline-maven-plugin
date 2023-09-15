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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;
import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2DaoInitializationTest {

    @Test
    public void initialize_database() {
        JdbcConnectionPool jdbcConnectionPool = JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");

        PipelineMavenPluginH2Dao dao = new PipelineMavenPluginH2Dao(jdbcConnectionPool) {
            @Override
            protected MigrationStep.JenkinsDetails getJenkinsDetails() {
                return new MigrationStep.JenkinsDetails() {
                    @Override
                    public String getMasterLegacyInstanceId() {
                        return "123456";
                    }

                    @Override
                    public String getMasterRootUrl() {
                        return "https://jenkins.mycompany.com/";
                    }
                };
            }
        };
    }

    @Test
    public void initialize_database_with_null_master_url() throws SQLException {
        JdbcConnectionPool jdbcConnectionPool = JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");

        PipelineMavenPluginH2Dao daoWithEmptyJenkinsUrl = new PipelineMavenPluginH2Dao(jdbcConnectionPool) {
            @Override
            protected MigrationStep.JenkinsDetails getJenkinsDetails() {
                return new MigrationStep.JenkinsDetails() {
                    @NonNull
                    @Override
                    public String getMasterLegacyInstanceId() {
                        return "123456";
                    }

                    @NonNull
                    @Override
                    public String getMasterRootUrl() {
                        return "";
                    }
                };
            }
        };

        // VERIFY THAT THE JENKINS_MASTER TABLE IS INITIALIZED WITH AN
        // JENKINS_MASTER.URL = ""
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            Long jenkinsMasterPrimaryKey = daoWithEmptyJenkinsUrl.getJenkinsMasterPrimaryKey(cnn);

            try (PreparedStatement stmt = cnn.prepareStatement("SELECT URL FROM JENKINS_MASTER WHERE ID = ?")) {
                stmt.setLong(1, jenkinsMasterPrimaryKey);
                try (ResultSet rst = stmt.executeQuery()) {
                    rst.next();
                    assertThat(rst.getString("URL")).isEqualTo("");
                }
            }
        }

        PipelineMavenPluginH2Dao daoWithValidJenkinsUrl = new PipelineMavenPluginH2Dao(jdbcConnectionPool) {
            @Override
            protected MigrationStep.JenkinsDetails getJenkinsDetails() {
                return new MigrationStep.JenkinsDetails() {
                    @NonNull
                    @Override
                    public String getMasterLegacyInstanceId() {
                        return "123456";
                    }

                    @NonNull
                    @Override
                    public String getMasterRootUrl() {
                        return "http://jenkins.mycompany.com";
                    }
                };
            }
        };

        // VERIFY THAT THE JENKINS_MASTER TABLE IS UPDATE WITH AN JENKINS_MASTER.URL =
        // "http://jenkins.mycompany.com"
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            Long jenkinsMasterPrimaryKey = daoWithValidJenkinsUrl.getJenkinsMasterPrimaryKey(cnn);

            try (PreparedStatement stmt = cnn.prepareStatement("SELECT URL FROM JENKINS_MASTER WHERE ID = ?")) {
                stmt.setLong(1, jenkinsMasterPrimaryKey);
                try (ResultSet rst = stmt.executeQuery()) {
                    rst.next();
                    assertThat(rst.getString("URL")).isEqualTo("http://jenkins.mycompany.com");
                }
            }
        }
    }
}
