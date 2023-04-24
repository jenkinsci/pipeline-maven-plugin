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
import hudson.model.TaskListener;
import hudson.plugins.jacoco.JacocoPublisher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JacocoReportPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(JacocoReportPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public JacocoReportPublisher() {

    }

    /*
     * <ExecutionEvent type="MojoStarted" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-09-24 18:00:21.408">
     *   <project baseDir="/path/to/my-webapp" file="/path/to/my-webapp/pom.xml" groupId="com.mycompany.compliance" name="my-webapp" artifactId="my-webapp" packaging="jar" version="0.0.1-SNAPSHOT">
     *     <build sourceDirectory="/path/to/my-webapp/src/main/java" directory="/path/to/my-webapp/target"/>
     *   </project>
     *   <plugin executionId="default-prepare-agent" goal="prepare-agent" lifecyclePhase="initialize" groupId="org.jacoco" artifactId="jacoco-maven-plugin" version="0.8.2">
     *     <address>${jacoco.address}</address>
     *     <append>${jacoco.append}</append>
     *     <classDumpDir>${jacoco.classDumpDir}</classDumpDir>
     *     <destFile>${jacoco.destFile}</destFile>
     *     <dumpOnExit>${jacoco.dumpOnExit}</dumpOnExit>
     *     <exclClassLoaders>${jacoco.exclClassLoaders}</exclClassLoaders>
     *     <inclBootstrapClasses>${jacoco.inclBootstrapClasses}</inclBootstrapClasses>
     *     <inclNoLocationClasses>${jacoco.inclNoLocationClasses}</inclNoLocationClasses>
     *     <jmx>${jacoco.jmx}</jmx>
     *     <output>${jacoco.output}</output>
     *     <pluginArtifactMap>${plugin.artifactMap}</pluginArtifactMap>
     *     <port>${jacoco.port}</port>
     *     <project>${project}</project>
     *     <propertyName>${jacoco.propertyName}</propertyName>
     *     <sessionId>${jacoco.sessionId}</sessionId>
     *     <skip>${jacoco.skip}</skip>
     *   </plugin>
     * </ExecutionEvent>
     * <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-09-24 18:00:22.635">
     *   <project baseDir="/path/to/my-webapp" file="/path/to/my-webapp/pom.xml" groupId="com.mycompany.compliance" name="my-webapp" artifactId="my-webapp" packaging="jar" version="0.0.1-SNAPSHOT">
     *     <build sourceDirectory="/path/to/my-webapp/src/main/java" directory="/path/to/my-webapp/target"/>
     *   </project>
     *   <plugin executionId="default-prepare-agent" goal="prepare-agent" lifecyclePhase="initialize" groupId="org.jacoco" artifactId="jacoco-maven-plugin" version="0.8.2">
     *     <address>${jacoco.address}</address>
     *     <append>${jacoco.append}</append>
     *     <classDumpDir>${jacoco.classDumpDir}</classDumpDir>
     *     <destFile>${jacoco.destFile}</destFile>
     *     <dumpOnExit>${jacoco.dumpOnExit}</dumpOnExit>
     *     <exclClassLoaders>${jacoco.exclClassLoaders}</exclClassLoaders>
     *     <inclBootstrapClasses>${jacoco.inclBootstrapClasses}</inclBootstrapClasses>
     *     <inclNoLocationClasses>${jacoco.inclNoLocationClasses}</inclNoLocationClasses>
     *     <jmx>${jacoco.jmx}</jmx>
     *     <output>${jacoco.output}</output>
     *     <pluginArtifactMap>${plugin.artifactMap}</pluginArtifactMap>
     *     <port>${jacoco.port}</port>
     *     <project>${project}</project>
     *     <propertyName>${jacoco.propertyName}</propertyName>
     *     <sessionId>${jacoco.sessionId}</sessionId>
     *     <skip>${jacoco.skip}</skip>
     *   </plugin>
     * </ExecutionEvent>
     */

    /**
     * TODO only collect the jacoco report if unit tests have run
     * @param context
     * @param mavenSpyLogsElt maven spy report. WARNING experimental structure for the moment, subject to change.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);

        List<Element> jacocoPrepareAgentEvents = XmlUtils.getExecutionEventsByPlugin(mavenSpyLogsElt, "org.jacoco", "jacoco-maven-plugin", "prepare-agent", "MojoSucceeded", "MojoFailed");
        List<Element> jacocoPrepareAgentIntegrationEvents = XmlUtils.getExecutionEventsByPlugin(mavenSpyLogsElt, "org.jacoco", "jacoco-maven-plugin", "prepare-agent-integration", "MojoSucceeded", "MojoFailed");
        jacocoPrepareAgentEvents.addAll(jacocoPrepareAgentIntegrationEvents); // add prepare-agent-integration goals

        if (jacocoPrepareAgentEvents.isEmpty()) {
            LOGGER.log(Level.FINE, "No org.jacoco:jacoco-maven-plugin:prepare-agent[-integration] execution found");
            return;
        }

        try {
            Class.forName("hudson.plugins.jacoco.JacocoPublisher");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/JaCoCo+Plugin", "JaCoCo Plugin");
            listener.getLogger().println(" not found, don't display org.jacoco:jacoco-maven-plugin:prepare-agent[-integration] results in pipeline screen.");
            return;
        }

        List<JacocoReportDetails> jacocoReportDetails = new ArrayList<>();

        for (Element jacocoPrepareAgentEvent : jacocoPrepareAgentEvents) {

            Element buildElement = XmlUtils.getUniqueChildElementOrNull(jacocoPrepareAgentEvent, "project", "build");
            if (buildElement == null) {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE, "Ignore execution event with missing 'build' child:" + XmlUtils.toString(jacocoPrepareAgentEvent));
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(jacocoPrepareAgentEvent, "plugin");
            Element destFileElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "destFile");
            Element projectElt = XmlUtils.getUniqueChildElement(jacocoPrepareAgentEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (destFileElt == null) {
                listener.getLogger().println("[withMaven] No <destFile> element found for <plugin> in " + XmlUtils.toString(jacocoPrepareAgentEvent));
                continue;
            }
            String destFile = destFileElt.getTextContent().trim();
            if (destFile.equals("${jacoco.destFile}")) {
                destFile = "${project.build.directory}/jacoco.exec";
                if ("prepare-agent-integration".equals(pluginInvocation.goal)) destFile = "${project.build.directory}/jacoco-it.exec";
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${project.build.directory}' found for <project> in " + XmlUtils.toString(jacocoPrepareAgentEvent));
                    continue;
                }

                destFile = destFile.replace("${project.build.directory}", projectBuildDirectory);
            } else if (destFile.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${project.build.directory}' found for <project> in " + XmlUtils.toString(jacocoPrepareAgentEvent));
                    continue;
                }
                destFile = destFile.replace("${project.build.directory}", projectBuildDirectory);

            } else if (destFile.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${basedir}' found for <project> in " + XmlUtils.toString(jacocoPrepareAgentEvent));
                    continue;
                }
                destFile = destFile.replace("${basedir}", baseDir);
            }

            destFile = XmlUtils.getPathInWorkspace(destFile, workspace);

            String sourceDirectory = buildElement.getAttribute("sourceDirectory");
            String classesDirectory = buildElement.getAttribute("directory") + "/classes";

            String sourceDirectoryRelativePath = XmlUtils.getPathInWorkspace(sourceDirectory, workspace);
            String classesDirectoryRelativePath = XmlUtils.getPathInWorkspace(classesDirectory, workspace);

            listener.getLogger().println("[withMaven] jacocoPublisher - Archive JaCoCo analysis results for Maven artifact " + mavenArtifact.toString() + " generated by " +
                    pluginInvocation + ": execFile: " + destFile + ", sources: " + sourceDirectoryRelativePath + ", classes: " + classesDirectoryRelativePath);
            jacocoReportDetails.add(new JacocoReportDetails(destFile, sourceDirectoryRelativePath, classesDirectoryRelativePath,  mavenArtifact.toString() + " " + pluginInvocation));
        }

        JacocoPublisher jacocoPublisher = new JacocoPublisher();

        String aggregatedDestFile = jacocoReportDetails.stream().map(details -> details.execFile).collect(Collectors.joining(","));
        String aggregatedSourceDirectory = jacocoReportDetails.stream().map(details -> details.sourceDirectory).collect(Collectors.joining(","));
        String aggregatedClassesDirectory = jacocoReportDetails.stream().map(details -> details.classesDirectory).collect(Collectors.joining(","));

        jacocoPublisher.setExecPattern(aggregatedDestFile);
        jacocoPublisher.setSourcePattern(aggregatedSourceDirectory);
        jacocoPublisher.setClassPattern(aggregatedClassesDirectory);

        try {
            jacocoPublisher.perform(run, workspace, launcher, listener);
        } catch (Exception e) {
            listener.error("[withMaven] jacocoPublisher - exception archiving JaCoCo results for " + jacocoReportDetails + ": " + e);
            LOGGER.log(Level.WARNING, "Exception processing JaCoCo results", e);
            throw new MavenPipelinePublisherException("jacocoPublisher", "archiving JaCoCo results for " + jacocoReportDetails, e);
        }
    }

    public static class JacocoReportDetails {
        final String execFile, sourceDirectory, classesDirectory, description;

        public JacocoReportDetails(String execFile, String sourceDirectory, String classesDirectory, String description) {
            this.execFile = execFile;
            this.sourceDirectory = sourceDirectory;
            this.classesDirectory = classesDirectory;
            this.description = description;
        }

        @Override
        public String toString() {
            return "JacocoReportDetails{" +
                    "execFile='" + execFile + '\'' +
                    ", sourceDirectory='" + sourceDirectory + '\'' +
                    ", classesDirectory='" + classesDirectory + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
    @Symbol("jacocoPublisher")
    @Extension
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Jacoco Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-jacoco-results";
        }
    }
}
