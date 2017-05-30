package org.jenkinsci.plugins.pipeline.maven;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension(ordinal = 50) @Symbol("pipelineMaven")
public class GlobalPipelineMavenConfig extends GlobalConfiguration {

    private final static Logger LOGGER = Logger.getLogger(GlobalPipelineMavenConfig.class.getName());

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

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.publisherOptions = req.bindJSONToList(MavenPublisher.class, json.get("publisherOptions"));
        save();
        return true;
    }

    @Nullable
    public static GlobalPipelineMavenConfig get() {
        return GlobalConfiguration.all().get(GlobalPipelineMavenConfig.class);
    }
}
