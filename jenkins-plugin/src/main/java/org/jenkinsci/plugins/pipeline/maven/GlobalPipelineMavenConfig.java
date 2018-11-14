package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
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
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDatabasePluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.jenkinsci.plugins.pipeline.maven.util.RuntimeSqlException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        if (dao != null) {
            return dao;
        }
        try {
            dao = Optional.ofNullable(jdbcUrl)
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .map(this::getConfiguredDao)
                    .orElseGet(this::getDefaultDao);

            return dao;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Exception creating database dao, skip", exception);
            return new PipelineMavenPluginNullDao();
        }
    }

    private PipelineMavenPluginDao getDefaultDao() {
        String defaultJdbcUrl = getDefaultJdbcUrl();
        UsernamePasswordCredentials defaultCredentials = getDefaultCredentials();
        return createDatabaseDao(defaultJdbcUrl, defaultCredentials);
    }

    private PipelineMavenPluginDao getConfiguredDao(String jdbcUrl) {
        UsernamePasswordCredentials credentials = getCredentials();
        return createDatabaseDao(jdbcUrl, credentials);
    }

    /**
     * if no jdbc url is defined, it means we need to use the default credentials for the embedded h2 db
     *
     * @return
     */
    private UsernamePasswordCredentials getDefaultCredentials() {
        return new UsernamePasswordCredentialsImpl(null, null, null, "sa", "sa");
    }

    private String getDefaultJdbcUrl() {
        Jenkins jenkins = Jenkins.getInstance();
        File jenkinsRootDir = jenkins.getRootDir();
        Path jenkinsRootDirPath = jenkinsRootDir.toPath();
        Path databaseRootDirPath = jenkinsRootDirPath.resolve("jenkins-jobs");
        try {
            if (!Files.exists(databaseRootDirPath)) {
                Files.createDirectories(databaseRootDirPath);
            }

            Path databasePath = databaseRootDirPath.resolve("jenkins-jobs");
            Path databaseAbsolutePath = databasePath.toAbsolutePath();
            return "jdbc:h2:file:" + databaseAbsolutePath + ";AUTO_SERVER=TRUE;MULTI_THREADED=1;QUERY_CACHE_SIZE=25;JMX=TRUE";
        } catch (IOException e) {
            throw new IllegalStateException("Failure to get maven pipeline database root dir " + databaseRootDirPath, e);
        }
    }

    private PipelineMavenPluginDatabasePluginDao createDatabaseDao(String actualJdbcUrl, UsernamePasswordCredentials credentials) {
        try {
            initDrivers();

            DataSource dataSource = getDataSource(actualJdbcUrl, credentials);
            String sqlDialect = getSqlDialect();
            return new PipelineMavenPluginDatabasePluginDao(dataSource, sqlDialect);
        } catch (SQLException | IllegalAccessException | InstantiationException | ClassNotFoundException exception) {
            throw new RuntimeSqlException("Error connecting to database", exception);
        }
    }

    private String getSqlDialect() {
        if (jdbcUrl.startsWith("jdbc:mysql")) {
            return "mysql";
        }
        // ok, it sucks, but we don't support anything else right now
        return "h2";
    }

    private DataSource getDataSource(String actualJdbcUrl, UsernamePasswordCredentials credentials) {
        String username = credentials.getUsername();
        Secret password = credentials.getPassword();
        String passwordPlainText = password.getPlainText();

        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(actualJdbcUrl, username, passwordPlainText);

        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory);
        poolableConnectionFactory.setPool(connectionPool);
        return new PoolingDataSource<>(connectionPool);
    }

    private UsernamePasswordCredentials getCredentials() {
        if (jdbcCredentialsId == null || jdbcCredentialsId.isEmpty()) {
            throw new IllegalStateException("No credentials defined for JDBC URL '" + jdbcUrl + "'");
        }
        UsernamePasswordCredentials credentialsNullable = (UsernamePasswordCredentials) CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(),
                        ACL.SYSTEM, Collections.EMPTY_LIST),
                CredentialsMatchers.withId(jdbcCredentialsId));
        return Optional.ofNullable(credentialsNullable)
                .orElseThrow(() -> new IllegalStateException("Credentials '" + jdbcCredentialsId + "' defined for JDBC URL '" + jdbcUrl + "' NOT found"));
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
        if (this.triggerDownstreamUponResultSuccess) {
            result.add(Result.SUCCESS);
        }
        if (this.triggerDownstreamUponResultUnstable) {
            result.add(Result.UNSTABLE);
        }
        if (this.triggerDownstreamUponResultAborted) {
            result.add(Result.ABORTED);
        }
        if (this.triggerDownstreamUponResultNotBuilt) {
            result.add(Result.NOT_BUILT);
        }
        if (this.triggerDownstreamUponResultFailure) {
            result.add(Result.FAILURE);
        }

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

    private void initDrivers() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
    }
}
