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
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestDataPublisher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JunitTestsPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(JunitTestsPublisher.class.getName());
    private static final String GROUP_ID = "org.apache.maven.plugins";
    private static final String SUREFIRE_ID = "maven-surefire-plugin";
    private static final String FAILSAFE_ID = "maven-failsafe-plugin";
    private static final String SUREFIRE_GOAL = "test";
    private static final String FAILSAFE_GOAL = "integration-test";

    private static final long serialVersionUID = 1L;

    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     *
     * @see JUnitResultArchiver#keepLongStdio
     */
    private boolean keepLongStdio;

    /**
     * @see JUnitResultArchiver#keepLongStdio
     */
    @CheckForNull
    private Double healthScaleFactor;


    private boolean ignoreAttachments;

    @DataBoundConstructor
    public JunitTestsPublisher() {

    }

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
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {

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
            listener.getLogger().print(" not found, don't display " + GROUP_ID + ":" + SUREFIRE_ID + ":" + SUREFIRE_GOAL);
            listener.getLogger().print(" nor " + GROUP_ID + ":" + FAILSAFE_ID + ":" + FAILSAFE_GOAL + " results in pipeline screen.");
            return;
        }

        List<Element> sureFireTestEvents = XmlUtils.getExecutionEvents(mavenSpyLogsElt, GROUP_ID, SUREFIRE_ID, SUREFIRE_GOAL);
        List<Element> failSafeTestEvents = XmlUtils.getExecutionEvents(mavenSpyLogsElt, GROUP_ID, FAILSAFE_ID, FAILSAFE_GOAL);

        executeReporter(context, listener, sureFireTestEvents, SUREFIRE_ID + ":" + SUREFIRE_GOAL);
        executeReporter(context, listener, failSafeTestEvents, FAILSAFE_ID + ":" + FAILSAFE_GOAL);
    }

    private void executeReporter(StepContext context, TaskListener listener, List<Element> testEvents, String goal) throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);

        if (testEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] junitPublisher - No " + GROUP_ID + ":" + goal + " execution found");
            }
            return;
        }

        /*
        See commented code below: JUnitResultArchiver.parseAndAttach(...)
        FlowNode node = context.get(FlowNode.class);
        String nodeId = node.getId();
        */

        for (Element testEvent : testEvents) {
            String surefireEventType = testEvent.getAttribute("type");
            if (!surefireEventType.equals("MojoSucceeded") && !surefireEventType.equals("MojoFailed")) {
                continue;
            }
            Element pluginElt = XmlUtils.getUniqueChildElement(testEvent, "plugin");
            Element reportsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "reportsDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(testEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);

            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (reportsDirectoryElt == null) {
                listener.getLogger().println("[withMaven] No <reportsDirectory> element found for <plugin> in " + XmlUtils.toString(testEvent));
                continue;
            }
            String reportsDirectory = reportsDirectoryElt.getTextContent().trim();
            if (reportsDirectory.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${project.build.directory}' found for <project> in " + XmlUtils.toString(testEvent));
                    continue;
                }

                reportsDirectory = reportsDirectory.replace("${project.build.directory}", projectBuildDirectory);

            } else if (reportsDirectory.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${basedir}' found for <project> in " + XmlUtils.toString(testEvent));
                    continue;
                }

                reportsDirectory = reportsDirectory.replace("${basedir}", baseDir);
            }

            reportsDirectory = XmlUtils.getPathInWorkspace(reportsDirectory, workspace);

            String testResults = reportsDirectory + fileSeparatorOnAgent + "*.xml";
            listener.getLogger().println("[withMaven] junitPublisher - Archive test results for Maven artifact " + mavenArtifact.getId() + " generated by " +
                pluginInvocation.getId() + ": " + testResults);
            JUnitResultArchiver archiver = new JUnitResultArchiver(testResults);

            if (healthScaleFactor != null) {
                archiver.setHealthScaleFactor(this.healthScaleFactor);
            }
            archiver.setKeepLongStdio(this.keepLongStdio);

            // even if "org.apache.maven.plugins:maven-surefire-plugin@test" succeeds, it maybe with "-DskipTests" and thus not have any test results.
            archiver.setAllowEmptyResults(true);


            if (Boolean.TRUE.equals(this.ignoreAttachments)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] junitPublisher - Ignore junit test attachments");
                }
            } else {
                String className = "hudson.plugins.junitattachments.AttachmentPublisher";
                try {
                    TestDataPublisher attachmentPublisher =  (TestDataPublisher) Class.forName(className).newInstance();
                    if (LOGGER.isLoggable(Level.FINE)) {
                        listener.getLogger().println("[withMaven] junitPublisher - Publish junit test attachments...");
                    }
                    archiver.setTestDataPublishers(Collections.singletonList(attachmentPublisher));
                } catch(ClassNotFoundException e){
                    listener.getLogger().print("[withMaven] junitPublisher - Jenkins ");
                    listener.hyperlink("https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Attachments+Plugin", "JUnit Attachments Plugin");
                    listener.getLogger().print(" not found, can't publish test attachments.");
                } catch (IllegalAccessException|InstantiationException e) {
                    PrintWriter err = listener.error("[withMaven] junitPublisher - Failure to publish test attachments, exception instantiating '" + className + "'");
                    e.printStackTrace(err);
                }
            }

            try {
                archiver.perform(run, workspace, launcher, listener);
                /*
                TODO replace "archiver.perform(run, workspace, launcher, listener)" by the code below when we can bump the junit-plugin to version 1.23+

                List<FlowNode> enclosingBlocks = JUnitResultsStepExecution.getEnclosingStagesAndParallels(node);
                PipelineTestDetails pipelineTestDetails = new PipelineTestDetails();
                pipelineTestDetails.setNodeId(nodeId);
                pipelineTestDetails.setEnclosingBlocks(JUnitResultsStepExecution.getEnclosingBlockIds(enclosingBlocks));
                pipelineTestDetails.setEnclosingBlockNames(JUnitResultsStepExecution.getEnclosingBlockNames(enclosingBlocks));

                TestResultAction testResultAction = JUnitResultArchiver.parseAndAttach(archiver, pipelineTestDetails,
                        run, workspace, launcher, listener);JunitTestsPublisher

                if (testResultAction != null) {
                    // TODO: Once JENKINS-43995 lands, update this to set the step status instead of the entire build.
                    if (testResultAction.getResult().getFailCount() > 0) {
                        context.setResult(Result.UNSTABLE);
                    }
                }
                */
            } catch (Exception e) {
                listener.error("[withMaven] junitPublisher - Silently ignore exception archiving JUnit results for Maven artifact " + mavenArtifact.toString() + " generated by " + pluginInvocation + ": " + e);
                LOGGER.log(Level.WARNING, "Exception processing " + XmlUtils.toString(testEvent), e);
            }

        }
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
        return "JunitTestsPublisher[" +
                "disabled=" + isDisabled() + "," +
                "healthScaleFactor=" + (healthScaleFactor == null ? ""  : healthScaleFactor) + "," +
                "keepLongStdio=" + keepLongStdio + "," +
                "ignoreAttachments=" + ignoreAttachments +
                ']';
    }

    /**
     * Don't use the symbol "junit", it would collide with hudson.tasks.junit.JUnitResultArchiver
     */
    @Symbol("junitPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Junit Publisher";
        }

        @Override
        public int ordinal() {
            return 10;
        }


        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-junit-results";
        }
    }
}
