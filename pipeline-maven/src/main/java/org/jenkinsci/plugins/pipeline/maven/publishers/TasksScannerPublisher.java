package org.jenkinsci.plugins.pipeline.maven.publishers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import java.io.IOException;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 *
 * @deprecated since TODO
 */
@Deprecated
public class TasksScannerPublisher extends MavenPublisher {

    private static final Logger LOGGER = Logger.getLogger(TasksScannerPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public TasksScannerPublisher() {}

    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {
        throw new AbortException(
                """
        The openTasksPublisher is deprecated as is the tasks plugin and you should not use it.
        Alternatively, you should rely on warningsPublisher.
        Or, if continuing to use the tasks plugin for now, run it explicitly rather than expecting withMaven to run it implicitly.
        """);
    }

    @DataBoundSetter
    public void setHealthy(String healthy) {}

    @DataBoundSetter
    public void setUnHealthy(String unHealthy) {}

    @DataBoundSetter
    public void setThresholdLimit(String thresholdLimit) {}

    @DataBoundSetter
    public void setHighPriorityTaskIdentifiers(String highPriorityTaskIdentifiers) {}

    @DataBoundSetter
    public void setNormalPriorityTaskIdentifiers(String normalPriorityTaskIdentifiers) {}

    @DataBoundSetter
    public void setLowPriorityTaskIdentifiers(String lowPriorityTaskIdentifiers) {}

    @DataBoundSetter
    public void setIgnoreCase(boolean ignoreCase) {}

    @DataBoundSetter
    public void setPattern(String pattern) {}

    @DataBoundSetter
    public void setExcludePattern(String excludePattern) {}

    @DataBoundSetter
    public void setAsRegexp(boolean asRegexp) {}

    @Symbol("openTasksPublisher")
    @Extension
    // should be OptionalExtension, and not Extension, but tasks plugin is deprecated, no more available in update site,
    // so we cannot install it automatically during test to enable this extension as optional
    // @OptionalExtension(requirePlugins = "tasks")
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_tasks_scanner_description();
        }

        @Override
        public int ordinal() {
            return -1;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-task-scanner";
        }
    }
}
