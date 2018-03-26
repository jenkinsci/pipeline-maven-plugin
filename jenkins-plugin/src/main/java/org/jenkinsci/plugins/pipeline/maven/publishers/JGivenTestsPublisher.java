/*
 * The MIT License Copyright (c) 2016, CloudBees, Inc. Permission is hereby
 * granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of
 * the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
 * OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesLister.listDependencies;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.jgiven.JgivenReportGenerator;
import org.jenkinsci.plugins.jgiven.JgivenReportGenerator.ReportConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JGivenTestsPublisher extends MavenPublisher {

    public static final String REPORTS_DIR = "jgiven-reports";

    private static final Logger LOGGER = Logger.getLogger(JGivenTestsPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public JGivenTestsPublisher() {

    }

    @Override
    public void process(@Nonnull final StepContext context, @Nonnull final Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }

        final FilePath workspace = context.get(FilePath.class);
        final Run run = context.get(Run.class);
        final Launcher launcher = context.get(Launcher.class);

        boolean foundJGivenDependency = false;
        List<MavenDependency> dependencies = listDependencies(mavenSpyLogsElt, LOGGER);
        for (MavenDependency dependency : dependencies) {
            if (dependency.artifactId.contains("jgiven")) {
                foundJGivenDependency = true;
                break;
            }
        }
        if (!foundJGivenDependency) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] jgivenPublisher - JGiven not found within your project dependencies, aborting.");
            }
            return;
        }

        try {
            Class.forName("org.jenkinsci.plugins.jgiven.JgivenReportGenerator");
        } catch (final ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] jgivenPublisher - Jenkins ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/JGiven+Plugin", "JGiven Plugin");
            listener.getLogger().println(" not found, do not archive jgiven reports.");
            return;
        }

        final String pattern = "**/" + REPORTS_DIR + "/*";
        final FilePath[] paths = workspace.list(pattern);
        if (paths == null || paths.length == 0) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] jgivenPublisher - Pattern \"" + pattern
                        + "\" does not match any file on workspace, aborting.");
            }
            return;
        }

        final JgivenReportGenerator generator = new JgivenReportGenerator(new ArrayList<ReportConfig>());

        try {
            listener.getLogger().println("[withMaven] jgivenPublisher - Running JGiven report generator");
            generator.perform(run, workspace, launcher, listener);
        } catch (final Exception e) {
            listener.error(
                    "[withMaven] jgivenPublisher - Silently ignore exception archiving JGiven reports: " + e);
            LOGGER.log(Level.WARNING, "Exception processing JGiven reports archiving", e);
        }
    }

    @Symbol("jgivenPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "JGiven Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-jgiven-results";
        }
    }
}
