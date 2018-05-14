package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
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
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginMonitoringDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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

    private boolean triggerDownstreamUponResultSuccess = true;
    private boolean triggerDownstreamUponResultUnstable;
    private boolean triggerDownstreamUponResultFailure;
    private boolean triggerDownstreamUponResultNotBuilt;
    private boolean triggerDownstreamUponResultAborted;

    private String jdbcUrl;
    private String jdbcCredentialsId;

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
                if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
                    File jenkinsRootDir = Jenkins.getInstance().getRootDir();
                    File databaseRootDir = new File(jenkinsRootDir, "jenkins-jobs");
                    if (!databaseRootDir.exists()) {
                        boolean created = databaseRootDir.mkdirs();
                        if (!created) {
                            throw new IllegalStateException("Failure to create database root dir " + databaseRootDir);
                        }
                    }
                    dao = new PipelineMavenPluginMonitoringDao(new PipelineMavenPluginH2Dao(databaseRootDir));
                } else if (jdbcUrl.startsWith("jdbc:h2:")) {
                    UsernamePasswordCredentials credentials;
                    if (jdbcCredentialsId == null || jdbcCredentialsId.isEmpty()) {
                        throw new IllegalStateException("No credentials defined for JDBC URL '" + jdbcUrl + "'");
                    } else {
                        credentials = (UsernamePasswordCredentials) CredentialsMatchers.firstOrNull(
                                CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(),
                                        ACL.SYSTEM, Collections.EMPTY_LIST),
                                CredentialsMatchers.withId(this.jdbcCredentialsId));
                        if (credentials == null) {
                            throw new IllegalStateException("Credentials '" + jdbcCredentialsId + "' defined for JDBC URL '" + jdbcUrl + "' NOT found");
                        }
                    }

                    JdbcConnectionPool jdbcConnectionPool = JdbcConnectionPool.create(this.jdbcUrl, credentials.getUsername(), Secret.toString(credentials.getPassword()));
                    try {
                        dao = new PipelineMavenPluginMonitoringDao(new PipelineMavenPluginH2Dao(jdbcConnectionPool));
                    } catch (Exception e) {
                        throw new SQLException(
                                "Exception connecting to '" + this.jdbcUrl + "' with credentials '" + this.jdbcCredentialsId + "' - " +
                                        credentials.getUsername() + "/***", e);
                    }
                } else {
                    LOGGER.warning("Unsupported jdbc URL '" + jdbcUrl + "'. JDBC URL must start with 'jdbc:h2:'");
                    dao = new PipelineMavenPluginNullDao();
                }
            } catch (RuntimeException | SQLException e) {
                LOGGER.log(Level.WARNING, "Exception creating database dao, skip", e);
                dao = new PipelineMavenPluginNullDao();
            }
        }
        return dao;
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
