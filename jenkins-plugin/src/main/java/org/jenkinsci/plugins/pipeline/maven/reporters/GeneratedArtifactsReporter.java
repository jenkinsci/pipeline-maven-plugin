package org.jenkinsci.plugins.pipeline.maven.reporters;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.tasks.Fingerprinter;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.ResultsReporter;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GeneratedArtifactsReporter implements ResultsReporter{
    private static final Logger LOGGER = Logger.getLogger(MavenSpyLogProcessor.class.getName());

    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {

        Run run = context.get(Run.class);
        ArtifactManager artifactManager = run.pickArtifactManager();
        Launcher launcher = context.get(Launcher.class);
        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }
        FilePath workspace = context.get(FilePath.class);
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        List<MavenSpyLogProcessor.MavenArtifact> mavenArtifacts = listArtifacts(mavenSpyLogsElt);
        List<MavenSpyLogProcessor.MavenArtifact> attachedMavenArtifacts = listAttachedArtifacts(mavenSpyLogsElt);
        List<MavenSpyLogProcessor.MavenArtifact> join = new ArrayList<>();
        join.addAll(mavenArtifacts);
        join.addAll(attachedMavenArtifacts);


        Map<String, String> artifactsToArchive = new HashMap<>(); // artifactPathInArchiveZone -> artifactPathInWorkspace
        Map<String, String> artifactsToFingerPrint = new HashMap<>(); // artifactPathInArchiveZone -> artifactMd5
        for (MavenSpyLogProcessor.MavenArtifact mavenArtifact : join) {
            try {
                if (StringUtils.isEmpty(mavenArtifact.file)) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        listener.getLogger().println("[withMaven] Can't archive maven artifact with no file attached: " + mavenArtifact);
                    }
                    continue;
                } else if (!(mavenArtifact.file.endsWith("." + mavenArtifact.extension))) {
                    FilePath mavenGeneratedArtifact = workspace.child(XmlUtils.getPathInWorkspace(mavenArtifact.file, workspace));
                    if (mavenGeneratedArtifact.isDirectory()) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Skip archiving for generated maven artifact of type directory (it's likely to be target/classes, see JENKINS-43714) " + mavenArtifact);
                        }
                        continue;
                    }
                }

                String artifactPathInArchiveZone =
                        mavenArtifact.groupId.replace(".", fileSeparatorOnAgent) + fileSeparatorOnAgent +
                                mavenArtifact.artifactId + fileSeparatorOnAgent +
                                mavenArtifact.version + fileSeparatorOnAgent +
                                mavenArtifact.getFileName();

                String artifactPathInWorkspace = XmlUtils.getPathInWorkspace(mavenArtifact.file, workspace);

                if (StringUtils.isEmpty(artifactPathInWorkspace)) {
                    listener.error("[withMaven] Invalid path in the workspace (" + workspace.getRemote() + ") for artifact " + mavenArtifact);
                } else {
                    FilePath artifactFilePath = new FilePath(workspace, artifactPathInWorkspace);
                    if (artifactFilePath.exists()) {
                        // the subsequent call to digest could test the existence but we don't want to prematurely optimize performances
                        listener.getLogger().println("[withMaven] Archive artifact " + artifactPathInWorkspace + " under " + artifactPathInArchiveZone);
                        artifactsToArchive.put(artifactPathInArchiveZone, artifactPathInWorkspace);
                        String artifactDigest = artifactFilePath.digest();
                        artifactsToFingerPrint.put(artifactPathInArchiveZone, artifactDigest);
                    } else {
                        listener.getLogger().println("[withMaven] FAILURE to archive " + artifactPathInWorkspace + " under " + artifactPathInArchiveZone + ", file not found");
                    }
                }
            } catch (IOException | RuntimeException e) {
                listener.error("[withMaven] WARNING: Exception archiving and fingerprinting " + mavenArtifact + ", skip archiving of the artifacts");
                e.printStackTrace(listener.getLogger());
                listener.getLogger().flush();
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] Archive and fingerprint " + artifactsToArchive);
        }

        // ARCHIVE GENERATED MAVEN ARTIFACT
        // see org.jenkinsci.plugins.workflow.steps.ArtifactArchiverStepExecution#run
        try {
            artifactManager.archive(workspace, launcher, new BuildListenerAdapter(listener), artifactsToArchive);
        } catch (IOException e) {
            throw new IOException("Exception archiving " + artifactsToArchive, e);
        } catch (RuntimeException e) {
            throw new RuntimeException("Exception archiving " + artifactsToArchive, e);
        }

        // FINGERPRINT GENERATED MAVEN ARTIFACT
        FingerprintMap fingerprintMap = Jenkins.getInstance().getFingerprintMap();
        for (Map.Entry<String, String> artifactToFingerprint : artifactsToFingerPrint.entrySet()) {
            String artifactPathInArchiveZone = artifactToFingerprint.getKey();
            String artifactMd5 = artifactToFingerprint.getValue();
            fingerprintMap.getOrCreate(run, artifactPathInArchiveZone, artifactMd5);
        }

        // add action
        Fingerprinter.FingerprintAction fingerprintAction = run.getAction(Fingerprinter.FingerprintAction.class);
        if (fingerprintAction == null) {
            run.addAction(new Fingerprinter.FingerprintAction(run, artifactsToFingerPrint));
        } else {
            fingerprintAction.add(artifactsToFingerPrint);
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
     * @return list of {@link MavenSpyLogProcessor.MavenArtifact}
     */
    /*

    <artifact artifactId="demo-pom" groupId="com.example" id="com.example:demo-pom:pom:0.0.1-SNAPSHOT" type="pom" version="0.0.1-SNAPSHOT">
      <file/>
    </artifact>
     */
    @Nonnull
    public List<MavenSpyLogProcessor.MavenArtifact> listArtifacts(Element mavenSpyLogs) {

        List<MavenSpyLogProcessor.MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : XmlUtils.getExecutionEvents(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "project");
            MavenSpyLogProcessor.MavenArtifact projectArtifact = XmlUtils.newMavenArtifact(projectElt);

            MavenSpyLogProcessor.MavenArtifact pomArtifact = new MavenSpyLogProcessor.MavenArtifact();
            pomArtifact.groupId = projectArtifact.groupId;
            pomArtifact.artifactId = projectArtifact.artifactId;
            pomArtifact.version = projectArtifact.version;
            pomArtifact.type = "pom";
            pomArtifact.extension = "pom";
            pomArtifact.file = projectElt.getAttribute("file");

            result.add(pomArtifact);

            Element artifactElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "artifact");
            MavenSpyLogProcessor.MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(artifactElt);
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
    public List<MavenSpyLogProcessor.MavenArtifact> listAttachedArtifacts(Element mavenSpyLogs) {
        List<MavenSpyLogProcessor.MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : XmlUtils.getExecutionEvents(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "project");
            MavenSpyLogProcessor.MavenArtifact projectArtifact = XmlUtils.newMavenArtifact(projectElt);

            Element attachedArtifactsParentElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "attachedArtifacts");
            List<Element> attachedArtifactsElts = XmlUtils.getChildrenElements(attachedArtifactsParentElt, "artifact");
            for (Element attachedArtifactElt : attachedArtifactsElts) {
                MavenSpyLogProcessor.MavenArtifact attachedMavenArtifact = XmlUtils.newMavenArtifact(attachedArtifactElt);

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
}
