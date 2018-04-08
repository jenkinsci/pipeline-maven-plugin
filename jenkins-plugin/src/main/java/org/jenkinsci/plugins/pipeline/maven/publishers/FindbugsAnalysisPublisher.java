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
import hudson.plugins.findbugs.FindBugsPublisher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FindbugsAnalysisPublisher extends AbstractHealthAwarePublisher {
    private static final Logger LOGGER = Logger.getLogger(FindbugsAnalysisPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public FindbugsAnalysisPublisher() {

    }

    /*
    <ExecutionEvent type="MojoStarted" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-05 19:46:26.956">
        <project baseDir="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project" file="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project/pom.xml" groupId="com.example" name="demo-pom" artifactId="demo-pom" version="0.0.1-SNAPSHOT">
          <build directory="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project/target"/>
        </project>
        <plugin executionId="findbugs" goal="findbugs" groupId="org.codehaus.mojo" artifactId="findbugs-maven-plugin" version="3.0.4">
          <classFilesDirectory>${project.build.outputDirectory}</classFilesDirectory>
          <compileSourceRoots>${project.compileSourceRoots}</compileSourceRoots>
          <debug>${findbugs.debug}</debug>
          <effort>${findbugs.effort}</effort>
          <excludeBugsFile>${findbugs.excludeBugsFile}</excludeBugsFile>
          <excludeFilterFile>${findbugs.excludeFilterFile}</excludeFilterFile>
          <failOnError>${findbugs.failOnError}</failOnError>
          <findbugsXmlOutput>true</findbugsXmlOutput>
          <findbugsXmlOutputDirectory>${project.build.directory}</findbugsXmlOutputDirectory>
          <fork>${findbugs.fork}</fork>
          <includeFilterFile>${findbugs.includeFilterFile}</includeFilterFile>
          <includeTests>${findbugs.includeTests}</includeTests>
          <jvmArgs>${findbugs.jvmArgs}</jvmArgs>
          <localRepository>${localRepository}</localRepository>
          <maxHeap>${findbugs.maxHeap}</maxHeap>
          <maxRank>${findbugs.maxRank}</maxRank>
          <nested>${findbugs.nested}</nested>
          <omitVisitors>${findbugs.omitVisitors}</omitVisitors>
          <onlyAnalyze>${findbugs.onlyAnalyze}</onlyAnalyze>
          <outputDirectory>${project.reporting.outputDirectory}</outputDirectory>
          <outputEncoding>${outputEncoding}</outputEncoding>
          <pluginArtifacts>${plugin.artifacts}</pluginArtifacts>
          <pluginList>${findbugs.pluginList}</pluginList>
          <project>${project}</project>
          <relaxed>${findbugs.relaxed}</relaxed>
          <remoteArtifactRepositories>${project.remoteArtifactRepositories}</remoteArtifactRepositories>
          <remoteRepositories>${project.remoteArtifactRepositories}</remoteRepositories>
          <skip>${findbugs.skip}</skip>
          <skipEmptyReport>${findbugs.skipEmptyReport}</skipEmptyReport>
          <sourceEncoding>${encoding}</sourceEncoding>
          <testClassFilesDirectory>${project.build.testOutputDirectory}</testClassFilesDirectory>
          <testSourceRoots>${project.testCompileSourceRoots}</testSourceRoots>
          <threshold>${findbugs.threshold}</threshold>
          <timeout>${findbugs.timeout}</timeout>
          <trace>${findbugs.trace}</trace>
          <userPrefs>${findbugs.userPrefs}</userPrefs>
          <visitors>${findbugs.visitors}</visitors>
          <xmlEncoding>UTF-8</xmlEncoding>
          <xmlOutput>${findbugs.xmlOutput}</xmlOutput>
          <xmlOutputDirectory>${project.build.directory}</xmlOutputDirectory>
          <xrefLocation>${project.reporting.outputDirectory}/xref</xrefLocation>
          <xrefTestLocation>${project.reporting.outputDirectory}/xref-test</xrefTestLocation>
        </plugin>
      </ExecutionEvent>
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-05 19:46:28.108">
        <project baseDir="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project" file="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project/pom.xml" groupId="com.example" name="demo-pom" artifactId="demo-pom" version="0.0.1-SNAPSHOT">
          <build directory="/Users/cleclerc/git/cyrille-leclerc/multi-module-maven-project/target"/>
        </project>
        <plugin executionId="findbugs" goal="findbugs" groupId="org.codehaus.mojo" artifactId="findbugs-maven-plugin" version="3.0.4">
          <classFilesDirectory>${project.build.outputDirectory}</classFilesDirectory>
          <compileSourceRoots>${project.compileSourceRoots}</compileSourceRoots>
          <debug>${findbugs.debug}</debug>
          <effort>${findbugs.effort}</effort>
          <excludeBugsFile>${findbugs.excludeBugsFile}</excludeBugsFile>
          <excludeFilterFile>${findbugs.excludeFilterFile}</excludeFilterFile>
          <failOnError>${findbugs.failOnError}</failOnError>
          <findbugsXmlOutput>true</findbugsXmlOutput>
          <findbugsXmlOutputDirectory>${project.build.directory}</findbugsXmlOutputDirectory>
          <fork>${findbugs.fork}</fork>
          <includeFilterFile>${findbugs.includeFilterFile}</includeFilterFile>
          <includeTests>${findbugs.includeTests}</includeTests>
          <jvmArgs>${findbugs.jvmArgs}</jvmArgs>
          <localRepository>${localRepository}</localRepository>
          <maxHeap>${findbugs.maxHeap}</maxHeap>
          <maxRank>${findbugs.maxRank}</maxRank>
          <nested>${findbugs.nested}</nested>
          <omitVisitors>${findbugs.omitVisitors}</omitVisitors>
          <onlyAnalyze>${findbugs.onlyAnalyze}</onlyAnalyze>
          <outputDirectory>${project.reporting.outputDirectory}</outputDirectory>
          <outputEncoding>${outputEncoding}</outputEncoding>
          <pluginArtifacts>${plugin.artifacts}</pluginArtifacts>
          <pluginList>${findbugs.pluginList}</pluginList>
          <project>${project}</project>
          <relaxed>${findbugs.relaxed}</relaxed>
          <remoteArtifactRepositories>${project.remoteArtifactRepositories}</remoteArtifactRepositories>
          <remoteRepositories>${project.remoteArtifactRepositories}</remoteRepositories>
          <skip>${findbugs.skip}</skip>
          <skipEmptyReport>${findbugs.skipEmptyReport}</skipEmptyReport>
          <sourceEncoding>${encoding}</sourceEncoding>
          <testClassFilesDirectory>${project.build.testOutputDirectory}</testClassFilesDirectory>
          <testSourceRoots>${project.testCompileSourceRoots}</testSourceRoots>
          <threshold>${findbugs.threshold}</threshold>
          <timeout>${findbugs.timeout}</timeout>
          <trace>${findbugs.trace}</trace>
          <userPrefs>${findbugs.userPrefs}</userPrefs>
          <visitors>${findbugs.visitors}</visitors>
          <xmlEncoding>UTF-8</xmlEncoding>
          <xmlOutput>${findbugs.xmlOutput}</xmlOutput>
          <xmlOutputDirectory>${project.build.directory}</xmlOutputDirectory>
          <xrefLocation>${project.reporting.outputDirectory}/xref</xrefLocation>
          <xrefTestLocation>${project.reporting.outputDirectory}/xref-test</xrefTestLocation>
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
        FilePath workspace = context.get(FilePath.class);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);


         List<Element> findbugsEvents = XmlUtils.getExecutionEvents(mavenSpyLogsElt, "org.codehaus.mojo", "findbugs-maven-plugin", "findbugs");

        if (findbugsEvents.isEmpty()) {
            LOGGER.log(Level.FINE, "No org.codehaus.mojo:findbugs-maven-plugin:findbugs execution found");
            return;
        }
        try {
            Class.forName("hudson.plugins.findbugs.FindBugsPublisher");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://wiki.jenkins-ci.org/display/JENKINS/FindBugs+Plugin", "FindBugs Plugin");
            listener.getLogger().println(" not found, don't display org.codehaus.mojo:findbugs-maven-plugin:findbugs results in pipeline screen.");
            return;
        }


        for (Element findBugsTestEvent : findbugsEvents) {
            String findBugsEventType = findBugsTestEvent.getAttribute("type");
            if (!findBugsEventType.equals("MojoSucceeded") && !findBugsEventType.equals("MojoFailed")) {
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(findBugsTestEvent, "plugin");
            Element xmlOutputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "xmlOutputDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(findBugsTestEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (xmlOutputDirectoryElt == null) {
                listener.getLogger().println("[withMaven] No <xmlOutputDirectoryElt> element found for <plugin> in " + XmlUtils.toString(findBugsTestEvent));
                continue;
            }
            String xmlOutputDirectory = xmlOutputDirectoryElt.getTextContent().trim();
            if (xmlOutputDirectory.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${project.build.directory}' found for <project> in " + XmlUtils.toString(findBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${project.build.directory}", projectBuildDirectory);

            } else if (xmlOutputDirectory.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${basedir}' found for <project> in " + XmlUtils.toString(findBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${basedir}", baseDir);
            }

            xmlOutputDirectory = XmlUtils.getPathInWorkspace(xmlOutputDirectory, workspace);

            String findBugsResultsFile = xmlOutputDirectory + "/findbugsXml.xml";
            listener.getLogger().println("[withMaven] findbugsPublisher - Archive FindBugs analysis results for Maven artifact " + mavenArtifact.toString() + " generated by " +
                    pluginInvocation + ": " + findBugsResultsFile);
            FindBugsPublisher findBugsPublisher = new FindBugsPublisher();

            findBugsPublisher.setPattern(findBugsResultsFile);

            setHealthAwarePublisherAttributes(findBugsPublisher);

            try {
                findBugsPublisher.perform(run, workspace, launcher, listener);
            } catch (Exception e) {
                listener.error("[withMaven] findbugsPublisher - Silently ignore exception archiving FindBugs results for Maven artifact " + mavenArtifact.toString() + " generated by " +
                        pluginInvocation + ": " + e);
                LOGGER.log(Level.WARNING, "Exception processing " + XmlUtils.toString(findBugsTestEvent), e);
            }

        }

    }

    /**
     * Don't use symbol "findbugs", it would collide with hudson.plugins.findbugs.FindBugsPublisher
     */
    @Symbol("findbugsPublisher")
    @Extension
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Findbugs Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-findbugs-results";
        }
    }
}
