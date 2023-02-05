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

package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.maveninvoker.MavenInvokerRecorder;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.FileUtils;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InvokerRunsPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(InvokerRunsPublisher.class.getName());
    protected static final String GROUP_ID = "org.apache.maven.plugins";
    protected static final String ARTIFACT_ID = "maven-invoker-plugin";
    protected static final String RUN_GOAL = "run";
    protected static final String INTEGRATION_TEST_GOAL = "integration-test";

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public InvokerRunsPublisher() {

    }

    /*
<ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-06-25 20:47:25.741">
    <project baseDir="/home/jenkins/workspace/aProject" file="/home/jenkins/workspace/aProject/pom.xml" groupId="org.myorg" name="My Project" artifactId="my-maven-plugin" version="1.0.0-SNAPSHOT">
      <build sourceDirectory="/home/jenkins/workspace/aProject/src/main/java" directory="/home/jenkins/workspace/aProject/target"/>
    </project>
    <plugin executionId="integration-test" goal="run" groupId="org.apache.maven.plugins" artifactId="maven-invoker-plugin" version="2.0.0">
      <projectsDirectory>${invoker.projectsDirectory}</projectsDirectory>
      <cloneProjectsTo>/var/lib/jenkins/workspace/ncjira-maven-plugin-pipeline/target/it</cloneProjectsTo>
      <reportsDirectory>${invoker.reportsDirectory}</reportsDirectory>
    </plugin>
  </ExecutionEvent>
     */
    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }

        List<Element> events = new ArrayList<>();
        events.addAll(XmlUtils.getExecutionEventsByPlugin(mavenSpyLogsElt, GROUP_ID, ARTIFACT_ID, RUN_GOAL, "MojoSucceeded", "MojoFailed"));
        events.addAll(XmlUtils.getExecutionEventsByPlugin(mavenSpyLogsElt, GROUP_ID, ARTIFACT_ID, INTEGRATION_TEST_GOAL, "MojoSucceeded", "MojoFailed"));

        if (events.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] invokerPublisher - " +
                        "No " + GROUP_ID + ":" + ARTIFACT_ID + ":" + RUN_GOAL +
                        " or " + GROUP_ID + ":" + ARTIFACT_ID + ":" + INTEGRATION_TEST_GOAL + " execution found");
            }
            return;
        }

        List<Element> junitLikeEvents = new ArrayList<>();
        List<Element> oldEvents = new ArrayList<>();
        events.forEach(e -> {
            Element pluginElt = XmlUtils.getUniqueChildElement(e, "plugin");
            Element writeJunitReportElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "writeJunitReport");
            if (writeJunitReportElt != null && writeJunitReportElt.getTextContent() != null && "true".equalsIgnoreCase(writeJunitReportElt.getTextContent().trim())) {
                junitLikeEvents.add(e);
            } else {
                oldEvents.add(e);
            }
        });

        manageEvents(context, listener, junitLikeEvents);
        manageOldEvents(context, listener, oldEvents);
    }

    private void manageEvents(StepContext context, TaskListener listener, List<Element> testEvents) throws IOException, InterruptedException {
        if (testEvents.isEmpty()) {
            return;
        }

        try {
            Class.forName("hudson.tasks.junit.JUnitResultArchiver");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("http://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin", "JUnit Plugin");
            listener.getLogger().print(" not found, don't display " + GROUP_ID + ":" + ARTIFACT_ID + ":" + INTEGRATION_TEST_GOAL);
            listener.getLogger().println(" nor " + GROUP_ID + ":" + ARTIFACT_ID + ":" + RUN_GOAL + " results in pipeline screen.");
            return;
        }

        FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        List<String> testResultsList = new ArrayList<>();
        for (Element testEvent : testEvents) {
            Element pluginElt = XmlUtils.getUniqueChildElement(testEvent, "plugin");
            Element reportsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "reportsDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(testEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (reportsDirectoryElt == null) {
                listener.getLogger().println("[withMaven] invokerPublisher - No <reportsDirectory> element found for <plugin> in " + XmlUtils.toString(testEvent));
                continue;
            }
            String reportsDirectory = expandAndRelativize(reportsDirectoryElt, "reportsDirectory", testEvent, projectElt, workspace, listener);

            String testResults = reportsDirectory + fileSeparatorOnAgent + "TEST-*.xml";
            listener.getLogger().println("[withMaven] invokerPublisher - Archive test results for Maven artifact " + mavenArtifact.getId() + " generated by " +
                    pluginInvocation.getId() + ": " + testResults);
            if (testResultsList.contains(testResults)) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] invokerPublisher - Ignore already added testResults " + testResults);
                }
            } else {
                testResultsList.add(testResults);
            }
        }
        String testResults = String.join(",", testResultsList);

        JUnitResultArchiver archiver = JUnitUtils.buildArchiver(testResults, true, null);
        JUnitUtils.archiveResults(context, archiver, testResults, "invokerPublisher");
    }

    private void manageOldEvents(StepContext context, TaskListener listener, List<Element> testEvents) throws IOException, InterruptedException {
        if (testEvents.isEmpty()) {
            return;
        }

        listener.getLogger().print("[withMaven] invokerPublisher - Found invoker tests without the ");
        listener.hyperlink("https://maven.apache.org/plugins/maven-invoker-plugin/run-mojo.html#writeJunitReport", "writeJunitReport parameter");
        listener.getLogger().print(" set. According to the ");
        listener.hyperlink("https://github.com/jenkinsci/maven-invoker-plugin", "Maven Invoker Plugin");
        listener.getLogger().println(" plugin, this is now the way to go.");

        try {
            Class.forName("org.jenkinsci.plugins.maveninvoker.MavenInvokerRecorder");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] invokerPublisher - Jenkins ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/Maven+Invoker+Plugin", "Maven Invoker Plugin");
            listener.getLogger().println(" not found, don't display " + GROUP_ID + ":" + ARTIFACT_ID + ":" + RUN_GOAL + " results in pipeline screen.");
            return;
        }

        executeReporterForOldEvents(context, listener, testEvents);
    }

    private void executeReporterForOldEvents(StepContext context, TaskListener listener, List<Element> testEvents) throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);
        FlowNode node = context.get(FlowNode.class);

        for (Element testEvent : testEvents) {
            Element projectElt = XmlUtils.getUniqueChildElement(testEvent, "project");
            Element pluginElt = XmlUtils.getUniqueChildElement(testEvent, "plugin");
            Element reportsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "reportsDirectory");
            Element cloneProjectsToElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "cloneProjectsTo");
            Element projectsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "projectsDirectory");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            String reportsDirectory = expandAndRelativize(reportsDirectoryElt, "reportsDirectory", testEvent, projectElt, workspace, listener);
            String projectsDirectory = expandAndRelativize(projectsDirectoryElt, "projectsDirectory", testEvent, projectElt, workspace, listener);
            String cloneProjectsTo = expandAndRelativize(cloneProjectsToElt, "cloneProjectsTo", testEvent, projectElt, workspace, listener);
            if (reportsDirectory == null || projectsDirectory == null ) {
                listener.getLogger().println("[withMaven] invokerPublisher - Missing <projectsDirectory> or <reportsDirectory> element for <plugin> in " + XmlUtils.toString(testEvent));
                continue;
            }

            String testResults = reportsDirectory + fileSeparatorOnAgent + "*.xml";
            listener.getLogger().println("[withMaven] invokerPublisher - Archive invoker results for Maven artifact " + mavenArtifact.getId() + " generated by " +
                pluginInvocation.getId() + ": " + testResults);
            MavenInvokerRecorder archiver = new MavenInvokerRecorder("**/" + reportsDirectory + "/BUILD*.xml", "**/" + (cloneProjectsTo != null ? cloneProjectsTo : projectsDirectory));

            try {
                archiver.perform(run, workspace, launcher, listener, node);
            } catch (Exception e) {
                listener.error("[withMaven] invokerPublisher - exception archiving Invoker runs for Maven artifact " + mavenArtifact.getId() + " generated by " + pluginInvocation.getId() + ": " + e + ". Failing the build.");
                LOGGER.log(Level.WARNING, "Exception processing " + XmlUtils.toString(testEvent), e);
                run.setResult(Result.FAILURE);
            }

        }
    }

    @Nullable
    protected String expandAndRelativize(@Nullable Element element, @Nullable String name, Element testEvent, Element projectElt, FilePath workspace, TaskListener listener) {
        if (element == null) {
            listener.getLogger().println("[withMaven] invokerPublisher - No <" + name + "> element found for <plugin> in " + XmlUtils.toString(testEvent));
            return null;
        }

        String result = element.getTextContent().trim();

        if (result.contains("${invoker.projectsDirectory}")) {
            result = result.replace("${invoker.projectsDirectory}", "${basedir}/src/it");
        } else if (result.contains("${invoker.reportsDirectory}")) {
            result = result.replace("${invoker.reportsDirectory}", "${project.build.directory}/invoker-reports");
        }

        if (result.contains("${project.build.directory}")) {
            String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
            if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                listener.getLogger().println("[withMaven] invokerPublisher - '${project.build.directory}' found for <project> in " + XmlUtils.toString(testEvent));
                return null;
            }

            result = result.replace("${project.build.directory}", projectBuildDirectory);

        } else if (result.contains("${basedir}")) {
            String baseDir = projectElt.getAttribute("baseDir");
            if (baseDir.isEmpty()) {
                listener.getLogger().println("[withMaven] invokerPublisher - '${basedir}' NOT found for <project> in " + XmlUtils.toString(testEvent));
                return null;
            }

            result = result.replace("${basedir}", baseDir);
        } else if (!FileUtils.isAbsolutePath(result)) {
            char separator = FileUtils.isWindows(result) ? '\\' : '/';
            String baseDir = projectElt.getAttribute("baseDir");
            if (baseDir.isEmpty()) {
                listener.getLogger().println("[withMaven] invokerPublisher - '${basedir}' NOT found for <project> in " + XmlUtils.toString(testEvent));
                return null;
            }
            result = baseDir + separator + result;
        }

        return XmlUtils.getPathInWorkspace(result, workspace);
    }

    /**
     * Don't use the symbol "junit", it would collide with hudson.tasks.junit.JUnitResultArchiver
     */
    @Symbol("invokerPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Invoker Publisher";
        }

        @Override
        public int ordinal() {
            return 10;
        }


        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-invoker-runs";
        }
    }
}
