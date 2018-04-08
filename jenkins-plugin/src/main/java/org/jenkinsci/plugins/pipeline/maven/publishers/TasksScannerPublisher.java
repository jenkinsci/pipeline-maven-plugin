package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 * @see hudson.plugins.tasks.TasksPublisher
 */
public class TasksScannerPublisher extends AbstractHealthAwarePublisher {
    private static final Logger LOGGER = Logger.getLogger(TasksScannerPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    /**
     * Coma separated high priority task identifiers
     *
     * @see hudson.plugins.tasks.TasksPublisher#getHigh()
     */
    private String highPriorityTaskIdentifiers = "";
    /**
     * @see hudson.plugins.tasks.TasksPublisher#getNormal()
     */
    private String normalPriorityTaskIdentifiers = "";
    /**
     * @see hudson.plugins.tasks.TasksPublisher#getLow()
     */
    private String lowPriorityTaskIdentifiers = "";
    /**
     * @see hudson.plugins.tasks.TasksPublisher#getIgnoreCase()
     */
    private boolean ignoreCase = false;
    /**
     * @see hudson.plugins.tasks.TasksPublisher#getPattern()
     */
    private String pattern = "";
    /**
     * @see hudson.plugins.tasks.TasksPublisher#getExcludePattern()
     */
    private String excludePattern = "";

    /**
     * @see hudson.plugins.tasks.TasksPublisher#getAsRegexp()
     */
    private boolean asRegexp = false;

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
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);

            String sourceDirectory = buildElement.getAttribute("sourceDirectory");

            // JENKINS-44359
            if (Objects.equals(sourceDirectory, "${project.basedir}/src/main/java")) {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE, "Skip task scanning for " + XmlUtils.toString(executionEvent));
                continue;
            }

            String sourceDirectoryRelativePath = XmlUtils.getPathInWorkspace(sourceDirectory, workspace);

            if (workspace.child(sourceDirectoryRelativePath).exists()) {
                sourceDirectoriesPatterns.add(sourceDirectoryRelativePath + fileSeparatorOnAgent + "**" + fileSeparatorOnAgent + "*");
                listener.getLogger().println("[withMaven] openTasksPublisher - Scan Tasks for Maven artifact " + mavenArtifact.getId() + " in source directory " + sourceDirectoryRelativePath);
            } else {
                LOGGER.log(Level.FINE, "Skip task scanning for {0}, folder {1} does not exist", new Object[]{mavenArtifact, sourceDirectoryRelativePath});
            }
        }

        if (sourceDirectoriesPatterns.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger().println("[withMaven] openTasksPublisher - no folder to scan");
            }
            return;
        }

        // To avoid duplicates
        hudson.plugins.tasks.TasksResultAction tasksResult = run.getAction(hudson.plugins.tasks.TasksResultAction.class);
        if (tasksResult != null) {
            run.removeAction(tasksResult);
        }

        hudson.plugins.tasks.TasksPublisher tasksPublisher = new hudson.plugins.tasks.TasksPublisher();
        String pattern = StringUtils.isEmpty(this.pattern)? XmlUtils.join(sourceDirectoriesPatterns, ",") : this.pattern;
        tasksPublisher.setPattern(pattern);
        tasksPublisher.setExcludePattern(StringUtils.trimToNull(this.excludePattern));

        tasksPublisher.setHigh(StringUtils.defaultIfEmpty(this.highPriorityTaskIdentifiers, "FIXME"));
        tasksPublisher.setNormal(StringUtils.defaultIfEmpty(this.normalPriorityTaskIdentifiers, "TODO"));
        tasksPublisher.setLow(StringUtils.trimToNull(this.lowPriorityTaskIdentifiers));
        tasksPublisher.setIgnoreCase(this.ignoreCase);
        tasksPublisher.setAsRegexp(this.asRegexp);

        setHealthAwarePublisherAttributes(tasksPublisher);

        try {
            tasksPublisher.perform(run, workspace, launcher, listener);
        } catch (Exception e) {
            listener.error("[withMaven] openTasksPublisher - Silently ignore exception scanning tasks in " + pattern + ": " + e);
            LOGGER.log(Level.WARNING, "Exception scanning tasks in  " + pattern, e);
        }
    }

    public String getHighPriorityTaskIdentifiers() {
        return highPriorityTaskIdentifiers;
    }

    @DataBoundSetter
    public void setHighPriorityTaskIdentifiers(String highPriorityTaskIdentifiers) {
        this.highPriorityTaskIdentifiers = highPriorityTaskIdentifiers;
    }

    public String getNormalPriorityTaskIdentifiers() {
        return normalPriorityTaskIdentifiers;
    }

    @DataBoundSetter
    public void setNormalPriorityTaskIdentifiers(String normalPriorityTaskIdentifiers) {
        this.normalPriorityTaskIdentifiers = normalPriorityTaskIdentifiers;
    }

    public String getLowPriorityTaskIdentifiers() {
        return lowPriorityTaskIdentifiers;
    }

    @DataBoundSetter
    public void setLowPriorityTaskIdentifiers(String lowPriorityTaskIdentifiers) {
        this.lowPriorityTaskIdentifiers = lowPriorityTaskIdentifiers;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @DataBoundSetter
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public String getPattern() {
        return pattern;
    }

    @DataBoundSetter
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    @DataBoundSetter
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    public boolean isAsRegexp() {
        return asRegexp;
    }

    @DataBoundSetter
    public void setAsRegexp(boolean asRegexp) {
        this.asRegexp = asRegexp;
    }

    @Symbol("openTasksPublisher")
    @Extension
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Open Task Scanner Publisher";
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
