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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.FilePath;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestDataPublisher;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JunitTestsPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(JunitTestsPublisher.class.getName());
    private static final String APACHE_GROUP_ID = "org.apache.maven.plugins";
    private static final String TYCHO_GROUP_ID = "org.eclipse.tycho";
    private static final String KARMA_GROUP_ID = "com.kelveden";
    private static final String FRONTEND_GROUP_ID = "com.github.eirslett";
    private static final String SUREFIRE_ID = "maven-surefire-plugin";
    private static final String FAILSAFE_ID = "maven-failsafe-plugin";
    private static final String TYCHO_ID = "tycho-surefire-plugin";
    private static final String KARMA_ID = "maven-karma-plugin";
    private static final String FRONTEND_ID = "frontend-maven-plugin";
    private static final String SUREFIRE_GOAL = "test";
    private static final String FAILSAFE_GOAL = "integration-test";
    private static final String TYCHO_GOAL = "test";
    private static final String KARMA_GOAL = "start";
    private static final String FRONTEND_GOAL = "karma";

    private static final long serialVersionUID = 1L;

    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     *
     * @see JUnitResultArchiver#isKeepLongStdio()
     */
    private boolean keepLongStdio;

    /**
     * @see JUnitResultArchiver#getHealthScaleFactor()
     */
    @CheckForNull
    private Double healthScaleFactor;

    private boolean ignoreAttachments;

    @DataBoundConstructor
    public JunitTestsPublisher() {}

    /*
    <ExecutionEvent type="MojoStarted" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-03 10:15:12.554">
        <project baseDir="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy" file="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/pom.xml" groupId="org.jenkins-ci.plugins" name="Maven Spy for the Pipeline Maven Integration Plugin" artifactId="pipeline-maven-spy" version="2.0-SNAPSHOT">
          <build outputDirectory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/target/classes"/>
        </project>
        <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.19.1">
          <additionalClasspathElements>${maven.test.additionalClasspath}</additionalClasspathElements>
          <argLine>${argLine}</argLine>
          <basedir>${basedir}</basedir>
          <childDelegation>${childDelegation}</childDelegation>
          <classesDirectory>${project.build.outputDirectory}</classesDirectory>
          <classpathDependencyExcludes>${maven.test.dependency.excludes}</classpathDependencyExcludes>
          <debugForkedProcess>${maven.surefire.debug}</debugForkedProcess>
          <dependenciesToScan>${dependenciesToScan}</dependenciesToScan>
          <disableXmlReport>${disableXmlReport}</disableXmlReport>
          <enableAssertions>${enableAssertions}</enableAssertions>
          <excludedGroups>${excludedGroups}</excludedGroups>
          <excludesFile>${surefire.excludesFile}</excludesFile>
          <failIfNoSpecifiedTests>${surefire.failIfNoSpecifiedTests}</failIfNoSpecifiedTests>
          <failIfNoTests>${failIfNoTests}</failIfNoTests>
          <forkCount>1C</forkCount>
          <forkMode>${forkMode}</forkMode>
          <forkedProcessTimeoutInSeconds>${surefire.timeout}</forkedProcessTimeoutInSeconds>
          <groups>${groups}</groups>
          <includesFile>${surefire.includesFile}</includesFile>
          <junitArtifactName>${junitArtifactName}</junitArtifactName>
          <jvm>${jvm}</jvm>
          <localRepository>${localRepository}</localRepository>
          <objectFactory>${objectFactory}</objectFactory>
          <parallel>${parallel}</parallel>
          <parallelMavenExecution>${session.parallel}</parallelMavenExecution>
          <parallelOptimized>${parallelOptimized}</parallelOptimized>
          <parallelTestsTimeoutForcedInSeconds>${surefire.parallel.forcedTimeout}</parallelTestsTimeoutForcedInSeconds>
          <parallelTestsTimeoutInSeconds>${surefire.parallel.timeout}</parallelTestsTimeoutInSeconds>
          <perCoreThreadCount>${perCoreThreadCount}</perCoreThreadCount>
          <pluginArtifactMap>${plugin.artifactMap}</pluginArtifactMap>
          <pluginDescriptor>${plugin}</pluginDescriptor>
          <printSummary>${surefire.printSummary}</printSummary>
          <projectArtifactMap>${project.artifactMap}</projectArtifactMap>
          <redirectTestOutputToFile>${maven.test.redirectTestOutputToFile}</redirectTestOutputToFile>
          <remoteRepositories>${project.pluginArtifactRepositories}</remoteRepositories>
          <reportFormat>${surefire.reportFormat}</reportFormat>
          <reportNameSuffix>${surefire.reportNameSuffix}</reportNameSuffix>
          <reportsDirectory>${project.build.directory}/surefire-reports</reportsDirectory>
          <rerunFailingTestsCount>${surefire.rerunFailingTestsCount}</rerunFailingTestsCount>
          <reuseForks>true</reuseForks>
          <runOrder>${surefire.runOrder}</runOrder>
          <shutdown>${surefire.shutdown}</shutdown>
          <skip>${maven.test.skip}</skip>
          <skipAfterFailureCount>${surefire.skipAfterFailureCount}</skipAfterFailureCount>
          <skipExec>${maven.test.skip.exec}</skipExec>
          <skipTests>${skipTests}</skipTests>
          <suiteXmlFiles>${surefire.suiteXmlFiles}</suiteXmlFiles>
          <systemProperties/>
          <test>${test}</test>
          <testClassesDirectory>${project.build.testOutputDirectory}</testClassesDirectory>
          <testFailureIgnore>${maven.test.failure.ignore}</testFailureIgnore>
          <testNGArtifactName>${testNGArtifactName}</testNGArtifactName>
          <testSourceDirectory>${project.build.testSourceDirectory}</testSourceDirectory>
          <threadCount>${threadCount}</threadCount>
          <threadCountClasses>${threadCountClasses}</threadCountClasses>
          <threadCountMethods>${threadCountMethods}</threadCountMethods>
          <threadCountSuites>${threadCountSuites}</threadCountSuites>
          <trimStackTrace>${trimStackTrace}</trimStackTrace>
          <useFile>${surefire.useFile}</useFile>
          <useManifestOnlyJar>${surefire.useManifestOnlyJar}</useManifestOnlyJar>
          <useSystemClassLoader>${surefire.useSystemClassLoader}</useSystemClassLoader>
          <useUnlimitedThreads>${useUnlimitedThreads}</useUnlimitedThreads>
          <workingDirectory>${basedir}</workingDirectory>
          <project>${project}</project>
          <session>${session}</session>
        </plugin>
      </ExecutionEvent>
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-03 10:15:13.274">
        <project baseDir="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy" file="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/pom.xml" groupId="org.jenkins-ci.plugins" name="Maven Spy for the Pipeline Maven Integration Plugin" artifactId="pipeline-maven-spy" version="2.0-SNAPSHOT">
          <build directory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/target"/>
        </project>
        <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.19.1">
          <reportsDirectory>${project.build.directory}/surefire-reports</reportsDirectory>
        </plugin>
      </ExecutionEvent>
         */
    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }

        try {
            Class.forName("hudson.tasks.junit.JUnitResultArchiver");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("http://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin", "JUnit Plugin");
            listener.getLogger()
                    .print(" not found, don't display " + APACHE_GROUP_ID + ":" + SUREFIRE_ID + ":" + SUREFIRE_GOAL);
            listener.getLogger()
                    .println(" nor " + APACHE_GROUP_ID + ":" + FAILSAFE_ID + ":" + FAILSAFE_GOAL
                            + " results in pipeline screen.");
            return;
        }

        List<Element> sureFireTestEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, APACHE_GROUP_ID, SUREFIRE_ID, SUREFIRE_GOAL, "MojoSucceeded", "MojoFailed");
        List<Element> failSafeTestEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, APACHE_GROUP_ID, FAILSAFE_ID, FAILSAFE_GOAL, "MojoSucceeded", "MojoFailed");
        List<Element> tychoTestEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, TYCHO_GROUP_ID, TYCHO_ID, TYCHO_GOAL, "MojoSucceeded", "MojoFailed");
        List<Element> karmaTestEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, KARMA_GROUP_ID, KARMA_ID, KARMA_GOAL, "MojoSucceeded", "MojoFailed");
        List<Element> frontendTestEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, FRONTEND_GROUP_ID, FRONTEND_ID, FRONTEND_GOAL, "MojoSucceeded", "MojoFailed");

        executeReporter(
                context,
                listener,
                sureFireTestEvents,
                APACHE_GROUP_ID + ":" + SUREFIRE_ID + ":" + SUREFIRE_GOAL,
                "reportsDirectory");
        executeReporter(
                context,
                listener,
                failSafeTestEvents,
                APACHE_GROUP_ID + ":" + FAILSAFE_ID + ":" + FAILSAFE_GOAL,
                "reportsDirectory");
        executeReporter(
                context,
                listener,
                tychoTestEvents,
                APACHE_GROUP_ID + ":" + TYCHO_ID + ":" + TYCHO_GOAL,
                "reportsDirectory");
        executeReporter(
                context,
                listener,
                karmaTestEvents,
                KARMA_GROUP_ID + ":" + KARMA_ID + ":" + KARMA_GOAL,
                "reportsDirectory");
        executeReporter(
                context,
                listener,
                frontendTestEvents,
                FRONTEND_GROUP_ID + ":" + FRONTEND_ID + ":" + FRONTEND_GOAL,
                "environmentVariables",
                "REPORTS_DIRECTORY");
    }

    private void executeReporter(
            StepContext context,
            TaskListener listener,
            List<Element> testEvents,
            String goal,
            String... reportsDirElementNames)
            throws IOException, InterruptedException {
        if (testEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] junitPublisher - No " + goal + " execution found");
            }
            return;
        }

        FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        List<String> testResultsList = new ArrayList<>();

        for (Element testEvent : testEvents) {
            Element pluginElt = XmlUtils.getUniqueChildElement(testEvent, "plugin");
            Element reportsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, reportsDirElementNames);
            Element projectElt = XmlUtils.getUniqueChildElement(testEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);

            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (reportsDirectoryElt == null) {
                listener.getLogger()
                        .println("[withMaven] No <"
                                + Arrays.stream(reportsDirElementNames).collect(Collectors.joining("."))
                                + "> element found for <plugin> in " + XmlUtils.toString(testEvent));
                continue;
            }
            String reportsDirectory = XmlUtils.resolveMavenPlaceholders(reportsDirectoryElt, projectElt);
            if (reportsDirectory == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] could not resolve placeholder '${project.build.directory}' or '${basedir}' in "
                                        + XmlUtils.toString(testEvent));
                continue;
            }
            reportsDirectory = XmlUtils.getPathInWorkspace(reportsDirectory, workspace);

            String testResults = reportsDirectory + fileSeparatorOnAgent + "*.xml";
            listener.getLogger()
                    .println("[withMaven] junitPublisher - Archive test results for Maven artifact "
                            + mavenArtifact.getId() + " generated by " + pluginInvocation.getId() + ": " + testResults);
            if (testResultsList.contains(testResults)) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger()
                            .println("[withMaven] junitPublisher - Ignore already added testResults " + testResults);
                }
            } else {
                testResultsList.add(testResults);
            }
        }
        String testResults = String.join(",", testResultsList);

        JUnitResultArchiver archiver =
                JUnitUtils.buildArchiver(testResults, this.keepLongStdio, this.healthScaleFactor);

        List<TestDataPublisher> testDataPublishers = new ArrayList<>();

        if (Boolean.TRUE.equals(this.ignoreAttachments)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] junitPublisher - Ignore junit test attachments");
            }
        } else {
            String attachmentsPublisherClassName = "hudson.plugins.junitattachments.AttachmentPublisher";
            try {
                TestDataPublisher attachmentPublisher = (TestDataPublisher)
                        Class.forName(attachmentsPublisherClassName).newInstance();
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] junitPublisher - Publish junit test attachments...");
                }
                testDataPublishers.add(attachmentPublisher);
            } catch (ClassNotFoundException e) {
                listener.getLogger().print("[withMaven] junitPublisher - Jenkins ");
                listener.hyperlink("https://plugins.jenkins.io/junit-attachments", "JUnit Attachments Plugin");
                listener.getLogger().println(" not found, can't publish test attachments.");
            } catch (IllegalAccessException | InstantiationException e) {
                PrintWriter err = listener.error(
                        "[withMaven] junitPublisher - Failure to publish test attachments, exception instantiating '"
                                + attachmentsPublisherClassName + "'");
                e.printStackTrace(err);
            }
        }

        String flakyTestDataPublisherClassName =
                "com.google.jenkins.flakyTestHandler.plugin.JUnitFlakyTestDataPublisher";
        try {
            TestDataPublisher flakyTestPublisher = (TestDataPublisher)
                    Class.forName(flakyTestDataPublisherClassName).newInstance();
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] junitPublisher - Publish JUnit flaky tests reports...");
            }
            testDataPublishers.add(flakyTestPublisher);
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] junitPublisher - Jenkins ");
            listener.hyperlink("https://plugins.jenkins.io/flaky-test-handler", "JUnit Flaky Test Handler Plugin");
            listener.getLogger().println(" not found, can't publish JUnit flaky tests reports.");
        } catch (IllegalAccessException | InstantiationException e) {
            PrintWriter err = listener.error(
                    "[withMaven] junitPublisher - Failure to publish flaky test reports, exception instantiating '"
                            + flakyTestDataPublisherClassName + "'");
            e.printStackTrace(err);
        }

        if (!testDataPublishers.isEmpty()) {
            archiver.setTestDataPublishers(testDataPublishers);
        }

        JUnitUtils.archiveResults(context, archiver, testResults, "junitPublisher");
    }

    public boolean getIgnoreAttachments() {
        return ignoreAttachments;
    }

    @DataBoundSetter
    public void setIgnoreAttachments(boolean ignoreAttachments) {
        this.ignoreAttachments = ignoreAttachments;
    }

    public boolean isKeepLongStdio() {
        return keepLongStdio;
    }

    @DataBoundSetter
    public void setKeepLongStdio(boolean keepLongStdio) {
        this.keepLongStdio = keepLongStdio;
    }

    @CheckForNull
    public Double getHealthScaleFactor() {
        return healthScaleFactor;
    }

    @DataBoundSetter
    public void setHealthScaleFactor(@Nullable Double healthScaleFactor) {
        this.healthScaleFactor = healthScaleFactor;
    }

    @Override
    public String toString() {
        return "JunitTestsPublisher[" + "disabled="
                + isDisabled() + "," + "healthScaleFactor="
                + (healthScaleFactor == null ? "" : healthScaleFactor) + "," + "keepLongStdio="
                + keepLongStdio + "," + "ignoreAttachments="
                + ignoreAttachments + ']';
    }

    /**
     * Don't use the symbol "junit", it would collide with hudson.tasks.junit.JUnitResultArchiver
     */
    @Symbol("junitPublisher")
    @OptionalExtension(requirePlugins = "junit")
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_junit_tests_description();
        }

        @Override
        public int ordinal() {
            return 10;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-junit-results";
        }
    }
}
