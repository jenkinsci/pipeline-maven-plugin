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
import hudson.EnvVars;
import hudson.model.ItemGroup;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig.GlobalMavenSettingsConfigProvider;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig.MavenSettingsConfigProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ListBoxModel;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.SettingsProvider;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Configures maven environment to use within a pipeline job by calling <tt>sh mvn</tt> or <tt>bat mvn</tt>.
 * The selected maven installation will be configured and prepended to the path.
 *
 */
public class WithMavenStep extends Step {


    private String mavenSettingsConfig;
    private String mavenSettingsFilePath = "";
    private String globalMavenSettingsConfig;
    private String globalMavenSettingsFilePath = "";
    private String maven;
    private String mavenOpts = "";
    private String jdk;
    private String mavenLocalRepo = "";

    @DataBoundConstructor
    public WithMavenStep() {
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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new WithMavenStepExecution(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "withMaven";
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
            return Jenkins.getActiveInstance().getDescriptorByType(Maven.DescriptorImpl.class);
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillMavenItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default Maven ---",null);
            for (MavenInstallation installation : getMavenDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }

        private JDK.DescriptorImpl getJDKDescriptor() {
            return Jenkins.getActiveInstance().getDescriptorByType(JDK.DescriptorImpl.class);
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillJdkItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default JDK ---",null);
            for (JDK installation : getJDKDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }
        
        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillMavenSettingsConfigItems(@AncestorInPath ItemGroup context) {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default settings or file path ---",null);
            for (Config config : ConfigFiles.getConfigsInContext(context, MavenSettingsConfigProvider.class)) {
                r.add(config.name, config.id);
            }
            return r;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillGlobalMavenSettingsConfigItems(@AncestorInPath ItemGroup context) {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default settings or file path ---",null);
            for (Config config : ConfigFiles.getConfigsInContext(context, GlobalMavenSettingsConfigProvider.class)) {
                r.add(config.name, config.id);
            }
            return r;
        }
    }
}
