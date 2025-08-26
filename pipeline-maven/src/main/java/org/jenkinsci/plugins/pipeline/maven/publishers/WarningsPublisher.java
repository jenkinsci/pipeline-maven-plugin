package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.springframework.util.ReflectionUtils.findMethod;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.ReflectionUtils.makeAccessible;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.RecordIssuesStep;
import io.jenkins.plugins.analysis.warnings.Java;
import io.jenkins.plugins.analysis.warnings.JavaDoc;
import io.jenkins.plugins.analysis.warnings.MavenConsole;
import io.jenkins.plugins.analysis.warnings.SpotBugs;
import java.io.File;
import java.io.IOException;
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
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WarningsPublisher extends MavenPublisher {

    private static final Logger LOGGER = Logger.getLogger(WarningsPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    private static final String FINDBUGS_GROUP_ID = "org.codehaus.mojo";
    private static final String FINDBUGS_ID = "findbugs-maven-plugin";
    private static final String FINDBUGS_GOAL = "findbugs";

    private static final String SPOTBUGS_GROUP_ID = "com.github.spotbugs";
    private static final String SPOTBUGS_ID = "spotbugs-maven-plugin";
    private static final String SPOTBUGS_GOAL = "spotbugs";

    @DataBoundConstructor
    public WarningsPublisher() {}

    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);

        try {
            Class.forName("org.jenkinsci.plugins.workflow.steps.StepExecution");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink(
                    "https://wiki.jenkins.io/display/JENKINS/Warnings+Next+Generation+Plugin", "Warnings NG Plugin");
            listener.getLogger().print(" not found, do not display static analysis reports in pipeline screen.");
            return;
        }

        perform(List.of(maven(context)), context, listener, "Maven console");
        perform(java(context), context, listener, "Java and JavaDoc");
        List<Element> findbugsEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, FINDBUGS_GROUP_ID, FINDBUGS_ID, FINDBUGS_GOAL, "MojoSucceeded", "MojoFailed");
        if (findbugsEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + FINDBUGS_GROUP_ID + ":" + FINDBUGS_ID + ":"
                                + FINDBUGS_GOAL + " execution found");
            }
        } else {
            processBugs(findbugsEvents, "findbugs", "findbugsXml.xml", context, listener);
        }
        List<Element> spotbugsEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, SPOTBUGS_GROUP_ID, SPOTBUGS_ID, SPOTBUGS_GOAL, "MojoSucceeded", "MojoFailed");
        if (spotbugsEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + SPOTBUGS_GROUP_ID + ":" + SPOTBUGS_ID + ":"
                                + SPOTBUGS_GOAL + " execution found");
            }
        } else {
            processBugs(spotbugsEvents, "spotbugs", "spotbugsXml.xml", context, listener);
        }
    }

    private void processBugs(
            List<Element> events, String kind, String reportFilename, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(event, "xmlOutputDirectory", reportFilename, workspace, listener);
            if (result != null) {
                tools.add(spotBugs(
                        context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, kind);
    }

    private void perform(List<Tool> tools, StepContext context, TaskListener listener, String kind) {

        if (tools == null || tools.isEmpty()) {
            return;
        }

        listener.getLogger().println("[withMaven] warningsPublisher - Processing " + kind + " warnings");
        RecordIssuesStep step = new RecordIssuesStep();
        step.setTools(tools);

        try {
            StepExecution stepExecution = step.start(context);
            Method method = findMethod(stepExecution.getClass(), "run");
            if (method != null) {
                makeAccessible(method);
                invokeMethod(method, stepExecution);
            } else {
                listener.error("[withMaven] warningsPublisher - error archiving " + kind
                        + " warnings results: RecordIssuesStep.Execution.run() method not found");
                LOGGER.log(
                        Level.WARNING,
                        "Error processing " + kind
                                + " warnings results: RecordIssuesStep.Execution.run() method not found");
                throw new MavenPipelinePublisherException(
                        "warningsPublisher",
                        "archiving " + kind + " warnings results",
                        new RuntimeException("RecordIssuesStep.Execution.run() method not found"));
            }
        } catch (Exception e) {
            listener.error("[withMaven] warningsPublisher - exception archiving " + kind + " warnings results: " + e);
            LOGGER.log(Level.WARNING, "Exception processing " + kind + " warnings results", e);
            throw new MavenPipelinePublisherException(
                    "warningsPublisher", "archiving " + kind + " warnings results", e);
        }
    }

    private Tool maven(StepContext context) throws IOException, InterruptedException {
        MavenConsole tool = new MavenConsole();
        String name = computeName(tool, context);
        tool.setId(toId(name));
        tool.setName(name);
        return tool;
    }

    private List<Tool> java(StepContext context) throws IOException, InterruptedException {
        Java java = new Java();
        String name = computeName(java, context);
        java.setId(toId(name));
        java.setName(name);
        JavaDoc javadoc = new JavaDoc();
        name = computeName(javadoc, context);
        javadoc.setId(toId(name));
        javadoc.setName(name);
        return List.of(java, javadoc);
    }

    private Tool spotBugs(
            StepContext context,
            MavenArtifact mavenArtifact,
            MavenSpyLogProcessor.PluginInvocation pluginInvocation,
            String reportFile)
            throws IOException, InterruptedException {
        SpotBugs tool = new SpotBugs();
        String name = computeName(tool, context) + " " + mavenArtifact.getId() + " " + pluginInvocation.getId();
        tool.setId(toId(name));
        tool.setName(name);
        tool.setPattern(reportFile);
        return tool;
    }

    private ResultFile extractResultFile(
            Element event,
            String directoryAttributeName,
            String reportFilename,
            FilePath workspace,
            TaskListener listener) {
        String eventType = event.getAttribute("type");
        if (!eventType.equals("MojoSucceeded") && !eventType.equals("MojoFailed")) {
            return null;
        }

        Element pluginElt = XmlUtils.getUniqueChildElement(event, "plugin");
        Element outputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, directoryAttributeName);
        Element projectElt = XmlUtils.getUniqueChildElement(event, "project");
        MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
        MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

        if (outputDirectoryElt == null) {
            listener.getLogger()
                    .println("[withMaven] warningsPublisher - No <xmlOutputDirectoryElt> element found for <plugin> in "
                            + XmlUtils.toString(event));
            return null;
        }
        String outputDirectory = XmlUtils.resolveMavenPlaceholders(outputDirectoryElt, projectElt);
        if (outputDirectory == null) {
            listener.getLogger()
                    .println(
                            "[withMaven] could not resolve placeholder '${project.build.directory}' or '${project.reporting.outputDirectory}' or '${basedir}' in "
                                    + XmlUtils.toString(event));
            return null;
        }

        String resultFile = outputDirectory + File.separator + reportFilename;
        return new ResultFile(mavenArtifact, pluginInvocation, XmlUtils.getPathInWorkspace(resultFile, workspace));
    }

    private String computeName(Tool tool, StepContext context) throws IOException, InterruptedException {
        return tool.getDescriptor().getName() + " " + context.get(Run.class).toString() + " "
                + context.get(FlowNode.class).getId();
    }

    private String toId(String name) {
        return name.replaceAll("[^\\p{Alnum}-_.]", "_");
    }

    private static class ResultFile {

        private MavenArtifact mavenArtifact;
        private MavenSpyLogProcessor.PluginInvocation pluginInvocation;
        private String resultFile;

        public ResultFile(
                MavenArtifact mavenArtifact,
                MavenSpyLogProcessor.PluginInvocation pluginInvocation,
                String resultFile) {
            this.mavenArtifact = mavenArtifact;
            this.pluginInvocation = pluginInvocation;
            this.resultFile = resultFile;
        }

        public MavenArtifact getMavenArtifact() {
            return mavenArtifact;
        }

        public MavenSpyLogProcessor.PluginInvocation getPluginInvocation() {
            return pluginInvocation;
        }

        public String getResultFile() {
            return resultFile;
        }
    }

    @Symbol("warningsPublisher")
    @OptionalExtension(requirePlugins = "warnings-ng")
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_warnings_description();
        }

        @Override
        public int ordinal() {
            return 10;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-warnings";
        }
    }
}
