package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.springframework.util.ReflectionUtils.findMethod;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.ReflectionUtils.makeAccessible;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.RecordIssuesStep;
import io.jenkins.plugins.analysis.core.util.ModelValidation;
import io.jenkins.plugins.analysis.core.util.TrendChartType;
import io.jenkins.plugins.analysis.core.util.WarningsQualityGate;
import io.jenkins.plugins.analysis.core.util.WarningsQualityGate.QualityGateType;
import io.jenkins.plugins.analysis.warnings.Java;
import io.jenkins.plugins.analysis.warnings.JavaDoc;
import io.jenkins.plugins.analysis.warnings.MavenConsole;
import io.jenkins.plugins.analysis.warnings.SpotBugs;
import io.jenkins.plugins.analysis.warnings.tasks.OpenTasks;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.ValidationUtilities;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
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

        perform(List.of(new MavenConsole()), context, listener, "Maven console");
        perform(List.of(new Java(), new JavaDoc()), context, listener, "Java and JavaDoc");
        perform(List.of(taskScanner()), context, listener, "Open tasks");
        // TODO: PMD
        /*
        Map pmdArguments = [tool: pmdParser(pattern: '* * /target/* * /pmd.xml'),
        */
        // TODO: CPD
        /*
        Map cpdArguments = [tool: cpd(pattern: '* * /target/* * /cpd.xml'),
        */
        // TODO: Checkstyle
        /*
        Map checkstyleArguments = [tool: checkStyle(pattern: '* * /target/* * /checkstyle-result.xml'),
        */
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
                        .println("[withMaven] warningsPublisher - No " + SPOTBUGS_GROUP_ID + ":" + SPOTBUGS_ID + ":" + SPOTBUGS_GOAL
                                + " execution found");
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
            String spotBugsEventType = event.getAttribute("type");
            if (!spotBugsEventType.equals("MojoSucceeded") && !spotBugsEventType.equals("MojoFailed")) {
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(event, "plugin");
            Element xmlOutputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "xmlOutputDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(event, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (xmlOutputDirectoryElt == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] warningsPublisher - No <xmlOutputDirectoryElt> element found for <plugin> in "
                                        + XmlUtils.toString(event));
                continue;
            }
            String xmlOutputDirectory = XmlUtils.resolveMavenPlaceholders(xmlOutputDirectoryElt, projectElt);
            if (xmlOutputDirectory == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] could not resolve placeholder '${project.build.directory}' or '${project.reporting.outputDirectory}' or '${basedir}' in "
                                        + XmlUtils.toString(event));
                continue;
            }

            String resultFile = xmlOutputDirectory + File.separator + reportFilename;
            tools.add(spotBugs(mavenArtifact, pluginInvocation, XmlUtils.getPathInWorkspace(resultFile, workspace)));
        }

        perform(tools, context, listener, kind);
    }

    private void perform(List<Tool> tools, StepContext context, TaskListener listener, String kind) {

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

    private OpenTasks taskScanner() {
        OpenTasks scanner = new OpenTasks();
        scanner.setIncludePattern("**/*.java");
        scanner.setExcludePattern("**/target/**");
        return scanner;
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
