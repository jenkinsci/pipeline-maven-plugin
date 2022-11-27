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

import com.google.common.collect.ImmutableSet;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.SettingsProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig.GlobalMavenSettingsConfigProvider;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig.MavenSettingsConfigProvider;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configures maven environment to use within a pipeline job by calling <code>sh mvn</code> or <code>bat mvn</code>.
 * The selected maven installation will be configured and prepended to the path.
 *
 */
public class WithMavenStep extends Step {


    private String tempBinDir = "";
    private String mavenSettingsConfig;
    private String mavenSettingsFilePath = "";
    private String globalMavenSettingsConfig;
    private String globalMavenSettingsFilePath = "";
    private String maven;
    private String mavenOpts = "";
    private String jdk;
    private String mavenLocalRepo = "";
    private List<MavenPublisher> options = new ArrayList<>();
    private MavenPublisherStrategy publisherStrategy = MavenPublisherStrategy.IMPLICIT;
    private boolean traceability = true;

    @DataBoundConstructor
    public WithMavenStep() {
    }

    public String getTempBinDir() {
        return tempBinDir;
    }

    @DataBoundSetter
    public void setTempBinDir(String tempBinDir) {
        this.tempBinDir = tempBinDir;
    }

    public String getMavenSettingsConfig() {
        return mavenSettingsConfig;
    }

    @DataBoundSetter
    public void setMavenSettingsConfig(String mavenSettingsConfig) {
        this.mavenSettingsConfig = mavenSettingsConfig;
    }

    public String getMavenSettingsFilePath() {
        return mavenSettingsFilePath;
    }

    @DataBoundSetter
    public void setMavenSettingsFilePath(String mavenSettingsFilePath) {
        this.mavenSettingsFilePath = mavenSettingsFilePath;
    }

    public String getGlobalMavenSettingsConfig() {
        return globalMavenSettingsConfig;
    }

    @DataBoundSetter
    public void setGlobalMavenSettingsConfig(String globalMavenSettingsConfig) {
        this.globalMavenSettingsConfig = globalMavenSettingsConfig;
    }

    public String getGlobalMavenSettingsFilePath() {
        return globalMavenSettingsFilePath;
    }

    @DataBoundSetter
    public void setGlobalMavenSettingsFilePath(String globalMavenSettingsFilePath) {
        this.globalMavenSettingsFilePath = globalMavenSettingsFilePath;
    }

    public String getMaven() {
        return maven;
    }

    @DataBoundSetter
    public void setMaven(String maven) {
        this.maven = maven;
    }

    public String getMavenOpts() {
        return mavenOpts;
    }

    @DataBoundSetter
    public void setMavenOpts(String mavenOpts) {
        this.mavenOpts = mavenOpts;
    }

    public String getJdk() {
        return jdk;
    }

    @DataBoundSetter
    public void setJdk(String jdk) {
        this.jdk = jdk;
    }

    public String getMavenLocalRepo() {
        return mavenLocalRepo;
    }

    @DataBoundSetter
    public void setMavenLocalRepo(String mavenLocalRepo) {
        this.mavenLocalRepo = mavenLocalRepo;
    }

    public MavenPublisherStrategy getPublisherStrategy() {
        return publisherStrategy;
    }

    @DataBoundSetter
    public void setPublisherStrategy(MavenPublisherStrategy publisherStrategy) {
        this.publisherStrategy = publisherStrategy;
    }

    public boolean isTraceability() {
        return traceability;
    }

    @DataBoundSetter
    public void setTraceability(final boolean traceability) {
        this.traceability = traceability;
    }

    public List<MavenPublisher> getOptions() {
        return options;
    }

    @Override
    public WithMavenStep.DescriptorImpl getDescriptor() {
        return (WithMavenStep.DescriptorImpl) super.getDescriptor();
    }

    /**
     * Return all the registered Maven publishers
     */
    public DescriptorExtensionList<MavenPublisher, MavenPublisher.DescriptorImpl> getOptionsDescriptors() {
        return getDescriptor().getOptionsDescriptors();
    }

    @DataBoundSetter
    public void setOptions(List<MavenPublisher> options) {
        this.options = options;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new WithMavenStepExecution2(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        public static final String FUNCTION_NAME = "withMaven";

        @Override
        public String getFunctionName() {
            return FUNCTION_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Provide Maven environment";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class, Launcher.class, EnvVars.class, Run.class);
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public SettingsProvider getDefaultSettingsProvider() {
            return GlobalMavenConfig.get().getSettingsProvider();
        }

        private Maven.DescriptorImpl getMavenDescriptor() {
            return Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class);
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillMavenItems(@AncestorInPath Item item) {
            ListBoxModel r = new ListBoxModel();
            if (item == null) {
                return r; // it's empty
            }
            item.checkPermission(Item.CONFIGURE);
            r.add("--- Use system default Maven ---");
            for (MavenInstallation installation : getMavenDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }

        private JDK.DescriptorImpl getJDKDescriptor() {
            return Jenkins.get().getDescriptorByType(JDK.DescriptorImpl.class);
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillJdkItems(@AncestorInPath Item item) {
            ListBoxModel r = new ListBoxModel();
            if (item == null) {
                return r; // it's empty
            }
            item.checkPermission(Item.CONFIGURE);
            r.add("--- Use system default JDK ---");
            for (JDK installation : getJDKDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }
        
        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillMavenSettingsConfigItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            ListBoxModel r = new ListBoxModel();
            if (item == null) {
                return r; // it's empty
            }
            item.checkPermission(Item.CONFIGURE);
            r.add("--- Use system default settings or file path ---");
            for (Config config : ConfigFiles.getConfigsInContext(context, MavenSettingsConfigProvider.class)) {
                r.add(config.name, config.id);
            }
            return r;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillGlobalMavenSettingsConfigItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            ListBoxModel r = new ListBoxModel();
            if (item == null) {
                return r; // it's empty
            }
            item.checkPermission(Item.CONFIGURE);
            r.add("--- Use system default settings or file path ---");
            for (Config config : ConfigFiles.getConfigsInContext(context, GlobalMavenSettingsConfigProvider.class)) {
                r.add(config.name, config.id);
            }
            return r;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillPublisherStrategyItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            ListBoxModel r = new ListBoxModel();
            if (item == null) {
                return r; // it's empty
            }
            item.checkPermission(Item.CONFIGURE);
            for(MavenPublisherStrategy publisherStrategy: MavenPublisherStrategy.values()) {
                r.add(publisherStrategy.getDescription(), publisherStrategy.name());
            }
            return r;
        }

        /**
         * Return all the registered Maven publishers
         */
        public DescriptorExtensionList<MavenPublisher, MavenPublisher.DescriptorImpl> getOptionsDescriptors() {
            return Jenkins.get().getDescriptorList(MavenPublisher.class);
        }

    }
}
