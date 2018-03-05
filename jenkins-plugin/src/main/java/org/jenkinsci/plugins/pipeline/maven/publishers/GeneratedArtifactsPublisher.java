package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
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
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GeneratedArtifactsPublisher extends MavenPublisher {

    private static final Logger LOGGER = Logger.getLogger(GeneratedArtifactsPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GeneratedArtifactsPublisher() {

    }

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

        List<MavenArtifact> join = XmlUtils.listGeneratedArtifacts(mavenSpyLogsElt, true);

        Map<String, String> artifactsToArchive = new HashMap<>(); // artifactPathInArchiveZone -> artifactPathInWorkspace
        Map<String, String> artifactsToFingerPrint = new HashMap<>(); // artifactPathInArchiveZone -> artifactMd5
        for (MavenArtifact mavenArtifact : join) {
            try {
                if (StringUtils.isEmpty(mavenArtifact.file)) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] artifactsPublisher - Can't archive maven artifact with no file attached: " + mavenArtifact);
                    }
                    continue;
                } else if (!(mavenArtifact.file.endsWith("." + mavenArtifact.extension))) {
                    FilePath mavenGeneratedArtifact = workspace.child(XmlUtils.getPathInWorkspace(mavenArtifact.file, workspace));
                    if (mavenGeneratedArtifact.isDirectory()) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] artifactsPublisher - Skip archiving for generated maven artifact of type directory (it's likely to be target/classes, see JENKINS-43714) " + mavenArtifact);
                        }
                        continue;
                    }
                }

                String artifactPathInArchiveZone =
                        mavenArtifact.groupId.replace(".", fileSeparatorOnAgent) + fileSeparatorOnAgent +
                                mavenArtifact.artifactId + fileSeparatorOnAgent +
                                mavenArtifact.baseVersion + fileSeparatorOnAgent +
                                mavenArtifact.getFileNameWithBaseVersion();

                String artifactPathInWorkspace = XmlUtils.getPathInWorkspace(mavenArtifact.file, workspace);
                if (StringUtils.isEmpty(artifactPathInWorkspace)) {
                    listener.error("[withMaven] artifactsPublisher - Invalid path in the workspace (" + workspace.getRemote() + ") for artifact " + mavenArtifact);
                } else if (Objects.equals(artifactPathInArchiveZone, mavenArtifact.file)) { // troubleshoot JENKINS-44088
                    listener.error("[withMaven] artifactsPublisher - Failed to relativize '" + mavenArtifact.file + "' in workspace '" + workspace.getRemote() + "' with file separator '" + fileSeparatorOnAgent + "'");
                } else {
                    FilePath artifactFilePath = new FilePath(workspace, artifactPathInWorkspace);
                    if (artifactFilePath.exists()) {
                        // the subsequent call to digest could test the existence but we don't want to prematurely optimize performances
                        listener.getLogger().println("[withMaven] artifactsPublisher - Archive artifact " + artifactPathInWorkspace + " under " + artifactPathInArchiveZone);
                        artifactsToArchive.put(artifactPathInArchiveZone, artifactPathInWorkspace);
                        String artifactDigest = artifactFilePath.digest();
                        artifactsToFingerPrint.put(artifactPathInArchiveZone, artifactDigest);
                    } else {
                        listener.getLogger().println("[withMaven] artifactsPublisher - FAILURE to archive " + artifactPathInWorkspace + " under " + artifactPathInArchiveZone + ", file not found in workspace " + workspace);
                    }
                }
            } catch (IOException | RuntimeException e) {
                listener.error("[withMaven] artifactsPublisher - WARNING: Exception archiving and fingerprinting " + mavenArtifact + ", skip archiving of the artifacts");
                e.printStackTrace(listener.getLogger());
                listener.getLogger().flush();
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] artifactsPublisher - Archive and fingerprint artifacts " + artifactsToArchive + " located in workspace " + workspace.getRemote());
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
            fingerprintMap.getOrCreate(run, artifactPathInArchiveZone, artifactMd5).addFor(run);
        }

        // add action
        Fingerprinter.FingerprintAction fingerprintAction = run.getAction(Fingerprinter.FingerprintAction.class);
        if (fingerprintAction == null) {
            run.addAction(new Fingerprinter.FingerprintAction(run, artifactsToFingerPrint));
        } else {
            fingerprintAction.add(artifactsToFingerPrint);
        }
    }

    @Symbol("artifactsPublisher")
    @Extension public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Generated Artifacts Publisher";
        }

        @Override
        public int ordinal() {
            return 1;
        }


        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-archive-generated-artifacts";
        }
    }
}
