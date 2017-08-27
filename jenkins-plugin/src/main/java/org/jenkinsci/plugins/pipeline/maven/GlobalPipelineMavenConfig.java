package org.jenkinsci.plugins.pipeline.maven;

import hudson.Extension;
import hudson.model.Result;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginNullDao;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension(ordinal = 50)
@Symbol("pipelineMaven")
public class GlobalPipelineMavenConfig extends GlobalConfiguration {

    private final static Logger LOGGER = Logger.getLogger(GlobalPipelineMavenConfig.class.getName());

    private static PipelineMavenPluginDao DAO;

    private boolean triggerDownstreamUponResultSuccess = true;
    private boolean triggerDownstreamUponResultUnstable;
    private boolean triggerDownstreamUponResultFailure;
    private boolean triggerDownstreamUponResultNotBuilt;
    private boolean triggerDownstreamUponResultAborted;

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

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.publisherOptions = req.bindJSONToList(MavenPublisher.class, json.get("publisherOptions"));
        save();
        return true;
    }

    /**
     * TODO replace this static method by proper dependency injection
     * This requires to understand better the lifecyle of components managed by Jenkins.
     */
    public static synchronized PipelineMavenPluginDao getDao() {
        if (DAO == null) {
            try {
                File jenkinsRootDir = Jenkins.getInstance().getRootDir();
                File databaseRootDir = new File(jenkinsRootDir, "jenkins-jobs");
                if (!databaseRootDir.exists()){
                    boolean created = databaseRootDir.mkdirs();
                    if (!created) {
                        throw new IllegalStateException("Failure to create database root dir " + databaseRootDir);
                    }
                }
                DAO = new PipelineMavenPluginH2Dao(databaseRootDir);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception creating database dao, skip", e);
                DAO = new PipelineMavenPluginNullDao();
            }
        }
        return DAO;
    }
    @Nonnull
    public Set<Result> getTriggerDownstreamBuildsResultsCriteria(){
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

    /**
     * TODO replace this static method by org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig#getTriggerDownstreamBuildsResultsCriteria()
     * This requires to understand better the lifecyle of components managed by Jenkins.
     */
    @Nonnull
    public static Set<Result> getTriggerDownstreamBuildsCriteria(){
        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();
        if (globalPipelineMavenConfig == null) {
            return Collections.singleton(Result.SUCCESS);
        } else {
            return globalPipelineMavenConfig.getTriggerDownstreamBuildsResultsCriteria();
        }
    }

    @Nullable
    public static GlobalPipelineMavenConfig get() {
        return GlobalConfiguration.all().get(GlobalPipelineMavenConfig.class);
    }
}
