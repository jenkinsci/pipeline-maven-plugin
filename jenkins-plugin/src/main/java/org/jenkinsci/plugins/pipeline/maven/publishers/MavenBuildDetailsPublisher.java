package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenBuildDetailsPublisher extends MavenPublisher {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MavenBuildDetailsPublisher.class.getName());

    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {
        TaskListener listener = context.get(TaskListener.class);
        PrintStream logger = listener.getLogger();
        process(mavenSpyLogsElt, logger);

    }

    public void process(@Nonnull Element mavenSpyLogsElt, PrintStream out) {
        out.println("[withMaven] Build details");
        // FIXME
    }





    @Symbol("moduleViewPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Module View Publisher";
        }

        @Override
        public int ordinal() {
            return 1000;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-module-view";
        }

    }
}
