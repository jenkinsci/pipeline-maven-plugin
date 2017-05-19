package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.plugins.tasks.TasksPublisher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 * @see hudson.plugins.tasks.TasksPublisher
 */
public class TasksScannerPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(FindbugsAnalysisPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public TasksScannerPublisher() {

    }

    /*
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-03-08 21:03:33.564">
        <project baseDir="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy" file="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/pom.xml" groupId="org.jenkins-ci.plugins" name="Maven Spy for the Pipeline Maven Integration Plugin" artifactId="pipeline-maven-spy" version="2.0-beta-7-SNAPSHOT">
          <build sourceDirectory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/src/main/java" directory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/target"/>
        </project>
        ...
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
        final String fileSeparatorOnAgent = XmlUtils.getFileSeparatorOnRemote(workspace);

        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);


        try {
            Class.forName("hudson.plugins.tasks.TasksPublisher");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://wiki.jenkins-ci.org/display/JENKINS/Task+Scanner+Plugin", "Task Scanner Plugin");
            listener.getLogger().println(" not found, don't display results of source code scanning for 'TODO' and 'FIXME' in pipeline screen.");
            return;
        }

        List<String> sourceDirectoriesPatterns = new ArrayList<>();
        for (Element executionEvent : XmlUtils.getExecutionEvents(mavenSpyLogsElt, "ProjectSucceeded", "ProjectFailed")) {

            /*
            <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-03-08 21:03:33.564">
                <project baseDir="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy" file="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/pom.xml" groupId="org.jenkins-ci.plugins" name="Maven Spy for the Pipeline Maven Integration Plugin" artifactId="pipeline-maven-spy" version="2.0-beta-7-SNAPSHOT">
                  <build sourceDirectory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/src/main/java" directory="/Users/cleclerc/git/jenkins/pipeline-maven-plugin/maven-spy/target"/>
                </project>
                ...
            </ExecutionEvent>
             */
            Element buildElement = XmlUtils.getUniqueChildElementOrNull(executionEvent, "project", "build");
            if (buildElement == null) {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE, "Ignore execution event with missing 'build' child:" + XmlUtils.toString(executionEvent));
                continue;
            }
            Element projectElt = XmlUtils.getUniqueChildElement(executionEvent, "project");
            MavenSpyLogProcessor.MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);

            String sourceDirectory = buildElement.getAttribute("sourceDirectory");

            String sourceDirectoryRelativePath = XmlUtils.getPathInWorkspace(sourceDirectory, workspace);

            if (workspace.child(sourceDirectoryRelativePath).exists()) {
                sourceDirectoriesPatterns.add(sourceDirectoryRelativePath + fileSeparatorOnAgent + "**" + fileSeparatorOnAgent + "*");
                listener.getLogger().println("[withMaven] Scan Tasks for Maven artifact " + mavenArtifact.toString() + " in source directory " + sourceDirectoryRelativePath);
            } else {
                LOGGER.log(Level.FINE, "Skip task scanning for {0}, folder {1} does not exist", new Object[]{mavenArtifact, sourceDirectoryRelativePath});
            }
        }

        TasksPublisher tasksPublisher = new TasksPublisher();
        String pattern = XmlUtils.join(sourceDirectoriesPatterns, ",");
        tasksPublisher.setPattern(pattern);
        tasksPublisher.setHigh("FIXME");
        tasksPublisher.setNormal("TODO");

        try {
            tasksPublisher.perform(run, workspace, launcher, listener);
        } catch (Exception e) {
            listener.error("[withMaven] Silently ignore exception scanning tasks in " + pattern + ": " + e);
            LOGGER.log(Level.WARNING, "Exception scanning tasks in  " + pattern, e);
        }
    }

    @Symbol("openTasksPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Task Scanner Publisher";
        }

        @Override
        public int ordinal() {
            return 100;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-task-scanner";
        }
    }
}
