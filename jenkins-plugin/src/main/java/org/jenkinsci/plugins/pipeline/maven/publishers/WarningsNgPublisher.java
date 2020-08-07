package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.IssuesRecorder;
import io.jenkins.plugins.analysis.warnings.SpotBugs;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WarningsNgPublisher extends MavenPublisher {
    private static final Logger LOGGER = Logger.getLogger(SpotBugsAnalysisPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {

        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);

        List<Element> spotbugsEvents = XmlUtils.getExecutionEventsByPlugin(mavenSpyLogsElt, "com.github.spotbugs", "spotbugs-maven-plugin", "spotbugs", "MojoSucceeded", "MojoFailed");

        if (spotbugsEvents.isEmpty()) {
            LOGGER.log(Level.FINE, "No com.github.spotbugs:spotbugs-maven-plugin:spotbugs execution found");
            return;
        }

        List<SpotBugsReportDetails> spotBugsReportDetails = new ArrayList<>();
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
                listener.getLogger().println("[withMaven] No <xmlOutputDirectoryElt> element found for <plugin> in " + XmlUtils.toString(spotBugsTestEvent));
                continue;
            }
            String xmlOutputDirectory = xmlOutputDirectoryElt.getTextContent().trim();
            if (xmlOutputDirectory.contains("${project.build.directory}")) {
                String projectBuildDirectory = XmlUtils.getProjectBuildDirectory(projectElt);
                if (projectBuildDirectory == null || projectBuildDirectory.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${project.build.directory}' found for <project> in " + XmlUtils.toString(spotBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${project.build.directory}", projectBuildDirectory);

            } else if (xmlOutputDirectory.contains("${basedir}")) {
                String baseDir = projectElt.getAttribute("baseDir");
                if (baseDir.isEmpty()) {
                    listener.getLogger().println("[withMaven] '${basedir}' found for <project> in " + XmlUtils.toString(spotBugsTestEvent));
                    continue;
                }

                xmlOutputDirectory = xmlOutputDirectory.replace("${basedir}", baseDir);
            }

            xmlOutputDirectory = XmlUtils.getPathInWorkspace(xmlOutputDirectory, workspace);

            String spotBugsResultsFile = xmlOutputDirectory + "/spotbugsXml.xml";

            SpotBugsReportDetails details = new SpotBugsReportDetails(mavenArtifact, pluginInvocation, spotBugsResultsFile);
            spotBugsReportDetails.add(details);
        }

        List<Tool> tools = new ArrayList<>();
        tools.addAll(spotBugsReportDetails.stream().map(details -> details.toTool()).collect(Collectors.toList()));

        IssuesRecorder issuesRecorder = new IssuesRecorder();
        issuesRecorder.setTools(tools);
        issuesRecorder.perform(run, workspace, launcher, listener);

    }

    public static class SpotBugsReportDetails {
        final MavenArtifact mavenArtifact;
        final MavenSpyLogProcessor.PluginInvocation pluginInvocation;
        final String spotBugsReportFile;

        public SpotBugsReportDetails(MavenArtifact mavenArtifact, MavenSpyLogProcessor.PluginInvocation pluginInvocation, String spotBugsReportFile) {
            this.mavenArtifact = mavenArtifact;
            this.pluginInvocation = pluginInvocation;
            this.spotBugsReportFile = spotBugsReportFile;
        }

        @Override
        public String toString() {
            return "spotBugsReportDetails{" +
                    "mavenArtifact=" + mavenArtifact +
                    ", pluginInvocation='" + pluginInvocation + '\'' +
                    ", spotBugsReportFile='" + spotBugsReportFile + '\'' +
                    '}';
        }
        public SpotBugs toTool() {
            SpotBugs spotBugs = new SpotBugs();
            spotBugs.setId(spotBugs.getDescriptor().getId() + "_" + mavenArtifact.getId() + "_" + pluginInvocation.getId());
            spotBugs.setName(spotBugs.getDescriptor().getName() + " " + mavenArtifact.getId() + " " + pluginInvocation.getId());
            spotBugs.setPattern(spotBugsReportFile);
            return spotBugs;
        }
    }
}
