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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.Terminator;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.jenkinsci.plugins.pipeline.maven.service.PipelineTriggerService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension(ordinal = 50)
@Symbol("pipelineMaven")
public class GlobalPipelineMavenConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(GlobalPipelineMavenConfig.class.getName());

    private transient volatile PipelineMavenPluginDao dao;

    private transient PipelineTriggerService pipelineTriggerService;

    private boolean globalTraceability = false;

    private boolean triggerDownstreamUponResultSuccess = true;
    private boolean triggerDownstreamUponResultUnstable;
    private boolean triggerDownstreamUponResultFailure;
    private boolean triggerDownstreamUponResultNotBuilt;
    private boolean triggerDownstreamUponResultAborted;

    private String jdbcUrl;
    private String jdbcCredentialsId;
    private String properties;

    private String daoClass;

    @DataBoundConstructor
    public GlobalPipelineMavenConfig() {
        load();
    }

    public List<PipelineMavenPluginDao> getPipelineMavenPluginDaos() {
        return ExtensionList.lookup(PipelineMavenPluginDao.class);
    }

    public String getDaoClass() {
        return daoClass;
    }

    @DataBoundSetter
    public void setDaoClass(String daoClass) {
        this.daoClass = daoClass;
    }

    private Optional<PipelineMavenPluginDao> findDaoFromExtension(String daoClass) {
        return ExtensionList.lookup(PipelineMavenPluginDao.class).stream()
                .filter(pipelineMavenPluginDao ->
                        pipelineMavenPluginDao.getClass().getName().equals(daoClass))
                .findFirst();
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

    public boolean isGlobalTraceability() {
        return globalTraceability;
    }

    @DataBoundSetter
    public void setGlobalTraceability(boolean globalTraceability) {
        this.globalTraceability = globalTraceability;
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
            closeDatasource();
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
            closeDatasource();
        }

        this.properties = properties;
    }

    @DataBoundSetter
    public synchronized void setJdbcCredentialsId(String jdbcCredentialsId) {
        if (!Objects.equals(jdbcCredentialsId, this.jdbcCredentialsId)) {
            closeDatasource();
        }

        this.jdbcCredentialsId = jdbcCredentialsId;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        if (!json.getString("daoClass").equals(daoClass)) {
            closeDatasource();
            this.dao = null;
        }
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.publisherOptions = req.bindJSONToList(MavenPublisher.class, json.get("publisherOptions"));
        save();
        return true;
    }

    public String getDaoPrettyString() {
        return dao != null ? dao.toPrettyString() : "Dao Not Ready yet";
    }

    @NonNull
    public synchronized PipelineMavenPluginDao getDao() {
        if (dao != null) {
            return dao;
        }
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            throw new IllegalStateException("Request to get DAO whilst Jenkins is shutting down or starting up");
        } else if (j.isTerminating()) {
            throw new IllegalStateException("Request to get DAO whilst Jenkins is terminating");
        }
        Optional<PipelineMavenPluginDao> optionalPipelineMavenPluginDao = findDaoFromExtension(getDaoClass());
        if (optionalPipelineMavenPluginDao.isPresent()) {
            PipelineMavenPluginDao.Builder.Config config = new PipelineMavenPluginDao.Builder.Config()
                    .credentialsId(jdbcCredentialsId)
                    .jdbcUrl(jdbcUrl)
                    .properties(properties);
            this.dao = optionalPipelineMavenPluginDao.get().getBuilder().build(config);
        } else {
            LOGGER.info("cannot configure any dao so use the default null values one");
            this.dao = new PipelineMavenPluginNullDao();
        }

        return dao;
    }

    @NonNull
    public synchronized PipelineTriggerService getPipelineTriggerService() {
        if (pipelineTriggerService == null) {
            pipelineTriggerService = new PipelineTriggerService(this);
        }
        return pipelineTriggerService;
    }

    @NonNull
    public Set<Result> getTriggerDownstreamBuildsResultsCriteria() {
        Set<Result> result = new HashSet<>(5);
        if (this.triggerDownstreamUponResultSuccess) result.add(Result.SUCCESS);
        if (this.triggerDownstreamUponResultUnstable) result.add(Result.UNSTABLE);
        if (this.triggerDownstreamUponResultAborted) result.add(Result.ABORTED);
        if (this.triggerDownstreamUponResultNotBuilt) result.add(Result.NOT_BUILT);
        if (this.triggerDownstreamUponResultFailure) result.add(Result.FAILURE);

        return result;
    }

    @Nullable
    public static GlobalPipelineMavenConfig get() {
        return GlobalConfiguration.all().get(GlobalPipelineMavenConfig.class);
    }

    public ListBoxModel doFillJdbcCredentialsIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // use deprecated "withMatching" because, even after 20 mins of research,
        // I didn't understand how to use the new "recommended" API
        return new StandardListBoxModel()
                .includeEmptyValue()
                .withMatching(
                        CredentialsMatchers.always(),
                        CredentialsProvider.lookupCredentials(
                                UsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.EMPTY_LIST));
    }

    @POST
    public FormValidation doValidateJdbcConnection(
            @QueryParameter String jdbcUrl,
            @QueryParameter String properties,
            @QueryParameter String jdbcCredentialsId,
            @QueryParameter String daoClass) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Optional<PipelineMavenPluginDao> optionalPipelineMavenPluginDao = findDaoFromExtension(daoClass);
        if (optionalPipelineMavenPluginDao.isEmpty()) {
            return FormValidation.ok("OK");
        }

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = optionalPipelineMavenPluginDao.get().getDefaultJdbcUrl();
        }

        try {
            PipelineMavenPluginDao.Builder.Config config = new PipelineMavenPluginDao.Builder.Config()
                    .credentialsId(jdbcCredentialsId)
                    .jdbcUrl(jdbcUrl)
                    .properties(properties);
            return optionalPipelineMavenPluginDao.get().getBuilder().validateConfiguration(config);
        } catch (Exception e) {
            return FormValidation.error(e, e.getMessage());
        }
    }

    @Terminator
    public synchronized void closeDatasource() {
        if (dao != null) {
            try {
                dao.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Exception closing the DAO", e);
            } finally {
                dao = null;
            }
        }
    }
}
