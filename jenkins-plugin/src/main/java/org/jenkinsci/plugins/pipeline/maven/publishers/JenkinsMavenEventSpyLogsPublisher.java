package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Primarily for debugging purpose, archive the Maven build logs
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JenkinsMavenEventSpyLogsPublisher implements Serializable {

    private static final long serialVersionUID = 1L;

    public void process(@Nonnull StepContext context, @Nonnull FilePath mavenSpyLogs) throws IOException, InterruptedException {

        Run run = context.get(Run.class);
        ArtifactManager artifactManager = run.pickArtifactManager();
        Launcher launcher = context.get(Launcher.class);
        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);

        // ARCHIVE MAVEN BUILD LOGS
        FilePath tmpFile = new FilePath(workspace, "." + mavenSpyLogs.getName());
        try {
            mavenSpyLogs.copyTo(tmpFile);
            listener.getLogger().println("[withMaven] Archive " + mavenSpyLogs.getRemote() + " as " + mavenSpyLogs.getName());
            // filePathInArchiveZone -> filePathInWorkspace
            Map<String, String> mavenBuildLogs = Collections.singletonMap(mavenSpyLogs.getName(), tmpFile.getName());
            artifactManager.archive(workspace, launcher, new BuildListenerAdapter(listener), mavenBuildLogs);
        } catch (Exception e) {
            PrintWriter errorWriter = listener.error("[withMaven] WARNING Exception archiving Maven build logs " + mavenSpyLogs + ", skip file. ");
            e.printStackTrace(errorWriter);
        } finally {
            boolean deleted = tmpFile.delete();
            if (!deleted) {
                listener.error("[withMaven] WARNING Failure to delete temporary file " + tmpFile);
            }
        }
    }
}
