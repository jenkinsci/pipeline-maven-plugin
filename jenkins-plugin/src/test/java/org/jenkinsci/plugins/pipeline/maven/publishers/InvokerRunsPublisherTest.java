package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.junit.Assert;
import org.junit.Before;
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
    Document doc;

    @Before
    public void before() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy-maven-invoker-plugin-integration-tests.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }

    /*
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-03-06 23:49:30.662">
<project baseDir="/path/to/khmarbaise/maui/src/main/resources/mp-it-1" file="/path/to/khmarbaise/maui/src/main/resources/mp-it-1/pom.xml" groupId="com.soebes.maven.guide.mp.it" name="Maven Plugin Integration Test" artifactId="mp-it-1" version="0.1-SNAPSHOT">
    <build sourceDirectory="/path/to/khmarbaise/maui/src/main/resources/mp-it-1/src/main/java" directory="/path/to/khmarbaise/maui/src/main/resources/mp-it-1/target"/>
</project>
<plugin executionId="integration-test" goal="run" lifecyclePhase="integration-test" groupId="org.apache.maven.plugins" artifactId="maven-invoker-plugin" version="3.0.1">
    <projectsDirectory>src/it</projectsDirectory>
    <cloneProjectsTo>/path/to/khmarbaise/maui/src/main/resources/mp-it-1/target/it</cloneProjectsTo>
    <reportsDirectory>${invoker.reportsDirectory}</reportsDirectory>
</plugin>
</ExecutionEvent>
 */

    /**
     * projectsDirectory = src/it -> relative path
     * cloneProjectsTo = /path/to/khmarbaise/maui/src/main/resources/mp-it-1/target/it -> absolute path
     * reportsDirectory = ${invoker.reportsDirectory} -> variabilized path
     */
    @Test
    public void test_relative_path_and_absolute_path_and_variabilized_path() {
        InvokerRunsPublisher invokerRunsPublisher = new InvokerRunsPublisher();
        List<Element> invokerRunEvents = XmlUtils.getExecutionEvents(doc.getDocumentElement(), InvokerRunsPublisher.GROUP_ID, InvokerRunsPublisher.ARTIFACT_ID, InvokerRunsPublisher.RUN_GOAL);

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
