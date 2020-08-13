package org.jenkinsci.plugins.pipeline.maven.model;

import org.apache.commons.collections.map.HashedMap;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.w3c.dom.Element;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ObjectFactory {
    private final transient DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.n");

    @Nonnull
    public MavenExecutionDetails analyseMavenBuildExecution(@Nonnull Element mavenSpyLogsElt) {
        String startTimeAsString = mavenSpyLogsElt.getAttribute("_time");
        ZonedDateTime mavenBuildStartTime = dateTimeFormatter.parse(startTimeAsString, LocalDateTime::from).atZone(ZoneId.systemDefault());

        MavenExecutionDetails mavenExecutionDetails = new MavenExecutionDetails(mavenBuildStartTime);
        Element mavenExecutionResult = XmlUtils.getUniqueChildElement(mavenSpyLogsElt, "MavenExecutionResult");
        String stopTimeAsString = mavenExecutionResult.getAttribute("_time");
        ZonedDateTime mavenBuildStopTime = dateTimeFormatter.parse(stopTimeAsString, LocalDateTime::from).atZone(ZoneId.systemDefault());

        List<Element> buildSummaries = XmlUtils.getChildrenElements(mavenExecutionResult,"buildSummary");
        // FIXME associate build summaries with projects
        /*
        <buildSummary
            baseDir="/path/to/multi-module-maven-project"
            file="/path/to/multi-module-maven-project/pom.xml"
            groupId="com.example"
            name="demo-parent"
            artifactId="demo-parent"
            time="960" version="0.0.29-SNAPSHOT"
            class="org.apache.maven.execution.BuildSuccess">
        </buildSummary>

        MavenExecutionStatus status;

        if (buildSummary == null) {
            status = MavenExecutionStatus.Failure;
        } else if ("org.apache.maven.execution.BuildSuccess".equals(buildSummary.getAttribute("class"))) {
            status = MavenExecutionStatus.Success;
        } else {
            status = MavenExecutionStatus.Failure;
        }
        */

        mavenExecutionDetails.setStop(mavenBuildStopTime);

        List<MavenMojoExecutionDetails> mojoTimers = analyseMavenMojoExecutions(mavenSpyLogsElt);
        Map<MavenArtifact, MavenProjectExecutionDetails> projectTimersPerProject = new HashedMap();
        for (MavenMojoExecutionDetails mojoTimer : mojoTimers) {
            projectTimersPerProject.computeIfAbsent(mojoTimer.getProject(), p -> new MavenProjectExecutionDetails(p)).add(mojoTimer);
        }

        mavenExecutionDetails.getMavenProjectExecutionDetails().addAll(projectTimersPerProject.values());

        return mavenExecutionDetails;
    }

    @Nonnull
    public List<MavenMojoExecutionDetails> analyseMavenMojoExecutions(@Nonnull Element mavenSpyLogsElt) {
        List<Element> executionEvents = XmlUtils.getChildrenElements(mavenSpyLogsElt, "ExecutionEvent");

        List<MavenMojoExecutionDetails> timers = new ArrayList<>();

        for (Element executionEventElt : executionEvents) {

            MavenExecutionEventType type = MavenExecutionEventType.valueOf(executionEventElt.getAttribute("type"));
            String timeAsString = executionEventElt.getAttribute("_time");
            ZonedDateTime mojoExecutionTime = dateTimeFormatter.parse(timeAsString, LocalDateTime::from).atZone(ZoneId.systemDefault());

            Element projectElt = XmlUtils.getUniqueChildElementOrNull(executionEventElt, "project");
            if (projectElt == null) {
                // ExecutionEvent not attached to a project. Not seen so far, just in case
                continue;
            }
            if (!projectElt.hasAttribute("groupId") || !projectElt.hasAttribute("artifactId") || !projectElt.hasAttribute("version")) {
                // ExecutionEvent not attached stop an artifact such as "ProjectDiscoveryStarted"
                continue;
            }
            MavenArtifact project = XmlUtils.newMavenArtifact(projectElt);

            Element pluginElt = XmlUtils.getUniqueChildElementOrNull(executionEventElt, "plugin");
            if (pluginElt == null) {
                // ExecutionEvent not attached to a plugin/mojo, ignore
                continue;
            }
            if (!pluginElt.hasAttribute("groupId") || !pluginElt.hasAttribute("artifactId") || !pluginElt.hasAttribute("version")) {
                // ExecutionEvent not attached to a plugin/mojo (e.g. "ProjectDiscoveryStarted"), ignore
                continue;
            }

            MavenArtifact plugin = XmlUtils.newMavenArtifact(pluginElt);
            String executionId = pluginElt.getAttribute("executionId");
            String goal = pluginElt.getAttribute("goal");
            String lifecyclePhase = pluginElt.getAttribute("lifecyclePhase");


            switch (type) {
                case MojoStarted:
                case MojoSkipped:
                    MavenMojoExecutionDetails timer = new MavenMojoExecutionDetails(project, plugin, executionId, lifecyclePhase, goal, mojoExecutionTime, type);
                    timers.add(timer);
                    break;
                case MojoFailed:
                case MojoSucceeded:
                    MavenMojoExecutionDetails matchingTimer = timers.stream().filter(t -> t.getProject().equals(project) && t.getPlugin().equals(plugin) && t.getExecutionId().equals(executionId)).findFirst().get();
                    if (matchingTimer == null) {
                        // fixme log warning
                    } else {
                        matchingTimer.stop(mojoExecutionTime, type);
                    }
                    break;
                default:
                    // ignore other event types
            }
        }
        Collections.sort(timers);
        return timers;
    }
}
