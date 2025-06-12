package org.jenkinsci.plugins.pipeline.maven.publishers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.coverage.metrics.steps.CoverageStep;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.prism.SourceCodeDirectory;
import io.jenkins.plugins.prism.SourceCodeRetention;
import java.io.File;
import java.io.IOException;
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
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

public class CoveragePublisher extends MavenPublisher {

    private static final long serialVersionUID = -538821400471248829L;

    private static final Logger LOGGER = Logger.getLogger(CoveragePublisher.class.getName());

    private static final String JACOCO_GROUP_ID = "org.jacoco";
    private static final String JACOCO_ID = "jacoco-maven-plugin";
    private static final String REPORT_GOAL = "report";

    private String extraPattern = StringUtils.EMPTY;
    private SourceCodeRetention sourceCodeRetention = SourceCodeRetention.MODIFIED;

    @DataBoundConstructor
    public CoveragePublisher() {}

    @DataBoundSetter
    public void setExtraPattern(final String extraPattern) {
        this.extraPattern = extraPattern;
    }

    @CheckForNull
    public String getExtraPattern() {
        return extraPattern;
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

        List<Element> jacocoReportEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, JACOCO_GROUP_ID, JACOCO_ID, REPORT_GOAL, "MojoSucceeded", "MojoFailed");

        if (jacocoReportEvents.isEmpty()) {
            LOGGER.log(Level.FINE, "No " + JACOCO_GROUP_ID + ":" + JACOCO_ID + ":" + REPORT_GOAL + " execution found");
            return;
        }

        try {
            Class.forName("io.jenkins.plugins.coverage.metrics.steps.CoverageStep");
        } catch (ClassNotFoundException e) {
            listener.getLogger().print("[withMaven] Jenkins ");
            listener.hyperlink("https://plugins.jenkins.io/coverage/", "Coverage Plugin");
            listener.getLogger()
                    .println(
                            " not found, don't display org.jacoco:jacoco-maven-plugin:prepare-agent[-integration] results in pipeline screen.");
            return;
        }

        CoverageTool tool = buildJacocoTool(context, jacocoReportEvents);

        CoverageStep step = new CoverageStep();
        step.setTools(List.of(tool));
        step.setSourceCodeRetention(getSourceCodeRetention());
        step.setSourceDirectories(jacocoReportEvents.stream()
                .map(this::toSourceDirectory)
                .filter(Objects::nonNull)
                .toList());

        try {
            step.start(context).start();
        } catch (Exception e) {
            listener.error("[withMaven] coveragePublisher - exception archiving JaCoCo results: " + e);
            LOGGER.log(Level.WARNING, "Exception processing JaCoCo results", e);
            throw new MavenPipelinePublisherException("coveragePublisher", "archiving JaCoCo results", e);
        }
    }

    private CoverageTool buildJacocoTool(StepContext context, List<Element> events)
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
            String outputDirectory = outputDirectoryElt.getTextContent().trim();
            if (outputDirectory.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger()
                            .println("[withMaven] '${project.build.directory}' found for <project> in "
                                    + XmlUtils.toString(event));
                    continue;
                }
                outputDirectory = outputDirectory.replace("${project.build.directory}", projectBuildDirectory);

            } else if (outputDirectory.contains("${project.reporting.outputDirectory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger()
                            .println("[withMaven] '${project.reporting.outputDirectory}' found for <project> in "
                                    + XmlUtils.toString(event));
                    continue;
                }
                outputDirectory = outputDirectory.replace(
                        "${project.reporting.outputDirectory}", projectBuildDirectory + File.separator + "site");

            } else if (outputDirectory.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger()
                            .println("[withMaven] '${basedir}' found for <project> in " + XmlUtils.toString(event));
                    continue;
                }
                outputDirectory = outputDirectory.replace("${basedir}", baseDir);
            }

            String resultFile = outputDirectory + File.separator + "jacoco.xml";
            patterns.add(XmlUtils.getPathInWorkspace(resultFile, workspace));

            listener.getLogger()
                    .println("[withMaven] coveragePublisher - Archive JaCoCo analysis results for Maven artifact "
                            + mavenArtifact.toString() + " generated by " + pluginInvocation + ", resultFile: "
                            + resultFile);
        }

        StringBuilder patternsAsString = new StringBuilder();
        patternsAsString.append(patterns.stream().collect(Collectors.joining(",")));
        if (StringUtils.isNotBlank(getExtraPattern())) {
            patternsAsString.append(",").append(getExtraPattern());
        }

        CoverageTool tool = new CoverageTool();
        tool.setParser(Parser.JACOCO);
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
    @Extension
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
