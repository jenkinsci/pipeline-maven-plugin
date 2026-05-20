package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositoryEvent;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

/**
 * Handler for the {@code org.apache.maven.plugins:maven-deploy-plugin:deploy-file} goal.
 *
 * Does two things:
 *
 * 1. Sets {@code lifecyclePhase} to {@code deploy} when none is set (direct
 *    invocation, e.g. {@code mvn deploy:deploy-file -Dfile=...}).
 * 2. Correlates {@code ARTIFACT_DEPLOYED} repository events that fire between
 *    {@code MojoStarted} and {@code MojoSucceeded} for each distinct execution and attaches
 *    those artifacts to the Maven project. This makes them visible in the
 *    {@code ProjectSucceeded} report so that downstream Jenkins processing
 *    ({@code XmlUtils.listGeneratedArtifacts}) can pick them up via the normal
 *    file-path correlation with the top-level {@code RepositoryEvent} elements.
 *
 * @author René Schleusner
 */
public class DeployDeployFileExecutionHandler extends AbstractExecutionHandler {

    private static final String PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String PLUGIN_ARTIFACT_ID = "maven-deploy-plugin";
    private static final String PLUGIN_GOAL = "deploy-file";

    /**
     * Repository events buffered per executionId while a {@code deploy-file} mojo is in flight.
     * Keyed by {@link MojoExecution#getExecutionId()}.
     */
    private final Map<String, List<RepositoryEvent>> repositoryEventsByExecutionId = new LinkedHashMap<>();

    public DeployDeployFileExecutionHandler(@NonNull MavenEventReporter reporter) {
        super(reporter);
    }

    /**
     * Intercepts three event kinds in addition to the normal {@code MojoSucceeded} delegation:
     *
     * - {@code MojoStarted} for deploy-file: opens a per-executionId buffer.
     * - {@code RepositoryEvent.ARTIFACT_DEPLOYED} while any buffer is open: appends to every
     *   open buffer but returns {@code false} so {@link ArtifactDeployedEventHandler} still
     *   emits the top-level {@code <RepositoryEvent>} element that
     *   {@code XmlUtils.getArtifactDeployedEvents()} expects.
     * - {@code MojoFailed} for deploy-file: discards the buffer to avoid leaks.
     */
    @Override
    public boolean handle(@NonNull Object event) {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            MojoExecution mojoExecution = executionEvent.getMojoExecution();
            if (isDeployFile(mojoExecution)) {
                if (ExecutionEvent.Type.MojoStarted.equals(executionEvent.getType())) {
                    repositoryEventsByExecutionId.put(mojoExecution.getExecutionId(), new ArrayList<>());
                    return false;
                } else if (ExecutionEvent.Type.MojoFailed.equals(executionEvent.getType())) {
                    repositoryEventsByExecutionId.remove(mojoExecution.getExecutionId());
                    return false;
                }
            }
        }

        if (event instanceof RepositoryEvent && !repositoryEventsByExecutionId.isEmpty()) {
            RepositoryEvent repositoryEvent = (RepositoryEvent) event;
            if (RepositoryEvent.EventType.ARTIFACT_DEPLOYED.equals(repositoryEvent.getType())) {
                for (List<RepositoryEvent> buffer : repositoryEventsByExecutionId.values()) {
                    buffer.add(repositoryEvent);
                }
                // Return false: ArtifactDeployedEventHandler must still emit the top-level
                // <RepositoryEvent> element so XmlUtils.getArtifactDeployedEvents() finds it.
                return false;
            }
        }

        return super.handle(event);
    }

    private boolean isDeployFile(@Nullable MojoExecution mojoExecution) {
        return mojoExecution != null
                && PLUGIN_GROUP_ID.equals(mojoExecution.getGroupId())
                && PLUGIN_ARTIFACT_ID.equals(mojoExecution.getArtifactId())
                && PLUGIN_GOAL.equals(mojoExecution.getGoal());
    }

    @Override
    protected List<String> getConfigurationParametersToReport(ExecutionEvent executionEvent) {
        return new ArrayList<>();
    }

    @Override
    protected void addDetails(@NonNull ExecutionEvent executionEvent, @NonNull Xpp3Dom root) {
        super.addDetails(executionEvent, root);
        MojoExecution execution = executionEvent.getMojoExecution();
        if (execution == null) {
            return;
        }

        /*
         * When Plugin is executed directly the MojoExecution doesn't
         * contain a lifecyclePhase. For the deploy-file goal we assume
         * the deploy phase in this case
         */
        String lifecyclePhase = execution.getLifecyclePhase();
        if (lifecyclePhase == null || lifecyclePhase.isEmpty()) {
            Xpp3Dom plugin = root.getChild("plugin");
            if (plugin != null) {
                plugin.setAttribute("lifecyclePhase", "deploy");
            }
        }

        List<RepositoryEvent> repositoryEvents = repositoryEventsByExecutionId.remove(execution.getExecutionId());
        if (repositoryEvents == null || repositoryEvents.isEmpty()) {
            return;
        }

        MavenProject project = executionEvent.getProject();
        DefaultMavenProjectHelper projectHelper = new DefaultMavenProjectHelper();

        for (RepositoryEvent repositoryEvent : repositoryEvents) {
            Artifact artifact = convertAetherToMavenArtifact(repositoryEvent.getArtifact());
            if (artifact == null || artifact.getFile() == null) {
                continue;
            }
            projectHelper.attachArtifact(project, artifact);
        }
    }

    private @Nullable Artifact convertAetherToMavenArtifact(
            @Nullable org.eclipse.aether.artifact.Artifact aetherArtifact) {
        if (aetherArtifact == null || aetherArtifact.getFile() == null) {
            return null;
        }

        String classifier = aetherArtifact.getClassifier();
        DefaultArtifactHandler artifactHandler = new DefaultArtifactHandler(aetherArtifact.getExtension());
        DefaultArtifact mavenArtifact = new DefaultArtifact(
                aetherArtifact.getGroupId(),
                aetherArtifact.getArtifactId(),
                aetherArtifact.getVersion(),
                null,
                aetherArtifact.getExtension(),
                classifier != null && !classifier.isEmpty() ? classifier : null,
                artifactHandler);
        mavenArtifact.setFile(aetherArtifact.getFile());
        return mavenArtifact;
    }

    @Override
    protected Type getSupportedType() {
        return ExecutionEvent.Type.MojoSucceeded;
    }

    @Nullable
    @Override
    protected String getSupportedPluginGoal() {
        return String.join(":", PLUGIN_GROUP_ID, PLUGIN_ARTIFACT_ID, PLUGIN_GOAL);
    }
}
