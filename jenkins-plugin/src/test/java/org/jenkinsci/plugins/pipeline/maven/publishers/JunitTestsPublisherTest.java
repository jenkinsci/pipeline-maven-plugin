package org.jenkinsci.plugins.pipeline.maven.publishers;

import static java.lang.System.getProperty;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;

import java.io.File;
import java.io.InputStream;
import java.util.Calendar;

import javax.xml.parsers.DocumentBuilderFactory;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;

public class JunitTestsPublisherTest {

    @Test
    public void test_frontend_plugin() throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-maven-frontend-plugin.xml");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new File("src/test/resources/org/jenkinsci/plugins/pipeline/maven/karma-reports/TEST-karma.xml").lastModified());
        StepContext context = mock(StepContext.class);
        Run run = mock(Run.class);
        FlowNode node = mock(FlowNode.class);
        EnvVars envvars = mock(EnvVars.class);
        when(context.get(FilePath.class)).thenReturn(new FilePath(new File("")));
        when(context.get(Run.class)).thenReturn(run);
        when(context.get(FlowNode.class)).thenReturn(node);
        when(node.getId()).thenReturn("nodeId");
        when(run.getEnvironment(any())).thenReturn(envvars);
        when(run.getTimestamp()).thenReturn(calendar);
        when(run.getRootDir()).thenReturn(new File(getProperty("java.io.tmpdir")));
        when(envvars.expand(any())).thenReturn("src/test/resources/org/jenkinsci/plugins/pipeline/maven/karma-reports/*.xml");

        new JunitTestsPublisher().process(context , doc.getDocumentElement());

        verify(run).addAction(any(TestResultAction.class));
    }
}
