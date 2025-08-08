package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.springframework.util.ReflectionUtils.findMethod;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.ReflectionUtils.makeAccessible;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.coverage.metrics.steps.CoverageStep;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.prism.SourceCodeDirectory;
import io.jenkins.plugins.prism.SourceCodeRetention;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

public class CoveragePublisher extends MavenPublisher {

    private static final long serialVersionUID = -538821400471248829L;

    private static final Logger LOGGER = Logger.getLogger(CoveragePublisher.class.getName());

    private static final String COBERTURA_GROUP_ID = "org.codehaus.mojo";
    private static final String COBERTURA_ID = "cobertura-maven-plugin";
    private static final String COBERTURA_REPORT_GOAL = "cobertura";

    private static final String JACOCO_GROUP_ID = "org.jacoco";
    private static final String JACOCO_ID = "jacoco-maven-plugin";
    private static final String JACOCO_REPORT_GOAL = "report";

    private String coberturaExtraPattern = StringUtils.EMPTY;
    private String jacocoExtraPattern = StringUtils.EMPTY;
    private SourceCodeRetention sourceCodeRetention = SourceCodeRetention.MODIFIED;

    @DataBoundConstructor
    public CoveragePublisher() {}

    @DataBoundSetter
    public void setCoberturaExtraPattern(final String coberturaExtraPattern) {
        this.coberturaExtraPattern = coberturaExtraPattern;
    }

    @CheckForNull
    public String getCoberturaExtraPattern() {
        return coberturaExtraPattern;
    }

    @DataBoundSetter
    public void setJacocoExtraPattern(final String jacocoExtraPattern) {
        this.jacocoExtraPattern = jacocoExtraPattern;
    }

    @CheckForNull
    public String getJacocoExtraPattern() {
        return jacocoExtraPattern;
    }

    @DataBoundSetter
    public void setSourceCodeRetention(final SourceCodeRetention sourceCodeRetention) {
        this.sourceCodeRetention = sourceCodeRetention;
    }

    public SourceCodeRetention getSourceCodeRetention() {
        return sourceCodeRetention;
    }

    @Override
    public void process(StepContext context, Element mavenSpyLogsElt) throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);

        List<Element> coberturaReportEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt,
                COBERTURA_GROUP_ID,
                COBERTURA_ID,
                COBERTURA_REPORT_GOAL,
                "MojoSucceeded",
                "MojoFailed");
        List<Element> jacocoReportEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, JACOCO_GROUP_ID, JACOCO_ID, JACOCO_REPORT_GOAL, "MojoSucceeded", "MojoFailed");

        if (coberturaReportEvents.isEmpty() && jacocoReportEvents.isEmpty()) {
            LOGGER.log(
                    Level.FINE,
                    "No " + COBERTURA_GROUP_ID + ":" + COBERTURA_ID + ":" + COBERTURA_REPORT_GOAL + " or "
                            + JACOCO_GROUP_ID + ":" + JACOCO_ID + ":" + JACOCO_REPORT_GOAL + " execution found");
            return;
        }

        try {
            Class.forName("io.jenkins.plugins.coverage.metrics.steps.CoverageStep");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://plugins.jenkins.io/coverage/", "Coverage Plugin");
            listener.getLogger()
                    .println(" not found, don't display " + COBERTURA_GROUP_ID + ":" + COBERTURA_ID + ":"
                            + COBERTURA_REPORT_GOAL + " or " + JACOCO_GROUP_ID + ":" + JACOCO_ID + ":"
                            + JACOCO_REPORT_GOAL + " results in pipeline screen.");
            return;
        }

        List<CoverageTool> tools = new ArrayList<>();
        CoverageTool coberturaTool = buildCoberturaTool(context, coberturaReportEvents);
        if (coberturaTool != null) {
            tools.add(coberturaTool);
        }
        CoverageTool jacocoTool = buildJacocoTool(context, jacocoReportEvents);
        if (jacocoTool != null) {
            tools.add(jacocoTool);
        }

        CoverageStep step = new CoverageStep();
        step.setTools(tools);
        step.setSourceCodeRetention(getSourceCodeRetention());
        step.setSourceDirectories(jacocoReportEvents.stream()
                .map(this::toSourceDirectory)
                .filter(Objects::nonNull)
                .toList());

        try {
            StepExecution stepExecution = step.start(context);
            Method method = findMethod(stepExecution.getClass(), "run");
            if (method != null) {
                makeAccessible(method);
                invokeMethod(method, stepExecution);
            }
        } catch (Exception e) {
            listener.error("[withMaven] coveragePublisher - exception archiving coverage results: " + e);
            LOGGER.log(Level.WARNING, "Exception processing coverage results", e);
            throw new MavenPipelinePublisherException("coveragePublisher", "archiving coverage results", e);
        }
    }

    private CoverageTool buildCoberturaTool(StepContext context, List<Element> events)
            throws IOException, InterruptedException {
        return buildCoverageTool(
                "Cobertura", "coverage.xml", Parser.COBERTURA, getCoberturaExtraPattern(), context, events);
    }

    private CoverageTool buildJacocoTool(StepContext context, List<Element> events)
            throws IOException, InterruptedException {
        return buildCoverageTool("JaCoCo", "jacoco.xml", Parser.JACOCO, getJacocoExtraPattern(), context, events);
    }

    private CoverageTool buildCoverageTool(
            String name,
            String reportFilename,
            Parser parser,
            String extraPattern,
            StepContext context,
            List<Element> events)
            throws IOException, InterruptedException {
        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);

        List<String> patterns = new ArrayList<>();

        for (Element event : events) {

            Element buildElement = XmlUtils.getUniqueChildElementOrNull(event, "project", "build");
            if (buildElement == null) {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(
                            Level.FINE,
                            "Ignore execution event with missing 'build' child:" + XmlUtils.toString(event));
                continue;
            }

            Element pluginElt = XmlUtils.getUniqueChildElement(event, "plugin");
            Element outputDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "outputDirectory");
            Element projectElt = XmlUtils.getUniqueChildElement(event, "project");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

            if (outputDirectoryElt == null) {
                listener.getLogger()
                        .println("[withMaven] No <outputDirectory> element found for <plugin> in "
                                + XmlUtils.toString(event));
                continue;
            }
            String outputDirectory = XmlUtils.resolveMavenPlaceholders(outputDirectoryElt, projectElt);
            if (outputDirectory == null) {
                listener.getLogger()
                        .println(
                                "[withMaven] could not resolve placeholder '${project.build.directory}' or '${project.reporting.outputDirectory}' or '${basedir}' in "
                                        + XmlUtils.toString(event));
                continue;
            }

            String resultFile = outputDirectory + File.separator + reportFilename;
            patterns.add(XmlUtils.getPathInWorkspace(resultFile, workspace));

            listener.getLogger()
                    .println("[withMaven] coveragePublisher - Archive " + name + " analysis results for Maven artifact "
                            + mavenArtifact.toString() + " generated by " + pluginInvocation + ", resultFile: "
                            + resultFile);
        }

        if (patterns.isEmpty() && StringUtils.isBlank(extraPattern)) {
            return null;
        }

        StringBuilder patternsAsString = new StringBuilder();
        patternsAsString.append(patterns.stream().collect(Collectors.joining(",")));
        if (StringUtils.isNotBlank(extraPattern)) {
            if (!patterns.isEmpty()) {
                patternsAsString.append(",");
            }
            patternsAsString.append(extraPattern);
        }

        CoverageTool tool = new CoverageTool();
        tool.setParser(parser);
        tool.setPattern(patternsAsString.toString());
        return tool;
    }

    private SourceCodeDirectory toSourceDirectory(Element e) {
        Element buildElement = XmlUtils.getUniqueChildElementOrNull(e, "project", "build");
        if (buildElement == null) {
            return null;
        }
        return new SourceCodeDirectory(buildElement.getAttribute("sourceDirectory"));
    }

    @Symbol("coveragePublisher")
    @OptionalExtension(requirePlugins = "coverage")
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_coverage_description();
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-coverage-results";
        }
    }
}
