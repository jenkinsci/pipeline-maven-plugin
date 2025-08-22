package org.jenkinsci.plugins.pipeline.maven.publishers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.IssuesRecorder;
import io.jenkins.plugins.analysis.warnings.SpotBugs;
import io.jenkins.plugins.util.PipelineResultHandler;
import io.jenkins.plugins.util.ResultHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WarningsNgPublisher extends MavenPublisher {

    private static final Logger LOGGER = Logger.getLogger(WarningsNgPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    private static final String SPOTBUGS_GROUP_ID = "com.github.spotbugs";
    private static final String SPOTBUGS_ID = "spotbugs-maven-plugin";
    private static final String SPOTBUGS_GOAL = "spotbugs";

    @DataBoundConstructor
    public WarningsNgPublisher() {}

    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        if (listener == null) {
            LOGGER.warning("TaskListener is NULL, default to stderr");
            listener = new StreamBuildListener((OutputStream) System.err);
        }

        try {
            Class.forName("io.jenkins.plugins.analysis.core.steps.IssuesRecorder");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink(
                    "https://wiki.jenkins.io/display/JENKINS/Warnings+Next+Generation+Plugin", "Warnings Plugin");
            listener.getLogger().print(" not found, do not display static analysis reports in pipeline screen.");
            return;
        }

        // TODO: MavenConsole
        // TODO: Java
        // TODO: JavaDoc
        // TODO: Spotbugs
        // TODO: FindBugs
        // TODO: Open tasks
        // TODO: PMD
        // TODO: CPD
        // TODO: Checkstyle

        FilePath workspace = context.get(FilePath.class);

        List<Element> spotbugsEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, SPOTBUGS_GROUP_ID, SPOTBUGS_ID, SPOTBUGS_GOAL, "MojoSucceeded", "MojoFailed");

        if (spotbugsEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsNgPublisher - No " + SPOTBUGS_GOAL + " execution found");
            }
            return;
        }

        List<Tool> tools = new ArrayList<>();
        for (Element spotBugsTestEvent : spotbugsEvents) {
            String spotBugsEventType = spotBugsTestEvent.getAttribute("type");
            if (!spotBugsEventType.equals("MojoSucceeded") && !spotBugsEventType.equals("MojoFailed")) {
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(spotBugsTestEvent, "plugin");
            Element xmlOutputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "xmlOutputDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(spotBugsTestEvent, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (xmlOutputDirectoryElt == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] warningsNgPublisher - No <xmlOutputDirectoryElt> element found for <plugin> in "
                                        + XmlUtils.toString(spotBugsTestEvent));
                continue;
            }
            String xmlOutputDirectory = xmlOutputDirectoryElt.getTextContent().trim();
            if (xmlOutputDirectory.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger()
                            .println(
                                    "[withMaven] warningsNgPublisher - '${project.build.directory}' found for <project> in "
                                            + XmlUtils.toString(spotBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${project.build.directory}", projectBuildDirectory);

            } else if (xmlOutputDirectory.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger()
                            .println("[withMaven] warningsNgPublisher - '${basedir}' found for <project> in "
                                    + XmlUtils.toString(spotBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${basedir}", baseDir);
            }

            xmlOutputDirectory = XmlUtils.getPathInWorkspace(xmlOutputDirectory, workspace);

            String spotBugsResultsFile = xmlOutputDirectory + "/spotbugsXml.xml";

            tools.add(spotBugs(mavenArtifact, pluginInvocation, spotBugsResultsFile));
        }

        perform(tools, context, workspace, listener, "spotbugs");
    }

    private void perform(
            List<Tool> tools, StepContext context, FilePath workspace, TaskListener listener, String kind) {
        try {
            Run<?, ?> run = context.get(Run.class);
            IssuesRecorder issuesRecorder = new IssuesRecorder();
            issuesRecorder.setTools(tools);
            Method method = IssuesRecorder.class.getDeclaredMethod(
                    "perform", Run.class, FilePath.class, TaskListener.class, ResultHandler.class);
            method.setAccessible(true);
            method.invoke(
                    issuesRecorder,
                    run,
                    workspace,
                    listener,
                    new PipelineResultHandler(run, context.get(FlowNode.class)));
        } catch (Exception ex) {
            throw new MavenPipelinePublisherException("warningsNgPublisher", "archiving " + kind + " reports", ex);
        }
    }

    private SpotBugs spotBugs(
            MavenArtifact mavenArtifact,
            MavenSpyLogProcessor.PluginInvocation pluginInvocation,
            String spotBugsReportFile) {
        SpotBugs spotBugs = new SpotBugs();
        spotBugs.setId(spotBugs.getDescriptor().getId() + "_" + mavenArtifact.getId() + "_" + pluginInvocation.getId());
        spotBugs.setName(
                spotBugs.getDescriptor().getName() + " " + mavenArtifact.getId() + " " + pluginInvocation.getId());
        spotBugs.setPattern(spotBugsReportFile);
        return spotBugs;
    }

    @Symbol("warningsNgPublisher")
    @OptionalExtension(requirePlugins = "warnings-ng")
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_warningsng_description();
        }

        @Override
        public int ordinal() {
            return 10;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-warningsng";
        }
    }
}
