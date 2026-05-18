package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
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
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoSucceeded);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("install");
    }

    @Test
    public void testWithoutExistingLifecyclePhase() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoSucceeded);

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
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoSucceeded);

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
        ExecutionEvent event = this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoSucceeded);

        this.handler._handle(event);

        Xpp3Dom report = this.closeReporterAndGenerateReport();
        Xpp3Dom executionReport = report.getChild("ExecutionEvent");
        Xpp3Dom plugin = executionReport.getChild("plugin");

        assertThat(plugin.getAttribute("lifecyclePhase")).isEqualTo("deploy");
    }

    @Test
    public void testRepositoryEventsBufferedBetweenStartAndSucceeded() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());

        // MojoStarted opens the buffer; should not be consumed
        ExecutionEvent startedEvent =
                this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoStarted);
        boolean startedHandled = this.handler.handle(startedEvent);
        assertThat(startedHandled).isFalse();

        // ARTIFACT_DEPLOYED event during the execution window: buffered, not consumed
        File deployedFile = new File("/tmp/mylib-1.0.jar");
        RepositoryEvent repositoryEvent =
                createArtifactDeployedEvent("com.example", "mylib", "1.0", "jar", "", deployedFile);
        boolean repoEventHandled = this.handler.handle(repositoryEvent);
        assertThat(repoEventHandled).isFalse();

        // MojoSucceeded drains the buffer and attaches the artifact
        ExecutionEvent succeededEvent =
                this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoSucceeded);
        this.handler.handle(succeededEvent);

        List<Artifact> attachedArtifacts = project.getAttachedArtifacts();
        assertThat(attachedArtifacts).hasSize(1);
        Artifact attached = attachedArtifacts.get(0);
        assertThat(attached.getGroupId()).isEqualTo("com.example");
        assertThat(attached.getArtifactId()).isEqualTo("mylib");
        assertThat(attached.getVersion()).isEqualTo("1.0");
        assertThat(attached.getFile()).isEqualTo(deployedFile);
    }

    @Test
    public void testRepositoryEventsNotBufferedAfterMojoFailed() throws Exception {
        MojoExecution execution = new MojoExecution(this.createTestingMojoDescriptor());

        this.handler.handle(this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoStarted));
        this.handler.handle(this.createTestDeployFileExecutionEvent(execution, ExecutionEvent.Type.MojoFailed));

        // After failure the buffer is gone; a subsequent RepositoryEvent must not be buffered
        RepositoryEvent repositoryEvent =
                createArtifactDeployedEvent("com.example", "mylib", "1.0", "jar", "", new File("/tmp/mylib-1.0.jar"));
        boolean repoEventHandled = this.handler.handle(repositoryEvent);
        assertThat(repoEventHandled).isFalse();
        assertThat(project.getAttachedArtifacts()).isEmpty();
    }

    @Test
    public void testRepositoryEventBeforeStartNotBuffered() throws Exception {
        // No MojoStarted yet → RepositoryEvent must pass through unaffected
        RepositoryEvent repositoryEvent =
                createArtifactDeployedEvent("com.example", "mylib", "1.0", "jar", "", new File("/tmp/mylib-1.0.jar"));
        boolean handled = this.handler.handle(repositoryEvent);
        assertThat(handled).isFalse();
        assertThat(project.getAttachedArtifacts()).isEmpty();
    }

    private ExecutionEvent createTestDeployFileExecutionEvent(MojoExecution mojoExecution, ExecutionEvent.Type type) {
        return new ExecutionEvent() {
            @Override
            public Type getType() {
                return type;
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
     * Create a dummy Maven-Project
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

    private RepositoryEvent createArtifactDeployedEvent(
            String groupId, String artifactId, String version, String extension, String classifier, File file) {
        org.eclipse.aether.artifact.Artifact artifact =
                new DefaultArtifact(groupId, artifactId, classifier, extension, version).setFile(file);
        RemoteRepository repository =
                new RemoteRepository.Builder("central", "default", "https://repo.example.com/releases").build();
        return new RepositoryEvent.Builder(
                        new DefaultRepositorySystemSession(), RepositoryEvent.EventType.ARTIFACT_DEPLOYED)
                .setArtifact(artifact)
                .setRepository(repository)
                .build();
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
