package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.OutputStreamEventReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:r.schleuse@gmail.com">René Schleusner</a>
 */
public class DeployDeployFileExecutionHandlerTest {

    DeployDeployFileExecutionHandler handler;
    MavenProject project;
    ByteArrayOutputStream eventReportOutputStream;
    OutputStreamEventReporter reporter;

    @BeforeEach
    public void before() throws Exception {
        eventReportOutputStream = new ByteArrayOutputStream();
        reporter = new OutputStreamEventReporter(eventReportOutputStream);
        handler = new DeployDeployFileExecutionHandler(reporter);
        project = this.createTestMavenProject();
    }

    @AfterEach
    public void after() throws IOException {
        this.eventReportOutputStream.close();
    }

    @Test
    public void testWithExistingLifecyclePhase() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());
        execution.setLifecyclePhase("install");
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("install");
    }

    @Test
    public void testWithoutExistingLifecyclePhase() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("deploy");
    }

    @Test
    public void testWithEmptyLifecyclePhase() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());
        execution.setLifecyclePhase("");
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("deploy");
    }

    @Test
    public void testWithNullLifecyclePhase() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());
        execution.setLifecyclePhase(null);
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("deploy");
    }

    /**
     * Creates a ExecutionEvent which describes a successfull invocation of passed
     * MojoExecution on a dummy-project
     *
     * @param mojoExecution
     * @return
     */
    private ExecutionEvent createTestDeployFileExecutionEvent(MojoExecution mojoExecution) {
        return new ExecutionEvent() {
            @Override
            public Type getType() {
                return Type.MojoSucceeded;
            }

            @Override
            public MavenSession getSession() {
                return null;
            }

            @Override
            public MavenProject getProject() {
                return project;
            }

            @Override
            public MojoExecution getMojoExecution() {
                return mojoExecution;
            }

            @Override
            public Exception getException() {
                return null;
            }
        };
    }

    /**
     * Create a dummy Maven-Projet
     *
     * @return
     * @throws IOException
     * @throws XmlPullParserException
     */
    private MavenProject createTestMavenProject() throws Exception {
        Model model = new Model();
        model.setGroupId("org.springframework.samples");
        model.setArtifactId("spring-petclinic");
        model.setVersion("1.4.2");
        model.setName("petclinic");

        project = new MavenProject(model);
        project.setGroupId(model.getGroupId());
        project.setArtifactId(model.getArtifactId());
        project.setVersion(model.getVersion());
        project.setName(model.getName());
        return project;
    }

    /**
     * Creates a MojoDescriptor for an invocation of maven-deploy-plugin:deploy-file
     *
     * @return
     */
    private MojoDescriptor createTestingMojoDescriptor() {
        PluginDescriptor plugin = new PluginDescriptor();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-deploy-plugin");
        plugin.setVersion("3.0.0-M2");

        MojoDescriptor desc = new MojoDescriptor();
        desc.setPluginDescriptor(plugin);
        desc.setGoal("deploy-file");

        return desc;
    }

    /**
     * Closes the reporter and returns the resulting report as a Xpp3Dom object
     *
     * @return
     * @throws XmlPullParserException
     * @throws IOException
     */
    private Xpp3Dom closeReporterAndGenerateReport() throws Exception {
        this.reporter.close();
        ByteArrayInputStream inp = new ByteArrayInputStream(this.eventReportOutputStream.toByteArray());
        return Xpp3DomBuilder.build(inp, "UTF-8");
    }
}
