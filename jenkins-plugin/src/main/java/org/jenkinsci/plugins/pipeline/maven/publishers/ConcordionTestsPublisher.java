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

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ConcordionTestsPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(ConcordionTestsPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @CheckForNull
    private String pattern = "**/target/*concordion*/**";

    @DataBoundConstructor
    public ConcordionTestsPublisher() {

    }

    @Override
    public void process(@Nonnull final StepContext context, @Nonnull final Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }

        try {
            Class.forName("htmlpublisher.HtmlPublisher");
        } catch (final ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/HTML+Publisher+Plugin",
                    "HTML Publisher Plugin");
            listener.getLogger().print(" not found, do not archive concordion reports.");
            return;
        }

        executeReporter(context, listener);
    }

    private void executeReporter(final StepContext context, final TaskListener listener)
            throws IOException, InterruptedException {
        final FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        final Run run = context.get(Run.class);
        final Launcher launcher = context.get(Launcher.class);

        if (pattern == null || pattern.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] No pattern given, aborting.");
            }
            return;
        }

        final FilePath[] paths = workspace.list(pattern);
        listener.getLogger().println(
                "[withMaven] Pattern \"" + pattern + "\" matched " + paths.length + " file(s).");

        final List<String> files = new ArrayList<String>();
        for (final FilePath path : paths) {
            files.add(XmlUtils.getPathInWorkspace(path.getRemote(), workspace));
        }
        final HtmlPublisherTarget target = new HtmlPublisherTarget("Concordion reports", ".",
                XmlUtils.join(files, ","), true, true, true);

        try {
            listener.getLogger().println(
                    "[withMaven] Publishing HTML named \"Concordion reports\" with the following files: "
                            + target.getReportFiles());
            HtmlPublisher.publishReports(run, workspace, launcher, listener, Arrays.asList(target),
                    HtmlPublisher.class);
        } catch (final Exception e) {
            listener.error("[withMaven] Silently ignore exception archiving Concordion reports: " + e);
            LOGGER.log(Level.WARNING, "Exception processing Concordion reports archiving", e);
        }

    }

    @CheckForNull
    public String getPattern() {
        return pattern;
    }

    @DataBoundSetter
    public void setPattern(@Nullable final String pattern) {
        this.pattern = pattern;
    }

    @Symbol("concordionPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Concordion Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-concordion-results";
        }
    }
}
