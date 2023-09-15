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

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginMySqlDaoTest extends PipelineMavenPluginDaoAbstractTest {

    @Override
    public DataSource before_newDataSource() {
        return JdbcConnectionPool.create("jdbc:h2:mem:;MODE=MYSQL", "sa", "");
    }

    @Override
    public AbstractPipelineMavenPluginDao before_newAbstractPipelineMavenPluginDao(DataSource ds) {
        return new PipelineMavenPluginMySqlDao(ds) {
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
    public void test_mariadb_version_parsing_JENKINS_55378() {
        String actual = PipelineMavenPluginMySqlDao.extractMariaDbVersion("5.5.5-10.2.20-MariaDB");
        assertThat(actual).isEqualTo("10.2.20");
    }

    /**
     * docker run -e MYSQL_ROOT_PASSWORD=mypass -e MYSQL_DATABASE=jenkins -e
     * MYSQL_USER=jenkins -e MYSQL_PASSWORD=jenkins -p 3307:3306 -d
     * mariadb/server:latest
     */
    @Test
    public void test_mariadb_version_parsing_mariadb_as_docker_container() {
        String actual = PipelineMavenPluginMySqlDao.extractMariaDbVersion("5.5.5-10.3.11-MariaDB-1:10.3.11+maria~bionic");
        assertThat(actual).isEqualTo("10.3.11");
    }
}
