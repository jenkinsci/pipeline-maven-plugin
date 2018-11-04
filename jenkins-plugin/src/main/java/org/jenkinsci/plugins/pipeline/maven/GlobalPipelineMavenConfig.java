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

package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.google.common.base.Strings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hudson.Extension;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2v1Dao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMonitoringDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sql.DataSource;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension(ordinal = 50)
@Symbol("pipelineMaven")
public class GlobalPipelineMavenConfig extends GlobalConfiguration {

    private final static Logger LOGGER = Logger.getLogger(GlobalPipelineMavenConfig.class.getName());

    private transient PipelineMavenPluginDao dao;

    private transient PipelineTriggerService pipelineTriggerService;

    private boolean triggerDownstreamUponResultSuccess = true;
    private boolean triggerDownstreamUponResultUnstable;
    private boolean triggerDownstreamUponResultFailure;
    private boolean triggerDownstreamUponResultNotBuilt;
    private boolean triggerDownstreamUponResultAborted;

    private String jdbcUrl;
    private String jdbcCredentialsId;
    private String properties;

    @DataBoundConstructor
    public GlobalPipelineMavenConfig() {
        load();
    }

    @Override
    public ToolConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(ToolConfigurationCategory.class);
    }

    private List<MavenPublisher> publisherOptions;

    @CheckForNull
    public List<MavenPublisher> getPublisherOptions() {
        return publisherOptions;
    }

    @DataBoundSetter
    public void setPublisherOptions(List<MavenPublisher> publisherOptions) {
        this.publisherOptions = publisherOptions;
    }

    public boolean isTriggerDownstreamUponResultSuccess() {
        return triggerDownstreamUponResultSuccess;
    }

    @DataBoundSetter
    public void setTriggerDownstreamUponResultSuccess(boolean triggerDownstreamUponResultSuccess) {
        this.triggerDownstreamUponResultSuccess = triggerDownstreamUponResultSuccess;
    }

    public boolean isTriggerDownstreamUponResultUnstable() {
        return triggerDownstreamUponResultUnstable;
    }

    @DataBoundSetter
    public void setTriggerDownstreamUponResultUnstable(boolean triggerDownstreamUponResultUnstable) {
        this.triggerDownstreamUponResultUnstable = triggerDownstreamUponResultUnstable;
    }

    public boolean isTriggerDownstreamUponResultFailure() {
        return triggerDownstreamUponResultFailure;
    }

    @DataBoundSetter
    public void setTriggerDownstreamUponResultFailure(boolean triggerDownstreamUponResultFailure) {
        this.triggerDownstreamUponResultFailure = triggerDownstreamUponResultFailure;
    }

    public boolean isTriggerDownstreamUponResultNotBuilt() {
        return triggerDownstreamUponResultNotBuilt;
    }

    @DataBoundSetter
    public void setTriggerDownstreamUponResultNotBuilt(boolean triggerDownstreamUponResultNotBuilt) {
        this.triggerDownstreamUponResultNotBuilt = triggerDownstreamUponResultNotBuilt;
    }

    public boolean isTriggerDownstreamUponResultAborted() {
        return triggerDownstreamUponResultAborted;
    }

    @DataBoundSetter
    public void setTriggerDownstreamUponResultAborted(boolean triggerDownstreamUponResultAborted) {
        this.triggerDownstreamUponResultAborted = triggerDownstreamUponResultAborted;
    }

    public synchronized String getJdbcUrl() {
        return jdbcUrl;
    }

    @DataBoundSetter
    public synchronized void setJdbcUrl(String jdbcUrl) {
        if (!Objects.equals(jdbcUrl, this.jdbcUrl)) {
            this.dao = null;
        }
        this.jdbcUrl = jdbcUrl;
    }

    public synchronized String getJdbcCredentialsId() {
        return jdbcCredentialsId;
    }

    @DataBoundSetter
    public synchronized void setJdbcCredentialsId(String jdbcCredentialsId) {
        if (!Objects.equals(jdbcCredentialsId, this.jdbcCredentialsId)) {
            this.dao = null;
        }
        this.jdbcCredentialsId = jdbcCredentialsId;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.publisherOptions = req.bindJSONToList(MavenPublisher.class, json.get("publisherOptions"));
        save();
        return true;
    }

    @Nonnull
    public synchronized PipelineMavenPluginDao getDao() {
        if (dao == null) {
            try {
                String jdbcUrl, jdbcUserName, jdbcPassword;
                if (Strings.isNullOrEmpty(this.jdbcUrl)) {
                    // default embedded H2 database
                    File databaseRootDir = new File(Jenkins.getInstance().getRootDir(), "jenkins-jobs");
                    if (!databaseRootDir.exists()) {
                        boolean created = databaseRootDir.mkdirs();
                        if (!created) {
                            throw new IllegalStateException("Failure to create database root dir " + databaseRootDir);
                        }
                    }
                    jdbcUrl = "jdbc:h2:file:" + new File(databaseRootDir, "jenkins-jobs").getAbsolutePath() + ";" +
                            "AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
                    jdbcUserName = "sa";
                    jdbcPassword = "sa";
                } else {
                    jdbcUrl = this.jdbcUrl;
                    if (this.jdbcCredentialsId == null)
                        throw new IllegalStateException("No credentials defined for JDBC URL '" + jdbcUrl + "'");

                    UsernamePasswordCredentials jdbcCredentials = (UsernamePasswordCredentials) CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(),
                                    ACL.SYSTEM, Collections.EMPTY_LIST),
                            CredentialsMatchers.withId(this.jdbcCredentialsId));
                    if (jdbcCredentials == null) {
                        throw new IllegalStateException("Credentials '" + jdbcCredentialsId + "' defined for JDBC URL '" + jdbcUrl + "' NOT found");
                    }
                    jdbcUserName = jdbcCredentials.getUsername();
                    jdbcPassword = Secret.toString(jdbcCredentials.getPassword());
                }

                HikariConfig dsConfig = new HikariConfig();
                dsConfig.setJdbcUrl(jdbcUrl);
                dsConfig.setUsername(jdbcUserName);
                dsConfig.setPassword(jdbcPassword);

                if (!Strings.isNullOrEmpty(properties)) {
                    Properties p = new Properties();
                    p.load(new StringReader(properties));
                    dsConfig.setDataSourceProperties(p);
                }

                DataSource ds = new HikariDataSource(dsConfig);


                Class<? extends PipelineMavenPluginDao> daoClass;
                if (jdbcUrl.startsWith("jdbc:h2:")) {
                    daoClass = PipelineMavenPluginH2Dao.class;
                } else if (jdbcUrl.startsWith("jdbc:mysql:")) {
                    daoClass = PipelineMavenPluginMySqlDao.class;
                } else {
                    throw new IllegalArgumentException("Unsupported database type in JDBC URL " + jdbcUrl);
                }
                try {
                    dao = new PipelineMavenPluginMonitoringDao(daoClass.getConstructor(DataSource.class).newInstance(ds));
                } catch (Exception e) {
                    throw new SQLException(
                            "Exception connecting to '" + this.jdbcUrl + "' with credentials '" + this.jdbcCredentialsId + "' (" +
                                    jdbcUserName + "/***) and DAO " + daoClass.getSimpleName(), e);
                }


            } catch (RuntimeException | SQLException | IOException e) {
                LOGGER.log(Level.WARNING, "Exception creating database dao, skip", e);
                dao = new PipelineMavenPluginNullDao();
            }
        }
        return dao;
    }

    @Nonnull
    public synchronized PipelineTriggerService getPipelineTriggerService() {
        if (pipelineTriggerService == null) {
            pipelineTriggerService = new PipelineTriggerService(this);
        }
        return pipelineTriggerService;
    }

    @Nonnull
    public Set<Result> getTriggerDownstreamBuildsResultsCriteria() {
        Set<Result> result = new HashSet<>(5);
        if (this.triggerDownstreamUponResultSuccess)
            result.add(Result.SUCCESS);
        if (this.triggerDownstreamUponResultUnstable)
            result.add(Result.UNSTABLE);
        if (this.triggerDownstreamUponResultAborted)
            result.add(Result.ABORTED);
        if (this.triggerDownstreamUponResultNotBuilt)
            result.add(Result.NOT_BUILT);
        if (this.triggerDownstreamUponResultFailure)
            result.add(Result.FAILURE);

        return result;
    }

    @Nullable
    public static GlobalPipelineMavenConfig get() {
        return GlobalConfiguration.all().get(GlobalPipelineMavenConfig.class);
    }

    public ListBoxModel doFillJdbcCredentialsIdItems() {
        // use deprecated "withMatching" because, even after 20 mins of research,
        // I didn't understand how to use the new "recommended" API
        return new StandardListBoxModel()
                .includeEmptyValue()
                .withMatching(
                        CredentialsMatchers.always(),
                        CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class,
                                Jenkins.getInstance(),
                                ACL.SYSTEM,
                                Collections.EMPTY_LIST));
    }
}
