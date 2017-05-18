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

package org.jenkinsci.plugins.pipeline.maven;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.pipeline.maven.reporters.*;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenSpyLogProcessor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MavenSpyLogProcessor.class.getName());

    public void processMavenSpyLogs(StepContext context, FilePath mavenSpyLogFolder) throws IOException, InterruptedException {
        FilePath[] mavenSpyLogsList = mavenSpyLogFolder.list("maven-spy-*.log");
        LOGGER.log(Level.FINE, "Found {0} maven execution reports in {1}", new Object[]{mavenSpyLogsList.length, mavenSpyLogFolder});

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }
        FilePath workspace = context.get(FilePath.class);

        DocumentBuilder documentBuilder;
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Failure to create a DocumentBuilder", e);
        }

        for (FilePath mavenSpyLogs : mavenSpyLogsList) {
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven]  Evaluate Maven Spy logs: " + mavenSpyLogs.getRemote());
                }
                InputStream mavenSpyLogsInputStream = mavenSpyLogs.read();
                if (mavenSpyLogsInputStream == null) {
                    throw new IllegalStateException("InputStream for " + mavenSpyLogs.getRemote() + " is null");
                }

                FilePath archiveJenkinsMavenEventSpyLogs = workspace.child(".archive-jenkins-maven-event-spy-logs");
                if (archiveJenkinsMavenEventSpyLogs.exists()) {
                    LOGGER.log(Level.FINE, "Archive Jenkins Maven Event Spy logs {0}", mavenSpyLogs.getRemote());
                    new JenkinsMavenEventSpyLogsReporter().process(context, mavenSpyLogs);
                }

                Element mavenSpyLogsElt = documentBuilder.parse(mavenSpyLogsInputStream).getDocumentElement();

                FilePath skipArchiveArtifactsFile = workspace.child(".skip-archive-generated-artifacts");
                if (skipArchiveArtifactsFile.exists()) {
                    listener.getLogger().println("[withMaven] Skip archiving of generated artifacts, file '" + skipArchiveArtifactsFile + "' found in workspace");
                } else {
                    LOGGER.log(Level.FINE, "Look for generated artifacts to archive, file {0} NOT found in workspace", skipArchiveArtifactsFile);
                    new GeneratedArtifactsReporter().process(context, mavenSpyLogsElt);
                }

                FilePath skipFingerprintDependenciesFile = workspace.child(".skip-fingerprint-maven-dependencies");
                if (skipFingerprintDependenciesFile.exists()) {
                    listener.getLogger().println("[withMaven] Skip fingerprinting of maven dependencies, file '" + skipFingerprintDependenciesFile + "' found in workspace");
                } else {
                    LOGGER.log(Level.FINE, "Look for dependencies to fingerprint, file {0} NOT found in workspace", skipFingerprintDependenciesFile);
                    new DependenciesReporter().process(context, mavenSpyLogsElt);
                }

                FilePath skipJunitFile = workspace.child(".skip-publish-junit-results");
                if (skipJunitFile.exists()) {
                    listener.getLogger().println("[withMaven] Skip publishing of JUnit results, file '" + skipJunitFile + "' found in workspace");
                } else {
                    LOGGER.log(Level.FINE, "Look for JUnit results to publish, file {0} NOT found in workspace", skipJunitFile);
                    new JunitTestsReporter().process(context, mavenSpyLogsElt);
                }
                FilePath skipFindbugsFile = workspace.child(".skip-publish-findbugs-results");
                if (skipFindbugsFile.exists()) {
                    listener.getLogger().println("[withMaven] Skip publishing of FindBugs results, file '" + skipFindbugsFile + "' found in workspace");
                } else {
                    LOGGER.log(Level.FINE, "Look for Findbugs results to publish, file {0} NOT found in workspace", skipFindbugsFile);
                    new FindbugsAnalysisReporter().process(context, mavenSpyLogsElt);
                }

                FilePath skipTasksScannerFile = workspace.child(".skip-task-scanner");
                if (skipTasksScannerFile.exists()) {
                    listener.getLogger().println("[withMaven] Skip publishing of tasks in source code, file '" + skipTasksScannerFile + "' found in workspace");
                } else {
                    LOGGER.log(Level.FINE, "Look for tasks in source code to publish, file {0} NOT found in workspace", skipTasksScannerFile);
                    new TasksScannerReporter().process(context, mavenSpyLogsElt);
                }


            } catch (SAXException e) {
                Run run = context.get(Run.class);
                if (run.getActions(InterruptedBuildAction.class).isEmpty()) {
                    listener.error("[withMaven] WARNING Exception parsing the logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + ", ignore file. " +
                            " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ");
                } else {
                    // job has been aborted (see InterruptedBuildAction)
                    listener.error("[withMaven] WARNING logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + " are invalid, probably due to the interruption of the job, ignore file.");
                }
                listener.error(e.toString());
            } catch (Exception e) {
                PrintWriter errorWriter = listener.error("[withMaven] WARNING Exception processing the logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + ", ignore file. " +
                " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ");
                e.printStackTrace(errorWriter);
            }
        }
    }



    public static class MavenArtifact {
        public String groupId, artifactId, version, type, classifier, extension;
        public String file;

        public String getFileName() {
            return artifactId + "-" + version + ((classifier == null || classifier.isEmpty()) ? "" : "-" + classifier) + "." + extension;
        }

        @Override
        public String toString() {
            return "MavenArtifact{" +
                    groupId + ":" +
                    artifactId + ":" +
                    type +
                    (classifier == null ? "" : ":" + classifier) + ":" +
                    version +
                    (file == null ? "" : " " + file) +
                    '}';
        }
    }

    /*
      <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.19.1">
     */
    public static class PluginInvocation {
        public String groupId, artifactId, version, goal, executionId;

        @Override
        public String toString() {
            return "PluginInvocation{" +
                    groupId + ":" +
                    artifactId + ":" +
                    version + "@" +
                    goal + " " +
                    " " + executionId +
                    '}';
        }
    }
}
