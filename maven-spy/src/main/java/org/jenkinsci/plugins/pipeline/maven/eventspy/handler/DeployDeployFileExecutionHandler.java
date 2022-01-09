package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Handler to alter the
 * <code>org.apache.maven.plugins:maven-deploy-plugin:deploy-file</code> goal : it will
 * set the <code>lifecyclePhase</code> to <code>deploy</code> when no lifecycle is set.
 *
 * This can happen when the plugin is invoked directly
 *
 * @author <a href="mailto:r.schleuse@gmail.com">René Schleusner</a>
 *
 */
public class DeployDeployFileExecutionHandler extends AbstractExecutionHandler {
    public DeployDeployFileExecutionHandler(@NonNull MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected List<String> getConfigurationParametersToReport(ExecutionEvent executionEvent) {
        return new ArrayList<String>();
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
    }

    @Override
    protected Type getSupportedType() {
        return ExecutionEvent.Type.MojoSucceeded;
    }

	@Nullable
    @Override
    protected String getSupportedPluginGoal() {
        return "org.apache.maven.plugins:maven-deploy-plugin:deploy-file";
    }
}
