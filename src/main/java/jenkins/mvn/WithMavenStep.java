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

package jenkins.mvn;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig.MavenSettingsConfigProvider;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.JDK;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

/**
 * Configures maven environment to use within a pipeline job by calling <tt>sh mvn</tt> or <tt>bat mvn</tt>.
 * The selected maven installation will be configured and prepended to the path.
 *
 */
public class WithMavenStep extends AbstractStepImpl {


    private String mavenSettingsConfig;
    private String mavenSettingsFilePath;
    private String mavenInstallation;
    private String mavenOpts;
    private String jdk;
    private String mavenLocalRepo; 

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

    public String getMavenInstallation() {
        return mavenInstallation;
    }

    @DataBoundSetter
    public void setMavenInstallation(String mavenInstallation) {
        this.mavenInstallation = mavenInstallation;
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

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(WithMavenStepExecution.class);
        }

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

        public SettingsProvider getDefaultSettingsProvider() {
            return GlobalMavenConfig.get().getSettingsProvider();
        }
        
        private Maven.DescriptorImpl getMavenDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class);
        }

        public ListBoxModel doFillMavenInstallationItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default Maven ---",null);
            for (MavenInstallation installation : getMavenDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }

        private JDK.DescriptorImpl getJDKDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(JDK.DescriptorImpl.class);
        }

        public ListBoxModel doFillJdkItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default JDK ---",null);
            for (JDK installation : getJDKDescriptor().getInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }
        
        public ListBoxModel doFillMavenSettingsConfigItems() {
            ExtensionList<MavenSettingsConfigProvider> providers = Jenkins.getInstance().getExtensionList(MavenSettingsConfigProvider.class);
            ListBoxModel r = new ListBoxModel();
            r.add("--- Use system default settings or file path ---",null);
            for (ConfigProvider provider : providers) {
                for(Config config:provider.getAllConfigs()){
                    r.add(config.name, config.id);
                }
            }
            return r;
        }
    }
}
