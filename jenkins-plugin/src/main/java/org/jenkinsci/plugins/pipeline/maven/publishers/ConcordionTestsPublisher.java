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
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

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
    private static final String GROUP_ID = "org.apache.maven.plugins";
    private static final String SUREFIRE_ID = "maven-surefire-plugin";
    private static final String FAILSAFE_ID = "maven-failsafe-plugin";
    private static final String SUREFIRE_GOAL = "test";
    private static final String FAILSAFE_GOAL = "integration-test";

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public ConcordionTestsPublisher() {

    }

    /*
<ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-08-04 22:09:34.205">
    <project baseDir="/path/to/spring-petclinic" file="/path/to/spring-petclinic/pom.xml" groupId="org.springframework.samples" name="petclinic" artifactId="spring-petclinic" version="1.5.1">
      <build directory="/path/to/spring-petclinic/target"/>
    </project>
    <plugin executionId="default" goal="integration-test" groupId="org.apache.maven.plugins" artifactId="maven-failsafe-plugin" version="2.19.1">
      <reportsDirectory>${project.build.directory}/failsafe-reports</reportsDirectory>
      <systemPropertyVariables>
        <concordion.output.dir>target/concordion-reports</concordion.output.dir>
      </systemPropertyVariables>
    </plugin>
  </ExecutionEvent>
     */
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

        Set<String> concordionOutputDirPatterns = new HashSet<String>();
        concordionOutputDirPatterns.addAll(findConcordionOutputDirPatterns(XmlUtils.getExecutionEvents(mavenSpyLogsElt, GROUP_ID, SUREFIRE_ID, SUREFIRE_GOAL)));
        concordionOutputDirPatterns.addAll(findConcordionOutputDirPatterns(XmlUtils.getExecutionEvents(mavenSpyLogsElt, GROUP_ID, FAILSAFE_ID, FAILSAFE_GOAL)));

        if (concordionOutputDirPatterns.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] concordionPublisher - No concordion output dir pattern given, skip.");
            }
            return;
        }

        List<FilePath> paths = new ArrayList<FilePath>();
        for (String pattern : concordionOutputDirPatterns) {
            paths.addAll(Arrays.asList(workspace.list(pattern)));
        }
        if (paths.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println(
                        "[withMaven] concordionPublisher - Did not found any Concordion reports directory, skip.");
            }
            return;
        }

        listener.getLogger().println(
                "[withMaven] concordionPublisher - Found " + paths.size() + " file(s) in Concordion reports directory.");


        try {
            Class.forName("htmlpublisher.HtmlPublisher");
        } catch (final ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] concordionPublisher - Jenkins ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/HTML+Publisher+Plugin",
                    "HTML Publisher Plugin");
            listener.getLogger().println(" not found, do not archive concordion reports.");
            return;
        }

        final List<String> files = new ArrayList<String>();
        for (final FilePath path : paths) {
            files.add(XmlUtils.getPathInWorkspace(path.getRemote(), workspace));
        }

        final HtmlPublisherTarget target = new HtmlPublisherTarget("Concordion reports", ".",
                XmlUtils.join(files, ","), true, true, true);

        try {
            listener.getLogger().println(
                    "[withMaven] concordionPublisher - Publishing HTML reports named \"" + target.getReportName()  +
                            "\" with the following files: " + target.getReportFiles());
            HtmlPublisher.publishReports(run, workspace, launcher, listener, Arrays.asList(target),
                    HtmlPublisher.class);
        } catch (final Exception e) {
            listener.error("[withMaven] concordionPublisher - Silently ignore exception archiving Concordion reports: " + e);
            LOGGER.log(Level.WARNING, "Exception processing Concordion reports archiving", e);
        }
    }

    @Nonnull
    private Collection<String> findConcordionOutputDirPatterns(@Nonnull List<Element> elements) {
        List<String> result = new ArrayList<String>();
        for (Element element : elements) {
            Element envVars = XmlUtils.getUniqueChildElementOrNull(XmlUtils.getUniqueChildElement(element, "plugin"), "systemPropertyVariables");
            if (envVars != null) {
                Element concordionOutputDir = XmlUtils.getUniqueChildElementOrNull(envVars, "concordion.output.dir");
                if (concordionOutputDir != null) {
                    // TODO Cyrille Le Clerc 2017-08-06: couldn't we find the root relative path?
                    // isn't it getPathInWorkspace(${executionEvent.project.baseDir} + ${concordionOutputDir}) ?
                    result.add("**/" + concordionOutputDir.getTextContent().trim() + "/**");
                }
            }
        }
        return result;
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
