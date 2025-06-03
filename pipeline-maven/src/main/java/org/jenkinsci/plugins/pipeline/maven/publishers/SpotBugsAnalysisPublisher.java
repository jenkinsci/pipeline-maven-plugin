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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.findbugs.FindBugsPublisher;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

/**
 * handle {@code mvn spotbugs:spotbugs} invocations.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SpotBugsAnalysisPublisher extends AbstractHealthAwarePublisher {
    private static final Logger LOGGER = Logger.getLogger(SpotBugsAnalysisPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public SpotBugsAnalysisPublisher() {}

    /*
    <ExecutionEvent type="MojoStarted" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-06-04 09:32:13.205">
        <project baseDir="/path/to/test-spotbugs" file="/path/to/test-spotbugs/pom.xml" groupId="com.example.spotbugs" name="my-jar" artifactId="my-jar" version="0.1-SNAPSHOT">
            <build sourceDirectory="/path/to/test-spotbugs/src/main/java" directory="/path/to/test-spotbugs/target"/>
        </project>
        <plugin executionId="default-cli" goal="spotbugs" groupId="com.github.spotbugs" artifactId="spotbugs-maven-plugin" version="3.1.3">
            <classFilesDirectory>${project.build.outputDirectory}</classFilesDirectory>
            <compileSourceRoots>${project.compileSourceRoots}</compileSourceRoots>
            <debug>${spotbugs.debug}</debug>
            <effort>${spotbugs.effort}</effort>
            <excludeBugsFile>${spotbugs.excludeBugsFile}</excludeBugsFile>
            <excludeFilterFile>${spotbugs.excludeFilterFile}</excludeFilterFile>
            <failOnError>${spotbugs.failOnError}</failOnError>
            <fork>${spotbugs.fork}</fork>
            <includeFilterFile>${spotbugs.includeFilterFile}</includeFilterFile>
            <includeTests>${spotbugs.includeTests}</includeTests>
            <inputEncoding>${encoding}</inputEncoding>
            <jvmArgs>${spotbugs.jvmArgs}</jvmArgs>
            <localRepository>${localRepository}</localRepository>
            <maxHeap>${spotbugs.maxHeap}</maxHeap>
            <maxRank>${spotbugs.maxRank}</maxRank>
            <nested>${spotbugs.nested}</nested>
            <omitVisitors>${spotbugs.omitVisitors}</omitVisitors>
            <onlyAnalyze>${spotbugs.onlyAnalyze}</onlyAnalyze>
            <outputDirectory>${project.reporting.outputDirectory}</outputDirectory>
            <outputEncoding>${outputEncoding}</outputEncoding>
            <pluginArtifacts>${plugin.artifacts}</pluginArtifacts>
            <pluginList>${spotbugs.pluginList}</pluginList>
            <project>${project}</project>
            <relaxed>${spotbugs.relaxed}</relaxed>
            <remoteArtifactRepositories>${project.remoteArtifactRepositories}</remoteArtifactRepositories>
            <remoteRepositories>${project.remoteArtifactRepositories}</remoteRepositories>
            <skip>${spotbugs.skip}</skip>
            <skipEmptyReport>${spotbugs.skipEmptyReport}</skipEmptyReport>
            <sourceEncoding>${encoding}</sourceEncoding>
            <spotbugsXmlOutput>true</spotbugsXmlOutput>
            <spotbugsXmlOutputDirectory>${project.build.directory}</spotbugsXmlOutputDirectory>
            <testClassFilesDirectory>${project.build.testOutputDirectory}</testClassFilesDirectory>
            <testSourceRoots>${project.testCompileSourceRoots}</testSourceRoots>
            <threshold>${spotbugs.threshold}</threshold>
            <timeout>${spotbugs.timeout}</timeout>
            <trace>${spotbugs.trace}</trace>
            <userPrefs>${spotbugs.userPrefs}</userPrefs>
            <visitors>${spotbugs.visitors}</visitors>
            <xmlEncoding>UTF-8</xmlEncoding>
            <xmlOutput>${spotbugs.xmlOutput}</xmlOutput>
            <xmlOutputDirectory>${project.build.directory}</xmlOutputDirectory>
            <xrefLocation>${project.reporting.outputDirectory}/xref</xrefLocation>
            <xrefTestLocation>${project.reporting.outputDirectory}/xref-test</xrefTestLocation>
        </plugin>
    </ExecutionEvent>
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-06-04 09:32:19.043">
        <project baseDir="/path/to/test-spotbugs" file="/path/to/test-spotbugs/pom.xml" groupId="com.example.spotbugs" name="my-jar" artifactId="my-jar" version="0.1-SNAPSHOT">
            <build sourceDirectory="/path/to/test-spotbugs/src/main/java" directory="/path/to/test-spotbugs/target"/>
        </project>
        <plugin executionId="default-cli" goal="spotbugs" groupId="com.github.spotbugs" artifactId="spotbugs-maven-plugin" version="3.1.3">
            <classFilesDirectory>${project.build.outputDirectory}</classFilesDirectory>
            <compileSourceRoots>${project.compileSourceRoots}</compileSourceRoots>
            <debug>${spotbugs.debug}</debug>
            <effort>${spotbugs.effort}</effort>
            <excludeBugsFile>${spotbugs.excludeBugsFile}</excludeBugsFile>
            <excludeFilterFile>${spotbugs.excludeFilterFile}</excludeFilterFile>
            <failOnError>${spotbugs.failOnError}</failOnError>
            <fork>${spotbugs.fork}</fork>
            <includeFilterFile>${spotbugs.includeFilterFile}</includeFilterFile>
            <includeTests>${spotbugs.includeTests}</includeTests>
            <inputEncoding>${encoding}</inputEncoding>
            <jvmArgs>${spotbugs.jvmArgs}</jvmArgs>
            <localRepository>${localRepository}</localRepository>
            <maxHeap>${spotbugs.maxHeap}</maxHeap>
            <maxRank>${spotbugs.maxRank}</maxRank>
            <nested>${spotbugs.nested}</nested>
            <omitVisitors>${spotbugs.omitVisitors}</omitVisitors>
            <onlyAnalyze>${spotbugs.onlyAnalyze}</onlyAnalyze>
            <outputDirectory>${project.reporting.outputDirectory}</outputDirectory>
            <outputEncoding>${outputEncoding}</outputEncoding>
            <pluginArtifacts>${plugin.artifacts}</pluginArtifacts>
            <pluginList>${spotbugs.pluginList}</pluginList>
            <project>${project}</project>
            <relaxed>${spotbugs.relaxed}</relaxed>
            <remoteArtifactRepositories>${project.remoteArtifactRepositories}</remoteArtifactRepositories>
            <remoteRepositories>${project.remoteArtifactRepositories}</remoteRepositories>
            <skip>${spotbugs.skip}</skip>
            <skipEmptyReport>${spotbugs.skipEmptyReport}</skipEmptyReport>
            <sourceEncoding>${encoding}</sourceEncoding>
            <spotbugsXmlOutput>true</spotbugsXmlOutput>
            <spotbugsXmlOutputDirectory>${project.build.directory}</spotbugsXmlOutputDirectory>
            <testClassFilesDirectory>${project.build.testOutputDirectory}</testClassFilesDirectory>
            <testSourceRoots>${project.testCompileSourceRoots}</testSourceRoots>
            <threshold>${spotbugs.threshold}</threshold>
            <timeout>${spotbugs.timeout}</timeout>
            <trace>${spotbugs.trace}</trace>
            <userPrefs>${spotbugs.userPrefs}</userPrefs>
            <visitors>${spotbugs.visitors}</visitors>
            <xmlEncoding>UTF-8</xmlEncoding>
            <xmlOutput>${spotbugs.xmlOutput}</xmlOutput>
            <xmlOutputDirectory>${project.build.directory}</xmlOutputDirectory>
            <xrefLocation>${project.reporting.outputDirectory}/xref</xrefLocation>
            <xrefTestLocation>${project.reporting.outputDirectory}/xref-test</xrefTestLocation>
        </plugin>
    </ExecutionEvent>
         */
    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);

        List<Element> spotbugsEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt,
                "com.github.spotbugs",
                "spotbugs-maven-plugin",
                "spotbugs",
                "MojoSucceeded",
                "MojoFailed");

        if (spotbugsEvents.isEmpty()) {
            LOGGER.log(Level.FINE, "No com.github.spotbugs:spotbugs-maven-plugin:spotbugs execution found");
            return;
        }
        try {
            Class.forName("hudson.plugins.findbugs.FindBugsPublisher");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://wiki.jenkins-ci.org/display/JENKINS/FindBugs+Plugin", "FindBugs Plugin");
            listener.getLogger()
                    .println(
                            " not found, don't display com.github.spotbugs:spotbugs-maven-plugin:spotbugs results in pipeline screen.");
            return;
        }

        for (Element spotbugsEvent : spotbugsEvents) {
            String findBugsEventType = spotbugsEvent.getAttribute("type");
            if (!findBugsEventType.equals("MojoSucceeded") && !findBugsEventType.equals("MojoFailed")) {
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(spotbugsEvent, "plugin");
            Element xmlOutputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "xmlOutputDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(spotbugsEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (xmlOutputDirectoryElt == null) {
                listener.getLogger()
                        .println("[withMaven] No <xmlOutputDirectoryElt> element found for <plugin> in "
                                + XmlUtils.toString(spotbugsEvent));
                continue;
            }
            String xmlOutputDirectory = XmlUtils.resolveMavenPlaceholders(xmlOutputDirectoryElt, projectElt);
            if (xmlOutputDirectory == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] could not resolve placeholder '${project.build.directory}' or '${basedir}' in "
                                        + XmlUtils.toString(spotbugsEvent));
                continue;
            }
            xmlOutputDirectory = XmlUtils.getPathInWorkspace(xmlOutputDirectory, workspace);

            String spotbugsResultsFile = xmlOutputDirectory + "/spotbugsXml.xml";
            listener.getLogger()
                    .println("[withMaven] SpotBugsPublisher - Archive SpotBugs analysis results for Maven artifact "
                            + mavenArtifact.toString() + " generated by " + pluginInvocation + ": "
                            + spotbugsResultsFile);
            FindBugsPublisher findBugsPublisher = new FindBugsPublisher();

            findBugsPublisher.setPattern(spotbugsResultsFile);

            setHealthAwarePublisherAttributes(findBugsPublisher);

            try {
                findBugsPublisher.perform(run, workspace, launcher, listener);
            } catch (Exception e) {
                listener.error(
                        "[withMaven] SpotBugsPublisher - exception archiving FindBugs results for Maven artifact "
                                + mavenArtifact.toString() + " generated by " + pluginInvocation + ": " + e);
                LOGGER.log(Level.WARNING, "Exception processing " + XmlUtils.toString(spotbugsEvent), e);
                throw new MavenPipelinePublisherException(
                        "SpotBugsPublisher",
                        "archiving FindBugs results for Maven artifact " + mavenArtifact.getId() + " generated by "
                                + pluginInvocation.getId(),
                        e);
            }
        }
    }

    @Symbol("spotbugsPublisher")
    @Extension
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_spotbugs_analysis_description();
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-spotbugs-results";
        }
    }
}
