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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.publishers.JenkinsMavenEventSpyLogsPublisher;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenSpyLogProcessor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MavenSpyLogProcessor.class.getName());

    public void processMavenSpyLogs(Request request) throws IOException, InterruptedException {
        FilePath[] mavenSpyLogsList = request.mavenSpyLogFolder.list("maven-spy-*.log");
        LOGGER.log(Level.FINE, "Found {0} maven execution reports in {1}", new Object[]{mavenSpyLogsList.length, request.mavenSpyLogFolder});

        TaskListener listener = request.context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }
        FilePath workspace = request.context.get(FilePath.class);

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
                    new JenkinsMavenEventSpyLogsPublisher().process(request.context, mavenSpyLogs);
                }
                if(request.disableAllPublishers) {
                    LOGGER.log(Level.FINE, "All MavenPublishers have been disabled");
                    continue;
                }
                Element mavenSpyLogsElt = documentBuilder.parse(mavenSpyLogsInputStream).getDocumentElement();

                List<MavenPublisher> mavenPublishers = MavenPublisher.buildPublishersList(request.options, listener);
                for (MavenPublisher mavenPublisher : mavenPublishers) {
                    String skipFileName = mavenPublisher.getDescriptor().getSkipFileName();
                    if (Boolean.TRUE.equals(mavenPublisher.isDisabled())) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Skip '" + mavenPublisher.getDescriptor().getDisplayName() + "' disabled by configuration");
                        }
                    } else if (StringUtils.isNotEmpty(skipFileName) && workspace.child(skipFileName).exists()) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Skip '" + mavenPublisher.getDescriptor().getDisplayName() + "' disabled by marker file '" + skipFileName + "'");
                        }
                    } else {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Run '" + mavenPublisher.getDescriptor().getDisplayName() + "'...");
                        }
                        try {
                            mavenPublisher.process(request.context, mavenSpyLogsElt);
                        } catch (IOException | RuntimeException e) {
                            PrintWriter error = listener.error("[withMaven] WARNING Exception executing Maven reporter '" + mavenPublisher.getDescriptor().getDisplayName() +
                                    "' / " + mavenPublisher.getDescriptor().getId() + "." +
                                    " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ");
                            e.printStackTrace(error);

                        }
                    }
                }

            } catch (SAXException e) {
                Run run = request.context.get(Run.class);
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
        FilePath[] mavenSpyLogsInterruptedList = request.mavenSpyLogFolder.list("maven-spy-*.log.tmp");
        if (mavenSpyLogsInterruptedList.length > 0) {
            listener.getLogger().print("[withMaven] One or multiple Maven executions have been ignored by the " +
                    "Jenkins Pipeline Maven Plugin because they have been interrupted before completion " +
                    "(" + mavenSpyLogsInterruptedList.length + "). See ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/Pipeline+Maven+Plugin#PipelineMavenPlugin-mavenExecutionInterrupted", "Pipeline Maven Plugin FAQ");
            listener.getLogger().println(" for more details.");
            if (LOGGER.isLoggable(Level.FINE)) {
                for (FilePath mavenSpyLogsInterruptedLogs : mavenSpyLogsInterruptedList) {
                    listener.getLogger().print("[withMaven] Ignore: " + mavenSpyLogsInterruptedLogs.getRemote());
                }
            }
        }
    }

    public static class MavenDependency extends MavenArtifact {

        private String scope;
        public boolean optional;

        @Nonnull
        public String getScope() {
            return scope == null ? "compile" : scope;
        }

        public void setScope(String scope) {
            this.scope = scope == null || scope.isEmpty() ? null : scope;
        }

        @Override
        public String toString() {
            return "MavenDependency{" +
                    groupId + ":" +
                    artifactId + ":" +
                    type +
                    (classifier == null ? "" : ":" + classifier) + ":" +
                    baseVersion + ", " +
                    "scope: " + scope + ", " +
                    " optional: " + optional +
                    " version: " + version +
                    " snapshot: " + snapshot +
                    (file == null ? "" : " " + file) +
                    '}';
        }
        
        public MavenArtifact asMavenArtifact() {
            MavenArtifact result = new MavenArtifact();
            
            result.groupId = groupId;
            result.artifactId = artifactId;
            result.version = version;
            result.baseVersion = baseVersion;
            result.type = type;
            result.classifier = classifier;
            result.extension = extension;
            result.file = file;
            result.snapshot = snapshot;
            
            return result;
        }
        

		@Override
		public int hashCode() {
		    return Objects.hash(super.hashCode(), optional, scope);
		}

		@Override
		public boolean equals(Object obj) {
		    if (this == obj)
		        return true;
		    if (!super.equals(obj))
		        return false;
		    if (getClass() != obj.getClass())
		        return false;
		    MavenDependency other = (MavenDependency) obj;
		    if (optional != other.optional)
		        return false;
		    if (scope == null) {
		        if (other.scope != null)
		            return false;
		        } else if (!scope.equals(other.scope))
		            return false;
		    return true;
		}
    }

    /*
      <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.19.1">
     */
    public static class PluginInvocation {
        public String groupId, artifactId, version, goal, executionId;

        public String getId() {
            return artifactId + ":" +
                    goal + " " +
                    "(" + executionId + ")";
        }

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

    public static class Request {
        public StepContext context;
        public FilePath mavenSpyLogFolder;
        public List<MavenPublisher> options;
        public boolean disableAllPublishers = false;

        public Request context( StepContext context ) {
            this.context = context;
            return this;
        }

        public Request mavenSpyLogFolder( FilePath mavenSpyLogFolder ) {
            this.mavenSpyLogFolder = mavenSpyLogFolder;
            return this;
        }

        public Request options( List<MavenPublisher> options ) {
            this.options = options;
            return this;
        }

        public Request disableAllPublishers( boolean disableAllPublishers ) {
            this.disableAllPublishers = disableAllPublishers;
            return this;
        }
    }
}
