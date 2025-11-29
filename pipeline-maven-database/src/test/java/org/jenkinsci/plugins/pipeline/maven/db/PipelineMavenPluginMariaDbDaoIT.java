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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testcontainers.images.PullPolicy.alwaysPull;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.util.Collections;
import javax.sql.DataSource;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Testcontainers(disabledWithoutDocker = true) // Testcontainers does not support docker on Windows 2019 servers
public class PipelineMavenPluginMariaDbDaoIT extends PipelineMavenPluginDaoAbstractTest {

    @Container
    public static MariaDBContainer DB = new MariaDBContainer(MariaDBContainer.NAME).withImagePullPolicy(alwaysPull());

    @Override
    public DataSource before_newDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB.getJdbcUrl());
        config.setUsername(DB.getUsername());
        config.setPassword(DB.getPassword());
        return new HikariDataSource(config);
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
    public void ensureValidateConfiguration() throws Exception {
        try (MockedStatic<Jenkins> j = mockStatic(Jenkins.class);
                MockedStatic<CredentialsMatchers> m = mockStatic(CredentialsMatchers.class);
                MockedStatic<CredentialsProvider> c = mockStatic(CredentialsProvider.class)) {
            PipelineMavenPluginDao.Builder.Config config = new PipelineMavenPluginDao.Builder.Config()
                    .jdbcUrl(DB.getJdbcUrl())
                    .credentialsId("credsId");
            UsernamePasswordCredentials credentials = mock(UsernamePasswordCredentials.class);
            Secret password = Secret.fromString(DB.getPassword());
            String version = DB.createConnection("").getMetaData().getDatabaseProductVersion();
            j.when(Jenkins::get).thenReturn(null);
            m.when(() -> CredentialsMatchers.withId("credsId")).thenReturn(null);
            c.when(() -> CredentialsProvider.lookupCredentials(
                            UsernamePasswordCredentials.class, (Jenkins) null, ACL.SYSTEM, Collections.EMPTY_LIST))
                    .thenReturn(null);
            c.when(() -> CredentialsMatchers.firstOrNull(null, null)).thenReturn(credentials);
            when(credentials.getUsername()).thenReturn(DB.getUsername());
            when(credentials.getPassword()).thenReturn(password);

            FormValidation result = dao.getBuilder().validateConfiguration(config);

            assertThat(result.toString()).isEqualTo("OK: MariaDB " + version + " is a supported database");
        }
    }
}
