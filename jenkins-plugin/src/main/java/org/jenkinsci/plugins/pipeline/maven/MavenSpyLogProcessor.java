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
import hudson.tasks.Fingerprinter;
import jenkins.model.ArtifactManager;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
                    String workspaceRemote = workspace.getRemote();
                    if(!workspaceRemote.endsWith("/")) {
                        workspaceRemote = workspaceRemote + "/";
                    }
                    String artifactPathInWorkspace = StringUtils.substringAfter(mavenArtifact.file, workspaceRemote);

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

            } catch (SAXException e) {
                listener.error("Exception parsing maven spy logs " + mavenSpyLogs + ", ignore file");
                e.printStackTrace(listener.getLogger());
            }

        }
    }

    /*
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-01-31 00:15:42.255">
    <project artifactIdId="pipeline-maven" groupId="org.jenkins-ci.plugins" name="Pipeline Maven Integration Plugin" version="0.6-SNAPSHOT"/>
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
    @Nonnull
    public List<MavenArtifact> listArtifacts(Element mavenSpyLogs) {

        List<MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : getExecutionEvent(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = getUniqueChildElement(projectSucceededElt, "project");
            MavenArtifact projectArtifact = newMavenArtifact(projectElt);

            Element artifactElt = getUniqueChildElement(projectSucceededElt, "artifact");
            MavenArtifact mavenArtifact = newMavenArtifact(artifactElt);

            Element fileElt = getUniqueChildElementOrNull(artifactElt, "file");
            if (fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                LOGGER.log(Level.WARNING, "Project " + projectArtifact + ":  no associated file found for " + mavenArtifact + " in " + toString(artifactElt));
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
    <project artifactIdId="pipeline-maven" groupId="org.jenkins-ci.plugins" name="Pipeline Maven Integration Plugin" version="0.6-SNAPSHOT"/>
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

        for (Element projectSucceededElt : getExecutionEvent(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = getUniqueChildElement(projectSucceededElt, "project");
            MavenArtifact projectArtifact = newMavenArtifact(projectElt);

            Element attachedArtifactsParentElt = getUniqueChildElement(projectSucceededElt, "attachedArtifacts");
            List<Element> attachedArtifactsElts = getChildrenElements(attachedArtifactsParentElt, "artifact");
            for (Element attachedArtifactElt : attachedArtifactsElts) {
                MavenArtifact attachedMavenArtifact = newMavenArtifact(attachedArtifactElt);

                Element fileElt = getUniqueChildElementOrNull(attachedArtifactElt, "file");
                if (fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                    LOGGER.log(Level.WARNING, "Project " + projectArtifact + ", no associated file found for attached artifact " + attachedMavenArtifact + " in " + toString(attachedArtifactElt));
                }
                attachedMavenArtifact.file = StringUtils.trim(fileElt.getTextContent());
                result.add(attachedMavenArtifact);
            }


        }
        return result;
    }


    public MavenArtifact newMavenArtifact(Element artifactElt) {
        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.groupId = artifactElt.getAttribute("groupId");
        mavenArtifact.artifactId = artifactElt.getAttribute("artifactId");
        mavenArtifact.version = artifactElt.getAttribute("version");
        mavenArtifact.type = artifactElt.getAttribute("type");
        mavenArtifact.classifier = artifactElt.hasAttribute("classifier") ? artifactElt.getAttribute("classifier") : null;
        return mavenArtifact;
    }

    @Nonnull
    public Element getUniqueChildElement(@Nonnull Element element, @Nonnull String childElementName) {
        Element child = getUniqueChildElementOrNull(element, childElementName);
        if (child == null) {
            throw new IllegalStateException("No <" + childElementName + "> element found");
        }
        return child;
    }

    @Nullable
    public Element getUniqueChildElementOrNull(@Nonnull Element element, @Nonnull String childElementName) {
        List<Element> childElts = getChildrenElements(element, childElementName);
        if (childElts.size() == 0) {
            return null;
        } else if (childElts.size() > 1) {
            throw new IllegalStateException("More than 1 (" + childElts.size() + ") elements <" + childElementName + "> found in " + toString(element));
        }

        return childElts.get(0);
    }

    @Nonnull
    public List<Element> getChildrenElements(@Nonnull Element element, @Nonnull String childElementName) {
        NodeList childElts = element.getChildNodes();
        List<Element> result = new ArrayList<>();

        for (int i = 0; i < childElts.getLength(); i++) {
            Node node = childElts.item(i);
            if (node instanceof Element && node.getNodeName().equals(childElementName)) {
                result.add((Element) node);
            }
        }

        return result;
    }

    @Nonnull
    public String toString(@Nullable Node node) {
        try {
            StringWriter out = new StringWriter();
            Transformer identityTransformer = TransformerFactory.newInstance().newTransformer();
            identityTransformer.transform(new DOMSource(node), new StreamResult(out));
            return out.toString();
        } catch (TransformerException e) {
            LOGGER.log(Level.WARNING, "Exception dumping node " + node, e);
            return e.toString();
        }
    }

    @Nonnull
    public List<Element> getExecutionEvent(@Nonnull Element mavenSpyLogs, String expectedType) {

        List<Element> result = new ArrayList<>();
        for (Element element : getChildrenElements(mavenSpyLogs, "ExecutionEvent")) {
            if (element.getAttribute("type").equals(expectedType)) {
                result.add(element);
            }
        }
        return result;
    }

    static class MavenArtifact {
        String groupId, artifactId, version, type, classifier;
        String file;

        String getFileName(){
            return artifactId + "-" + version + ((classifier == null || classifier.isEmpty())? "" : "-" + classifier) + "." + type;
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
}
