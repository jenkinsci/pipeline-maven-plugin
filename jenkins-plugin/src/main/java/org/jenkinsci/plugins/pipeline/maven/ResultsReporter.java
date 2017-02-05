package org.jenkinsci.plugins.pipeline.maven;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface ResultsReporter {
    void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException;
}