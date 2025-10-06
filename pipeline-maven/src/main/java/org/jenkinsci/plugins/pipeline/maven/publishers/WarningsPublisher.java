package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.springframework.util.ReflectionUtils.findMethod;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.ReflectionUtils.makeAccessible;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.analysis.core.filter.ExcludeFile;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.RecordIssuesStep;
import io.jenkins.plugins.analysis.core.util.ModelValidation;
import io.jenkins.plugins.analysis.core.util.TrendChartType;
import io.jenkins.plugins.analysis.core.util.WarningsQualityGate;
import io.jenkins.plugins.analysis.core.util.WarningsQualityGate.QualityGateType;
import io.jenkins.plugins.analysis.warnings.CheckStyle;
import io.jenkins.plugins.analysis.warnings.Cpd;
import io.jenkins.plugins.analysis.warnings.Java;
import io.jenkins.plugins.analysis.warnings.JavaDoc;
import io.jenkins.plugins.analysis.warnings.MavenConsole;
import io.jenkins.plugins.analysis.warnings.Pmd;
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
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
import org.jenkinsci.plugins.workflow.graph.FlowNode;
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

    private static final String PMD_GROUP_ID = "org.apache.maven.plugins";
    private static final String PMD_ID = "maven-pmd-plugin";
    private static final String PMD_GOAL = "pmd";
    private static final String CPD_GOAL = "cpd";

    private static final String CHECKSTYLE_GROUP_ID = "org.apache.maven.plugins";
    private static final String CHECKSTYLE_ID = "maven-checkstyle-plugin";
    private static final String CHECKSTYLE_GOAL = "checkstyle";

    private static final String FINDBUGS_GROUP_ID = "org.codehaus.mojo";
    private static final String FINDBUGS_ID = "findbugs-maven-plugin";
    private static final String FINDBUGS_GOAL = "findbugs";

    private static final String SPOTBUGS_GROUP_ID = "com.github.spotbugs";
    private static final String SPOTBUGS_ID = "spotbugs-maven-plugin";
    private static final String SPOTBUGS_GOAL = "spotbugs";

    private String sourceCodeEncoding = "UTF-8";
    private boolean isEnabledForFailure = true;
    private boolean isBlameDisabled = true;
    private TrendChartType trendChartType = TrendChartType.TOOLS_ONLY;
    private int qualityGateThreshold = 1;
    private QualityGateType qualityGateType = QualityGateType.NEW;
    private QualityGateCriticality qualityGateCriticality = QualityGateCriticality.UNSTABLE;
    private String javaIgnorePatterns;
    private String highPriorityTaskIdentifiers = "FIXME";
    private String normalPriorityTaskIdentifiers = "TODO";
    private String tasksIncludePattern = "**/*.java";
    private String tasksExcludePattern = "**/target/**";

    @DataBoundConstructor
    public WarningsPublisher() {}

    public String getSourceCodeEncoding() {
        return sourceCodeEncoding;
    }

    @DataBoundSetter
    public void setSourceCodeEncoding(final String sourceCodeEncoding) {
        this.sourceCodeEncoding = sourceCodeEncoding;
    }

    public boolean isEnabledForFailure() {
        return isEnabledForFailure;
    }

    @DataBoundSetter
    public void setEnabledForFailure(final boolean enabledForFailure) {
        isEnabledForFailure = enabledForFailure;
    }

    public boolean isSkipBlames() {
        return isBlameDisabled;
    }

    @DataBoundSetter
    public void setSkipBlames(final boolean skipBlames) {
        isBlameDisabled = skipBlames;
    }

    public TrendChartType getTrendChartType() {
        return trendChartType;
    }

    @DataBoundSetter
    public void setTrendChartType(final TrendChartType trendChartType) {
        this.trendChartType = trendChartType;
    }

    public int getQualityGateThreshold() {
        return qualityGateThreshold;
    }

    @DataBoundSetter
    public void setQualityGateThreshold(int qualityGateThreshold) {
        this.qualityGateThreshold = qualityGateThreshold;
    }

    public QualityGateType getQualityGateType() {
        return qualityGateType;
    }

    @DataBoundSetter
    public void setQualityGateType(QualityGateType qualityGateType) {
        this.qualityGateType = qualityGateType;
    }

    public QualityGateCriticality getQualityGateCriticality() {
        return qualityGateCriticality;
    }

    @DataBoundSetter
    public void setQualityGateCriticality(QualityGateCriticality qualityGateCriticality) {
        this.qualityGateCriticality = qualityGateCriticality;
    }

    public String getJavaIgnorePatterns() {
        return javaIgnorePatterns;
    }

    @DataBoundSetter
    public void setJavaIgnorePatterns(String javaIgnorePatterns) {
        this.javaIgnorePatterns =
                javaIgnorePatterns != null && !javaIgnorePatterns.isEmpty() ? javaIgnorePatterns : null;
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

    public String getTasksIncludePattern() {
        return tasksIncludePattern;
    }

    @DataBoundSetter
    public void setTasksIncludePattern(String tasksIncludePattern) {
        this.tasksIncludePattern = tasksIncludePattern;
    }

    public String getTasksExcludePattern() {
        return tasksExcludePattern;
    }

    @DataBoundSetter
    public void setTasksExcludePattern(String tasksExcludePattern) {
        this.tasksExcludePattern = tasksExcludePattern;
    }

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

        perform(List.of(maven(context)), context, listener, "Maven console", step -> {});
        perform(java(context), context, listener, "Java and JavaDoc", step -> {
            if (javaIgnorePatterns != null && !javaIgnorePatterns.isEmpty()) {
                step.setFilters(List.of(new ExcludeFile(javaIgnorePatterns)));
            }
        });
        perform(List.of(taskScanner(context)), context, listener, "Open tasks", step -> {});
        List<Element> pmdEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, PMD_GROUP_ID, PMD_ID, PMD_GOAL, "MojoSucceeded", "MojoFailed");
        if (pmdEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + PMD_GROUP_ID + ":" + PMD_ID + ":" + PMD_GOAL
                                + " execution found");
            }
        } else {
            processPmd(pmdEvents, context, listener);
        }
        List<Element> cpdEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, PMD_GROUP_ID, PMD_ID, CPD_GOAL, "MojoSucceeded", "MojoFailed");
        if (cpdEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + PMD_GROUP_ID + ":" + PMD_ID + ":" + CPD_GOAL
                                + " execution found");
            }
        } else {
            processCpd(cpdEvents, context, listener);
        }
        List<Element> checkstyleEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, CHECKSTYLE_GROUP_ID, CHECKSTYLE_ID, CHECKSTYLE_GOAL, "MojoSucceeded", "MojoFailed");
        if (checkstyleEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + CHECKSTYLE_GROUP_ID + ":" + CHECKSTYLE_ID + ":"
                                + CHECKSTYLE_GOAL + " execution found");
            }
        } else {
            processCheckstyle(checkstyleEvents, context, listener);
        }
        List<Element> findbugsEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogsElt, FINDBUGS_GROUP_ID, FINDBUGS_ID, FINDBUGS_GOAL, "MojoSucceeded", "MojoFailed");
        if (findbugsEvents.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                listener.getLogger()
                        .println("[withMaven] warningsPublisher - No " + FINDBUGS_GROUP_ID + ":" + FINDBUGS_ID + ":"
                                + FINDBUGS_GOAL + " execution found");
            }
        } else {
            processFindBugs(findbugsEvents, context, listener);
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
            processSpotBugs(spotbugsEvents, context, listener);
        }
    }

    private void processPmd(List<Element> events, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(
                    event,
                    "targetDirectory",
                    null,
                    (dir, file) -> dir + File.separator + "pmd.xml",
                    workspace,
                    listener);
            if (result != null) {
                tools.add(
                        pmd(context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, "pmd", this::configureQualityGate);
    }

    private void processCpd(List<Element> events, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(
                    event,
                    "targetDirectory",
                    null,
                    (dir, file) -> dir + File.separator + "cpd.xml",
                    workspace,
                    listener);
            if (result != null) {
                tools.add(
                        cpd(context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, "cpd", this::configureQualityGate);
    }

    private void processCheckstyle(List<Element> events, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(
                    event,
                    null,
                    "outputFile",
                    (dir, file) -> file.contains("${checkstyle.output.file}")
                            ? file.replace(
                                    "${checkstyle.output.file}", "${project.build.directory}/checkstyle-result.xml")
                            : file,
                    workspace,
                    listener);
            if (result != null) {
                tools.add(checkstyle(
                        context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, "checkstyle", this::configureQualityGate);
    }

    private void processFindBugs(List<Element> events, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(
                    event,
                    "xmlOutputDirectory",
                    null,
                    (dir, file) -> dir + File.separator + "findbugsXml.xml",
                    workspace,
                    listener);
            if (result != null) {
                tools.add(spotBugs(
                        context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, "findbugs", this::configureQualityGate);
    }

    private void processSpotBugs(List<Element> events, StepContext context, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = context.get(FilePath.class);
        List<Tool> tools = new ArrayList<>();
        for (Element event : events) {
            ResultFile result = extractResultFile(
                    event,
                    "spotbugsXmlOutputDirectory",
                    "spotbugsXmlOutputFilename",
                    (dir, file) ->
                            dir + File.separator + file.replace("${spotbugs.outputXmlFilename}", "spotbugsXml.xml"),
                    workspace,
                    listener);
            if (result != null) {
                tools.add(spotBugs(
                        context, result.getMavenArtifact(), result.getPluginInvocation(), result.getResultFile()));
            }
        }
        perform(tools, context, listener, "spotbugs", this::configureQualityGate);
    }

    private void perform(
            List<Tool> tools,
            StepContext context,
            TaskListener listener,
            String kind,
            Consumer<RecordIssuesStep> stepConfigurer) {

        if (tools == null || tools.isEmpty()) {
            return;
        }

        listener.getLogger().println("[withMaven] warningsPublisher - Processing " + kind + " warnings");
        RecordIssuesStep step = new RecordIssuesStep();
        step.setTools(tools);
        stepConfigurer.accept(step);

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

    private Tool taskScanner(StepContext context) throws IOException, InterruptedException {
        OpenTasks tool = new OpenTasks();
        String name = computeName(tool, context);
        tool.setId(toId(name));
        tool.setName(name);
        tool.setIncludePattern(tasksIncludePattern);
        tool.setExcludePattern(tasksExcludePattern);
        tool.setHighTags(highPriorityTaskIdentifiers);
        tool.setNormalTags(normalPriorityTaskIdentifiers);
        return tool;
    }

    private Tool pmd(
            StepContext context,
            MavenArtifact mavenArtifact,
            MavenSpyLogProcessor.PluginInvocation pluginInvocation,
            String reportFile)
            throws IOException, InterruptedException {
        Pmd tool = new Pmd();
        String name = computeName(tool, context) + " " + mavenArtifact.getId() + " " + pluginInvocation.getId();
        tool.setId(toId(name));
        tool.setName(name);
        tool.setPattern(reportFile);
        return tool;
    }

    private Tool cpd(
            StepContext context,
            MavenArtifact mavenArtifact,
            MavenSpyLogProcessor.PluginInvocation pluginInvocation,
            String reportFile)
            throws IOException, InterruptedException {
        Cpd tool = new Cpd();
        String name = computeName(tool, context) + " " + mavenArtifact.getId() + " " + pluginInvocation.getId();
        tool.setId(toId(name));
        tool.setName(name);
        tool.setPattern(reportFile);
        return tool;
    }

    private Tool checkstyle(
            StepContext context,
            MavenArtifact mavenArtifact,
            MavenSpyLogProcessor.PluginInvocation pluginInvocation,
            String reportFile)
            throws IOException, InterruptedException {
        CheckStyle tool = new CheckStyle();
        String name = computeName(tool, context) + " " + mavenArtifact.getId() + " " + pluginInvocation.getId();
        tool.setId(toId(name));
        tool.setName(name);
        tool.setPattern(reportFile);
        return tool;
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

    private void configureQualityGate(RecordIssuesStep step) {
        step.setEnabledForFailure(isEnabledForFailure);
        step.setSourceCodeEncoding(sourceCodeEncoding);
        step.setSkipBlames(isBlameDisabled);
        step.setTrendChartType(trendChartType);
        step.setQualityGates(
                List.of(new WarningsQualityGate(qualityGateThreshold, qualityGateType, qualityGateCriticality)));
    }

    private ResultFile extractResultFile(
            Element event,
            String reportDirectoryAttribute,
            String reportFileAttribute,
            BiFunction<String, String, String> reportFilepathBuilder,
            FilePath workspace,
            TaskListener listener) {
        String eventType = event.getAttribute("type");
        if (!eventType.equals("MojoSucceeded") && !eventType.equals("MojoFailed")) {
            return null;
        }

        Element pluginElt = XmlUtils.getUniqueChildElement(event, "plugin");
        Element directoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, reportDirectoryAttribute);
        Element fileElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, reportFileAttribute);
        Element projectElt = XmlUtils.getUniqueChildElement(event, "project");
        MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(projectElt);
        MavenSpyLogProcessor.PluginInvocation pluginInvocation = XmlUtils.newPluginInvocation(pluginElt);

        if (reportDirectoryAttribute != null && !reportDirectoryAttribute.isEmpty() && directoryElt == null) {
            listener.getLogger()
                    .println("[withMaven] warningsPublisher - No <" + reportDirectoryAttribute
                            + "> element found for <plugin> in " + XmlUtils.toString(event));
            return null;
        }
        if (reportFileAttribute != null && !reportFileAttribute.isEmpty() && fileElt == null) {
            listener.getLogger()
                    .println("[withMaven] warningsPublisher - No <" + reportFileAttribute
                            + "> element found for <plugin> in " + XmlUtils.toString(event));
            return null;
        }

        String output = reportFilepathBuilder.apply(
                Optional.ofNullable(directoryElt)
                        .map(Element::getTextContent)
                        .map(String::trim)
                        .orElse(null),
                Optional.ofNullable(fileElt)
                        .map(Element::getTextContent)
                        .map(String::trim)
                        .orElse(null));
        output = XmlUtils.resolveMavenPlaceholders(output, projectElt);
        if (output == null) {
            listener.getLogger()
                    .println(
                            "[withMaven] could not resolve placeholder '${project.build.directory}' or '${project.reporting.outputDirectory}' or '${basedir}' in "
                                    + XmlUtils.toString(event));
            return null;
        }

        return new ResultFile(mavenArtifact, pluginInvocation, XmlUtils.getPathInWorkspace(output, workspace));
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
        private static final ValidationUtilities VALIDATION_UTILITIES = new ValidationUtilities();
        private static final JenkinsFacade JENKINS = new JenkinsFacade();
        private final ModelValidation model = new ModelValidation();

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

        @POST
        public ComboBoxModel doFillSourceCodeEncodingItems(@AncestorInPath final BuildableItem project) {
            if (JENKINS.hasPermission(Item.READ, project)) {
                return VALIDATION_UTILITIES.getAllCharsets();
            }
            return new ComboBoxModel();
        }

        @POST
        public FormValidation doCheckSourceCodeEncoding(
                @AncestorInPath final BuildableItem project, @QueryParameter final String sourceCodeEncoding) {
            if (!JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }
            return VALIDATION_UTILITIES.validateCharset(sourceCodeEncoding);
        }

        @POST
        public ListBoxModel doFillTrendChartTypeItems() {
            if (JENKINS.hasPermission(Jenkins.READ)) {
                return model.getAllTrendChartTypes();
            }
            return new ListBoxModel();
        }

        @POST
        public ListBoxModel doFillQualityGateTypeItems() {
            var model = new ListBoxModel();
            if (JENKINS.hasPermission(Jenkins.READ)) {
                for (QualityGateType qualityGateType : QualityGateType.values()) {
                    model.add(qualityGateType.getDisplayName(), qualityGateType.name());
                }
            }
            return model;
        }

        @POST
        public ListBoxModel doFillQualityGateCriticalityItems(@AncestorInPath final BuildableItem project) {
            if (JENKINS.hasPermission(Jenkins.READ)) {
                var options = new ListBoxModel();
                options.add(Messages.QualityGate_UnstableStage(), QualityGateCriticality.NOTE.name());
                options.add(Messages.QualityGate_UnstableRun(), QualityGateCriticality.UNSTABLE.name());
                options.add(Messages.QualityGate_FailureStage(), QualityGateCriticality.ERROR.name());
                options.add(Messages.QualityGate_FailureRun(), QualityGateCriticality.FAILURE.name());
                return options;
            }
            return new ListBoxModel();
        }

        @POST
        public FormValidation doCheckQualityGateThreshold(
                @AncestorInPath final BuildableItem project, @QueryParameter final int qualityGateThreshold) {
            if (!JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }
            if (qualityGateThreshold > 0) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.FieldValidator_Error_NegativeThreshold());
        }
    }
}
