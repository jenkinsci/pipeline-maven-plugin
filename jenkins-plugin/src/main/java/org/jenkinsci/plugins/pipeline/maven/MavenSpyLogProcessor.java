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
import hudson.model.TaskListener;
import jenkins.model.InterruptedBuildAction;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.publishers.JenkinsMavenEventSpyLogsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenPipelinePublisherException;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenSpyLogProcessor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MavenSpyLogProcessor.class.getName());

    public void processMavenSpyLogs(@Nonnull StepContext context, @Nonnull FilePath mavenSpyLogFolder, @Nonnull List<MavenPublisher> options,
                                    @Nonnull MavenPublisherStrategy publisherStrategy) throws IOException, InterruptedException {

        long nanosBefore = System.nanoTime();

        FilePath[] mavenSpyLogsList = mavenSpyLogFolder.list("maven-spy-*.log");
        LOGGER.log(Level.FINE, "Found {0} maven execution reports in {1}", new Object[]{mavenSpyLogsList.length, mavenSpyLogFolder});

        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);

        DocumentBuilder documentBuilder;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
            dbf.setExpandEntityReferences(false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all
            // XML entity attacks are prevented
            // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // If you can't completely disable DTDs, then at least do the following:
            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
            // JDK7+ - http://xml.org/sax/features/external-general-entities
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);

            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
            // JDK7+ - http://xml.org/sax/features/external-parameter-entities
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            // Disable external DTDs as well
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
            dbf.setXIncludeAware(false);

            documentBuilder = dbf.newDocumentBuilder();

            // See https://github.com/jenkinsci/jenkins/blob/jenkins-2.176/core/src/main/java/jenkins/util/xml/XMLUtils.java#L114
            documentBuilder.setEntityResolver(XmlUtils.RestrictiveEntityResolver.INSTANCE);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Failure to create a DocumentBuilder", e);
        }

        for (FilePath mavenSpyLogs : mavenSpyLogsList) {
            List<Map.Entry<String, Long>> durationInMillisPerPublisher = new ArrayList();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] Evaluate Maven Spy logs: " + mavenSpyLogs.getRemote());
                }
                InputStream mavenSpyLogsInputStream = mavenSpyLogs.read();
                if (mavenSpyLogsInputStream == null) {
                    throw new IllegalStateException("InputStream for " + mavenSpyLogs.getRemote() + " is null");
                }

                FilePath archiveJenkinsMavenEventSpyLogs = workspace.child(".archive-jenkins-maven-event-spy-logs");
                if (archiveJenkinsMavenEventSpyLogs.exists()) {
                    LOGGER.log(Level.FINE, "Archive Jenkins Maven Event Spy logs {0}", mavenSpyLogs.getRemote());
                    new JenkinsMavenEventSpyLogsPublisher().process(context, mavenSpyLogs);
                }

                Element mavenSpyLogsElt = documentBuilder.parse(mavenSpyLogsInputStream).getDocumentElement();

                if (LOGGER.isLoggable(Level.FINE)){
                    listener.getLogger().println("[withMaven] Maven Publisher Strategy: " + publisherStrategy.getDescription());
                }
                List<MavenPublisher> mavenPublishers = publisherStrategy.buildPublishersList(options, listener);
                List<MavenPipelinePublisherException> exceptions = new ArrayList<>();
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
                        long nanosBeforePublisher = System.nanoTime();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Run '" + mavenPublisher.getDescriptor().getDisplayName() + "'...");
                        }
                        try {
                            mavenPublisher.process(context, mavenSpyLogsElt);
                        } catch (InterruptedException e) {
                            listener.error("[withMaven] Processing of Maven build outputs interrupted in " + mavenPublisher.toString() + " after " +
                                    TimeUnit.MILLISECONDS.convert(System.nanoTime() - nanosBefore, TimeUnit.NANOSECONDS) + "ms.");
                            Thread.currentThread().interrupt();  // set interrupt flag
                            throw e;
                        } catch (MavenPipelinePublisherException e) {
                            exceptions.add(e);
                        } catch (Exception e) {
                            PrintWriter error = listener.error("[withMaven] WARNING Exception executing Maven reporter '" + mavenPublisher.getDescriptor().getDisplayName() +
                                    "' / " + mavenPublisher.getDescriptor().getId() + "." +
                                    " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ");
                            e.printStackTrace(error);
                            exceptions.add(new MavenPipelinePublisherException(mavenPublisher.getDescriptor().getDisplayName(), "", e));
                        } finally {
                            durationInMillisPerPublisher.add(new AbstractMap.SimpleImmutableEntry(mavenPublisher.getDescriptor().getDisplayName(), TimeUnit.MILLISECONDS.convert(System.nanoTime() - nanosBeforePublisher, TimeUnit.NANOSECONDS)));
                        }
                    }
                }
                if (!exceptions.isEmpty()) {
                    throw new MavenPipelineException(exceptions);
                }
            } catch (MavenPipelineException e) {
                throw e;
            } catch (SAXException e) {
                Run run = context.get(Run.class);
                String msg = "";
                if (run.getActions(InterruptedBuildAction.class).isEmpty()) {
                    msg = "[withMaven] WARNING Exception parsing the logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + ", ignore file. " +
                            " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ";
                } else {
                    // job has been aborted (see InterruptedBuildAction)
                    msg = "[withMaven] WARNING logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + " are invalid, probably due to the interruption of the job, ignore file.";
                }
                PrintWriter errorWriter = listener.error(msg);
                e.printStackTrace(errorWriter);
                throw new MavenPipelineException(e);
            } catch (InterruptedException e) {
                PrintWriter errorWriter = listener.error("[withMaven] Processing of Maven build outputs interrupted after " +
                        TimeUnit.MILLISECONDS.convert(System.nanoTime() - nanosBefore, TimeUnit.NANOSECONDS) + "ms.");
                if (LOGGER.isLoggable(Level.FINE)) {
                    e.printStackTrace(errorWriter);
                }
                Thread.currentThread().interrupt();  // set interrupt flag
                return;
            } catch (Exception e) {
                PrintWriter errorWriter = listener.error("[withMaven] WARNING Exception processing the logs generated by the Jenkins Maven Event Spy " + mavenSpyLogs + ", ignore file. " +
                        " Please report a bug associated for the component 'pipeline-maven-plugin' at https://issues.jenkins-ci.org ");
                e.printStackTrace(errorWriter);
                throw new MavenPipelineException(e);
            } finally {
                if (LOGGER.isLoggable(Level.INFO)) {
                    listener.getLogger().println("[withMaven] Publishers: " +
                            durationInMillisPerPublisher.stream().filter(entry -> entry.getValue() > 0).
                                    map(entry -> entry.getKey() + ": " + entry.getValue() + " ms").
                                    collect(Collectors.joining(", ")));
                }
            }
        }
        FilePath[] mavenSpyLogsInterruptedList = mavenSpyLogFolder.list("maven-spy-*.log.tmp");
        if (mavenSpyLogsInterruptedList.length > 0) {
            listener.getLogger().print("[withMaven] One or multiple Maven executions have been ignored by the " +
                    "Jenkins Pipeline Maven Plugin because they have been interrupted before completion " +
                    "(" + mavenSpyLogsInterruptedList.length + "). See ");
            listener.hyperlink("https://github.com/jenkinsci/pipeline-maven-plugin/blob/master/FAQ.adoc#how-to-use-the-pipeline-maven-plugin-with-docker", "Pipeline Maven Plugin FAQ");
            listener.getLogger().println(" for more details.");
            if (LOGGER.isLoggable(Level.FINE)) {
                for (FilePath mavenSpyLogsInterruptedLogs : mavenSpyLogsInterruptedList) {
                    listener.getLogger().print("[withMaven] Ignore: " + mavenSpyLogsInterruptedLogs.getRemote());
                }
            }
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
}
