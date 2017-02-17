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
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.findbugs.FindBugsReporter;
import hudson.tasks.Fingerprinter;
import jenkins.model.ArtifactManager;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.reporters.FindbugsAnalysisReporter;
import org.jenkinsci.plugins.pipeline.maven.reporters.JunitTestsReporter;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    protected final transient DocumentBuilder documentBuilder;

    public MavenSpyLogProcessor() {
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void processMavenSpyLogs(StepContext context, FilePath mavenSpyLogFolder) throws IOException, InterruptedException {
        FilePath[] mavenSpyLogsList = mavenSpyLogFolder.list("maven-spy-*.log");

        Run run = context.get(Run.class);
        ArtifactManager artifactManager = run.pickArtifactManager();
        Launcher launcher = context.get(Launcher.class);
        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("listener is NULL"); // TODO
        }
        FilePath workspace = context.get(FilePath.class); // TODO check that it's the good workspace

        for (FilePath mavenSpyLogs : mavenSpyLogsList) {
            try {
                LOGGER.log(Level.INFO, "Evaluate Maven Spy logs: " + mavenSpyLogs.getRemote());
                Element mavenSpyLogsElt = documentBuilder.parse(mavenSpyLogs.read()).getDocumentElement();

                List<MavenArtifact> mavenArtifacts = listArtifacts(mavenSpyLogsElt);
                List<MavenArtifact> attachedMavenArtifacts = listAttachedArtifacts(mavenSpyLogsElt);
                List<MavenArtifact> join = new ArrayList<>();
                join.addAll(mavenArtifacts);
                join.addAll(attachedMavenArtifacts);


                Map<String, String> artifactsToArchive = new HashMap<>(); // artifactPathInArchiveZone -> artifactPathInWorkspace
                Map<String, String> artifactsToFingerPrint = new HashMap<>(); // artifactPathInArchiveZone -> artifactMd5
                for (MavenArtifact mavenArtifact : join) {
                    if (StringUtils.isEmpty(mavenArtifact.file)) {
                        listener.error("Can't archive maven artifact with no file attached: " + mavenArtifact);
                        continue;
                    }

                    String artifactPathInArchiveZone =
                            mavenArtifact.groupId.replace('.', '/') + "/" +
                                    mavenArtifact.artifactId + "/" +
                                    mavenArtifact.version + "/" +
                                    mavenArtifact.getFileName();

                    String artifactPathInWorkspace = XmlUtils.getPathInWorkspace(mavenArtifact.file, workspace);

                    if (StringUtils.isEmpty(artifactPathInWorkspace)) {
                        listener.error("Invalid path in the workspace (" + workspace.getRemote() + ") for artifact " + mavenArtifact);
                    } else {
                        listener.getLogger().println("Archive " + mavenArtifact + " under " + artifactPathInArchiveZone);
                        artifactsToArchive.put(artifactPathInArchiveZone, artifactPathInWorkspace);
                        FilePath artifactFilePath = new FilePath(workspace, artifactPathInWorkspace);
                        String artifactDigest = artifactFilePath.digest();
                        listener.getLogger().println("Archive " + mavenArtifact + " under " + artifactPathInArchiveZone + " has digest " + artifactDigest);
                        artifactsToFingerPrint.put(artifactPathInArchiveZone, artifactDigest);
                    }
                }

                // ARCHIVE GENERATED MAVEN ARTIFACT
                // see org.jenkinsci.plugins.workflow.steps.ArtifactArchiverStepExecution#run
                artifactManager.archive(workspace, launcher, new BuildListenerAdapter(listener), artifactsToArchive);

                // FINGERPRINT GENERATED MAVEN ARTIFACT
                Fingerprinter.FingerprintAction fingerprintAction = run.getAction(Fingerprinter.FingerprintAction.class);
                if (fingerprintAction == null) {
                    run.addAction(new Fingerprinter.FingerprintAction(run, artifactsToFingerPrint));
                } else {
                    fingerprintAction.add(artifactsToFingerPrint);
                }

                new JunitTestsReporter().process(context, mavenSpyLogsElt);
                new FindbugsAnalysisReporter().process(context, mavenSpyLogsElt);
            } catch (SAXException e) {
                listener.error("Exception parsing maven spy logs " + mavenSpyLogs + ", ignore file");
                e.printStackTrace(listener.getLogger());
            }
        }
    }

    /*
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-01-31 00:15:42.255">
    <project artifactId="pipeline-maven" groupId="org.jenkins-ci.plugins" name="Pipeline Maven Integration Plugin" version="0.6-SNAPSHOT"/>
    <no-execution-found/>
    <artifact groupId="org.jenkins-ci.plugins" artifactId="pipeline-maven" id="org.jenkins-ci.plugins:pipeline-maven:hpi:0.6-SNAPSHOT" type="hpi" version="0.6-SNAPSHOT">
      <file>/Users/cleclerc/git/jenkins/pipeline-maven-plugin/target/pipeline-maven.hpi</file>
    </artifact>
    <attachedArtifacts>
      <artifact groupId="org.jenkins-ci.plugins" artifactId="pipeline-maven" id="org.jenkins-ci.plugins:pipeline-maven:jar:0.6-SNAPSHOT" type="jar" version="0.6-SNAPSHOT">
        <file>/Users/cleclerc/git/jenkins/pipeline-maven-plugin/target/pipeline-maven.jar</file>
      </artifact>
    </attachedArtifacts>
  </ExecutionEvent>
     */

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenArtifact}
     */
    /*

    <artifact artifactId="demo-pom" groupId="com.example" id="com.example:demo-pom:pom:0.0.1-SNAPSHOT" type="pom" version="0.0.1-SNAPSHOT">
      <file/>
    </artifact>
     */
    @Nonnull
    public List<MavenArtifact> listArtifacts(Element mavenSpyLogs) {

        List<MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : XmlUtils.getExecutionEvents(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "project");
            MavenArtifact projectArtifact = XmlUtils.newMavenArtifact(projectElt);

            MavenArtifact pomArtifact = new MavenArtifact();
            pomArtifact.groupId = projectArtifact.groupId;
            pomArtifact.artifactId = projectArtifact.artifactId;
            pomArtifact.version = projectArtifact.version;
            pomArtifact.type = "pom";
            pomArtifact.file = projectElt.getAttribute("file");

            result.add(pomArtifact);

            Element artifactElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "artifact");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(artifactElt);
            if ("pom".equals(mavenArtifact.type)) {
                // NO file is generated by Maven for pom projects, skip
                continue;
            }

            Element fileElt = XmlUtils.getUniqueChildElementOrNull(artifactElt, "file");
            if (fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                LOGGER.log(Level.WARNING, "listArtifacts: Project " + projectArtifact + ":  no associated file found for " + mavenArtifact + " in " + XmlUtils.toString(artifactElt));
            }
            mavenArtifact.file = StringUtils.trim(fileElt.getTextContent());
            result.add(mavenArtifact);
        }

        return result;
    }


    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link FilePath#getRemote()}
     */
    /*
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-01-31 00:15:42.255">
    <project artifactId="pipeline-maven" groupId="org.jenkins-ci.plugins" name="Pipeline Maven Integration Plugin" version="0.6-SNAPSHOT"/>
    <no-execution-found/>
    <artifact groupId="org.jenkins-ci.plugins" artifactId="pipeline-maven" id="org.jenkins-ci.plugins:pipeline-maven:hpi:0.6-SNAPSHOT" type="hpi" version="0.6-SNAPSHOT">
      <file>/Users/cleclerc/git/jenkins/pipeline-maven-plugin/target/pipeline-maven.hpi</file>
    </artifact>
    <attachedArtifacts>
      <artifact groupId="org.jenkins-ci.plugins" artifactId="pipeline-maven" id="org.jenkins-ci.plugins:pipeline-maven:jar:0.6-SNAPSHOT" type="jar" version="0.6-SNAPSHOT">
        <file>/Users/cleclerc/git/jenkins/pipeline-maven-plugin/target/pipeline-maven.jar</file>
      </artifact>
    </attachedArtifacts>
  </ExecutionEvent>
     */
    @Nonnull
    public List<MavenArtifact> listAttachedArtifacts(Element mavenSpyLogs) {
        List<MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : XmlUtils.getExecutionEvents(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "project");
            MavenArtifact projectArtifact = XmlUtils.newMavenArtifact(projectElt);

            Element attachedArtifactsParentElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "attachedArtifacts");
            List<Element> attachedArtifactsElts = XmlUtils.getChildrenElements(attachedArtifactsParentElt, "artifact");
            for (Element attachedArtifactElt : attachedArtifactsElts) {
                MavenArtifact attachedMavenArtifact = XmlUtils.newMavenArtifact(attachedArtifactElt);

                Element fileElt = XmlUtils.getUniqueChildElementOrNull(attachedArtifactElt, "file");
                if (fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                    LOGGER.log(Level.WARNING, "Project " + projectArtifact + ", no associated file found for attached artifact " + attachedMavenArtifact + " in " + XmlUtils.toString(attachedArtifactElt));
                }
                attachedMavenArtifact.file = StringUtils.trim(fileElt.getTextContent());
                result.add(attachedMavenArtifact);
            }


        }
        return result;
    }

    public static class MavenArtifact {
        public String groupId, artifactId, version, type, classifier;
        public String file;

        String getFileName() {
            return artifactId + "-" + version + ((classifier == null || classifier.isEmpty()) ? "" : "-" + classifier) + "." + type;
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
