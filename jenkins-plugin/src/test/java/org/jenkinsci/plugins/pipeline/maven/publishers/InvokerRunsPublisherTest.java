package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class InvokerRunsPublisherTest {


    @Test
    public void test_relative_path_and_absolute_path_and_variabilized_path_run_goal() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy-maven-invoker-plugin-run.xml";
        test_relative_path_and_absolute_path_and_variabilized_path_run_goal(mavenSpyLogs, InvokerRunsPublisher.RUN_GOAL);
    }
    @Test
    public void test_relative_path_and_absolute_path_and_variabilized_path_integration_test_goal() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy-maven-invoker-plugin-integration-test.xml";
        test_relative_path_and_absolute_path_and_variabilized_path_run_goal(mavenSpyLogs, InvokerRunsPublisher.INTEGRATION_TEST_GOAL);
    }

    /**
     * projectsDirectory = src/it -> relative path
     * cloneProjectsTo = /path/to/khmarbaise/maui/src/main/resources/mp-it-1/target/it -> absolute path
     * reportsDirectory = ${invoker.reportsDirectory} -> variabilized path
     */
    protected void test_relative_path_and_absolute_path_and_variabilized_path_run_goal(String mavenSpyLogs, String goal) throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        InvokerRunsPublisher invokerRunsPublisher = new InvokerRunsPublisher();
        List<Element> invokerRunEvents = XmlUtils.getExecutionEvents(doc.getDocumentElement(), InvokerRunsPublisher.GROUP_ID, InvokerRunsPublisher.ARTIFACT_ID, goal);

        FilePath workspace = new FilePath(new File("/path/to/khmarbaise/maui/src/main/resources/mp-it-1"));
        TaskListener listener = new StreamTaskListener(System.out, StandardCharsets.UTF_8);

        System.out.println(invokerRunEvents.size());
        List<Element> invokerRunSucceededEvents = new ArrayList<>();
        for (Element invokerRunEvent : invokerRunEvents) {
            String eventType = invokerRunEvent.getAttribute("type");
            if (eventType.equals("MojoSucceeded")) {
                invokerRunSucceededEvents.add(invokerRunEvent);
            }
        }
        Assert.assertThat(invokerRunSucceededEvents.size(), Matchers.is(1));
        Element invokerRunSucceedEvent = invokerRunSucceededEvents.get(0);

        Element projectElt = XmlUtils.getUniqueChildElement(invokerRunSucceedEvent, "project");
        Element pluginElt = XmlUtils.getUniqueChildElement(invokerRunSucceedEvent, "plugin");
        Element reportsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "reportsDirectory");
        Element cloneProjectsToElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "cloneProjectsTo");
        Element projectsDirectoryElt = XmlUtils.getUniqueChildElementOrNull(pluginElt, "projectsDirectory");

        String reportsDirectory = invokerRunsPublisher.expandAndRelativize(reportsDirectoryElt, "reportsDirectory", invokerRunSucceedEvent, projectElt, workspace, listener);
        Assert.assertThat(reportsDirectory, Matchers.is("target/invoker-reports"));
        String projectsDirectory = invokerRunsPublisher.expandAndRelativize(projectsDirectoryElt, "projectsDirectory", invokerRunSucceedEvent, projectElt, workspace, listener);
        Assert.assertThat(projectsDirectory, Matchers.is("src/it"));
        String cloneProjectsTo = invokerRunsPublisher.expandAndRelativize(cloneProjectsToElt, "cloneProjectsTo", invokerRunSucceedEvent, projectElt, workspace, listener);
        Assert.assertThat(cloneProjectsTo, Matchers.is("target/it"));
    }
}
