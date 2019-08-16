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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hudson.Extension;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMonitoringDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMySqlDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginPostgreSqlDao;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sql.DataSource;

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
            PipelineMavenPluginDao daoToClose = this.dao;
            this.dao = null;
            if (daoToClose instanceof Closeable) {
                try {
                    ((Closeable) daoToClose).close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception closing the previous DAO", e);
                }
            }
        }
        this.jdbcUrl = jdbcUrl;
    }

    public synchronized String getJdbcCredentialsId() {
        return jdbcCredentialsId;
    }

    public synchronized String getProperties() {
        return properties;
    }

    @DataBoundSetter
    public synchronized void setProperties(String properties) {
        if (!Objects.equals(properties, this.properties)) {
            PipelineMavenPluginDao daoToClose = this.dao;
            this.dao = null;
            if (daoToClose instanceof Closeable) {
                try {
                    ((Closeable) daoToClose).close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception closing the previous DAO", e);
                }
            }
        }
        this.properties = properties;
    }

    @DataBoundSetter
    public synchronized void setJdbcCredentialsId(String jdbcCredentialsId) {
        if (!Objects.equals(jdbcCredentialsId, this.jdbcCredentialsId)) {
            PipelineMavenPluginDao daoToClose = this.dao;
            this.dao = null;
            if (daoToClose instanceof Closeable) {
                try {
                    ((Closeable) daoToClose).close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception closing the previous DAO", e);
                }
            }
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
                if (StringUtils.isBlank(this.jdbcUrl)) {
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
                dsConfig.setAutoCommit(false);

                Properties p = new Properties();
                // todo refactor the DAO to inject config defaults in the DAO
                if (jdbcUrl.startsWith("jdbc:mysql")) {
                    // https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby
                    // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
                    p.setProperty("dataSource.cachePrepStmts", "true");
                    p.setProperty("dataSource.prepStmtCacheSize", "250");
                    p.setProperty("dataSource.prepStmtCacheSqlLimit", "2048");
                    p.setProperty("dataSource.useServerPrepStmts", "true");
                    p.setProperty("dataSource.useLocalSessionState", "true");
                    p.setProperty("dataSource.rewriteBatchedStatements", "true");
                    p.setProperty("dataSource.cacheResultSetMetadata", "true");
                    p.setProperty("dataSource.cacheServerConfiguration", "true");
                    p.setProperty("dataSource.elideSetAutoCommits", "true");
                    p.setProperty("dataSource.maintainTimeStats", "false");
                } else if (jdbcUrl.startsWith("jdbc:postgresql")) {
                    // no tuning recommendations found for postgresql
                } else if (jdbcUrl.startsWith("jdbc:h2")) {
                    // dsConfig.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource"); don't specify the datasource due to a classloading issue
                }
                if (StringUtils.isNotBlank(properties)) {
                    p.load(new StringReader(properties));
                }
                dsConfig.setDataSourceProperties(p);

                // TODO cleanup this quick fix for JENKINS-54587, we should have a better solution with the JDBC driver loaded by the DAO itself
                try {
                    DriverManager.getDriver(jdbcUrl);
                } catch (SQLException e) {
                    if ("08001".equals(e.getSQLState()) && 0 == e.getErrorCode()) {
                        // if it's a "No suitable driver" exception, we try to load the jdbc driver and retry
                        if (jdbcUrl.startsWith("jdbc:h2:")) {
                            try {
                                Class.forName("org.h2.Driver");
                            } catch (ClassNotFoundException cnfe) {
                                throw new IllegalStateException("H2 driver should be bundled with this plugin");
                            }
                        } else if (jdbcUrl.startsWith("jdbc:mysql:")) {
                            try {
                                Class.forName("com.mysql.cj.jdbc.Driver");
                            } catch (ClassNotFoundException cnfe) {
                                throw new RuntimeException("MySql driver 'com.mysql.cj.jdbc.Driver' not found. Please install the 'MySQL Database Plugin' to install the MySql driver");
                            }
                        } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
                            try {
                                Class.forName("org.postgresql.Driver");
                            } catch (ClassNotFoundException cnfe) {
                                throw new RuntimeException("PostgreSQL driver 'org.postgresql.Driver' not found. Please install the 'PostgreSQL Database Plugin' to install the PostgreSQL driver");
                            }
                        } else {
                            throw new IllegalArgumentException("Unsupported database type in JDBC URL " + jdbcUrl);
                        }
                        DriverManager.getDriver(jdbcUrl);
                    } else {
                        throw e;
                    }
                }

                LOGGER.log(Level.INFO, "Connect to database {0} with username {1} and properties {2}", new Object[]{jdbcUrl, jdbcUserName, p});
                DataSource ds = new HikariDataSource(dsConfig);

                Class<? extends PipelineMavenPluginDao> daoClass;
                if (jdbcUrl.startsWith("jdbc:h2:")) {
                    daoClass = PipelineMavenPluginH2Dao.class;
                } else if (jdbcUrl.startsWith("jdbc:mysql:")) {
                    daoClass = PipelineMavenPluginMySqlDao.class;
                } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
                    daoClass = PipelineMavenPluginPostgreSqlDao.class;
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
    public FormValidation doValidateJdbcConnection(
                                     @QueryParameter String jdbcUrl,
                                     @QueryParameter String properties,
                                     @QueryParameter String jdbcCredentialsId) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return FormValidation.ok("OK");
        }

        String driverClass = null;
        try {
            if (StringUtils.isBlank(jdbcUrl)) {
                driverClass = "org.h2.Driver";
            } else if (jdbcUrl.startsWith("jdbc:h2")) {
                driverClass = "org.h2.Driver";
            } else if (jdbcUrl.startsWith("jdbc:mysql")) {
                driverClass = "com.mysql.cj.jdbc.Driver";
            } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
                driverClass = "org.postgresql.Driver";
            } else {
                return FormValidation.error("Unsupported database specified in JDBC url '" + jdbcUrl + "'");
            }
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                if ("com.mysql.cj.jdbc.Driver".equals(driverClass)) {
                    return FormValidation.error(e, "MySQL JDBC driver '" + driverClass + "' not found, please install the Jenkins 'MySQL API Plugin'" + jdbcUrl);
                } else if ("org.postgresql.Driver".equals(driverClass)) {
                    return FormValidation.error(e, "PostgreSQL JDBC driver '" + driverClass + "' not found, please install the Jenkins 'PostgreSQL API Plugin'" + jdbcUrl);
                } else {
                    throw e;
                }
            }

            String jdbcUserName, jdbcPassword;
            if (StringUtils.isEmpty(jdbcCredentialsId)) {
                if (StringUtils.isBlank(jdbcUrl)) {
                    // embedded database, assume OK
                    return FormValidation.ok("OK");
                } else {
                    return FormValidation.error("No credentials specified for JDBC url '" + jdbcUrl + "'");
                }
            } else {
                UsernamePasswordCredentials jdbcCredentials = (UsernamePasswordCredentials) CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(),
                                ACL.SYSTEM, Collections.EMPTY_LIST),
                        CredentialsMatchers.withId(jdbcCredentialsId));
                if (jdbcCredentials == null) {
                    return FormValidation.error("Credentials '" + jdbcCredentialsId + "' defined for JDBC URL '" + jdbcUrl + "' not found");
                }
                jdbcUserName = jdbcCredentials.getUsername();
                jdbcPassword = Secret.toString(jdbcCredentials.getPassword());
            }
            HikariConfig dsConfig = new HikariConfig();
            dsConfig.setJdbcUrl(jdbcUrl);
            dsConfig.setUsername(jdbcUserName);
            dsConfig.setPassword(jdbcPassword);

            if (StringUtils.isNotBlank(properties)) {
                Properties p = new Properties();
                try {
                    p.load(new StringReader(properties));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                dsConfig.setDataSourceProperties(p);
            }

            try (HikariDataSource ds = new HikariDataSource(dsConfig)) {
                try (Connection cnn = ds.getConnection()) {
                    DatabaseMetaData metaData = cnn.getMetaData();
                    // getDatabaseProductVersion():
                    // * MySQL: "8.0.13"
                    // * Amazon Aurora: "5.6.10"
                    // * MariaDB: "5.5.5-10.2.20-MariaDB", "5.5.5-10.3.11-MariaDB-1:10.3.11+maria~bionic"
                    String databaseVersionDescription = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
                    String databaseRequirement = "MySQL Server version 5.7+ or Amazon Aurora MySQL 5.6+ or MariaDB 10.2+ or PostgreSQL 10+ is required";
                    if ("MySQL".equals(metaData.getDatabaseProductName())) {
                        @Nullable
                        String amazonAuroraVersion;
                        try (Statement stmt = cnn.createStatement()) {
                            try (ResultSet rst = stmt.executeQuery("select AURORA_VERSION()")) {
                                rst.next();
                                amazonAuroraVersion = rst.getString(1);
                                databaseVersionDescription += " / Aurora " + rst.getString(1);
                            } catch (SQLException e) {
                                if (e.getErrorCode() == 1305) { // com.mysql.cj.exceptions.MysqlErrorNumbers.ER_SP_DOES_NOT_EXIST
                                    amazonAuroraVersion = null;
                                } else {
                                    LOGGER.log(Level.WARNING,"Exception checking Amazon Aurora version", e);
                                    amazonAuroraVersion = null;
                                }
                            }
                        }
                        @Nullable
                        String mariaDbVersion = PipelineMavenPluginMySqlDao.extractMariaDbVersion(metaData.getDatabaseProductVersion());

                        switch (metaData.getDatabaseMajorVersion()) {
                            case 8:
                                // OK
                                break;
                            case 5:
                                switch (metaData.getDatabaseMinorVersion()) {
                                    case 7:
                                        // ok
                                        break;
                                    case 6:
                                        if (amazonAuroraVersion == null) {
                                            // see JENKINS-54784
                                            return FormValidation.warning("Non validated MySQL version " + metaData.getDatabaseProductVersion() + ". " + databaseRequirement);
                                        } else {
                                            // we have successfully tested on Amazon Aurora MySQL 5.6.10a
                                            break;
                                        }
                                    case 5:
                                        if (mariaDbVersion == null) {
                                            return FormValidation.warning("Non validated MySQL version " + metaData.getDatabaseProductVersion() + ". " + databaseRequirement);
                                        } else {
                                            // JENKINS-55378 have successfully tested with "5.5.5-10.2.20-MariaDB"
                                            return FormValidation.ok("MariaDB version " + mariaDbVersion + " detected. Please ensure that your MariaDB version is at least version 10.2+");
                                        }
                                    default:
                                        return FormValidation.error("Non supported MySQL version " + metaData.getDatabaseProductVersion() + ". " + databaseRequirement);
                                }
                                break;
                            default:
                                return FormValidation.error("Non supported MySQL version " + metaData.getDatabaseProductVersion() + ". " + databaseRequirement);
                        }
                    } else if ("PostgreSQL".equals(metaData.getDatabaseProductName())) {
                        @Nullable
                        String amazonAuroraVersion; // https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/AuroraPostgreSQL.Updates.html
                        try (Statement stmt = cnn.createStatement()) {
                            try (ResultSet rst = stmt.executeQuery("select AURORA_VERSION()")) {
                                rst.next();
                                amazonAuroraVersion = rst.getString(1);
                                databaseVersionDescription += " / Aurora " + rst.getString(1);
                            } catch (SQLException e) {
                                if ("42883".equals(e.getSQLState())) { // org.postgresql.util.PSQLState.UNDEFINED_FUNCTION.getState()
                                    amazonAuroraVersion = null;
                                } else {
                                    LOGGER.log(Level.WARNING,"Exception checking Amazon Aurora version", e);
                                    amazonAuroraVersion = null;
                                }
                            }
                        }
                        switch (metaData.getDatabaseMajorVersion()) {
                            case 11:
                            case 10:
                                // OK
                                break;
                            default:
                                return FormValidation.warning("Non tested PostgreSQL version " + metaData.getDatabaseProductVersion() + ". " + databaseRequirement);
                        }
                    } else {
                        return FormValidation.warning("Non production grade database. For production workloads, " + databaseRequirement);
                    }
                    try (Statement stmt = cnn.createStatement()) {
                        try (ResultSet rst = stmt.executeQuery("select 1")) {
                            rst.next();
                            // TODO more tests
                        }
                    }
                    return FormValidation.ok(databaseVersionDescription + " is a supported database");
                } catch (SQLException e ){
                    return FormValidation.error(e, "Failure to connect to the database " + jdbcUrl);
                }
            }
        } catch (RuntimeException e) {
            return FormValidation.error(e, "Failed to test JDBC connection '" + jdbcUrl + "'");
        } catch (ClassNotFoundException e) {
            return FormValidation.error(e, "Failed to load JDBC driver '" + driverClass + "' for JDBC connection '" + jdbcUrl + "'");
        }

    }
}
