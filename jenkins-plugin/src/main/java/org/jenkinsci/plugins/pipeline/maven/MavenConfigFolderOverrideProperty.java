package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.CopyOnWriteList;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides a way to override maven configuration at a folder level
 */
public class MavenConfigFolderOverrideProperty extends AbstractFolderProperty<AbstractFolder<?>> {

    /**
     * Provides access to the settings.xml to be used for a build.
     */
    @DataBoundSetter
    private SettingsProvider settings;

    /**
     * Provides access to the global settings.xml to be used for a build.
     */
    @DataBoundSetter
    private GlobalSettingsProvider globalSettings;

    /**
     * Defines if the instance level configuration should be overriden by this folder one
     */
    private boolean override;

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public MavenConfigFolderOverrideProperty() {
    }

    public boolean isOverride() {
        return override;
    }

    @DataBoundSetter
    public void setOverride(boolean override) {
        this.override = override;
    }

    public SettingsProvider getSettings() {
        return settings;
    }

    protected void setSettings(SettingsProvider settings) {
        this.settings = settings;
    }

    public GlobalSettingsProvider getGlobalSettings() {
        return globalSettings;
    }


    protected void setGlobalSettings(GlobalSettingsProvider globalSettings) {
        this.globalSettings = globalSettings;
    }

    /**
     * Descriptor class.
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Override Maven Settings";
        }
    }
}
