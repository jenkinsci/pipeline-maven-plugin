package org.jenkinsci.plugins.pipeline.maven;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.w3c.dom.Element;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Experimental interface, likely to change in the future.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface ResultsReporter {
    /**
     *
     * @param context
     * @param mavenSpyLogsElt maven spy report. WARNING experimental structure for the moment, subject to change.
     * @throws IOException
     * @throws InterruptedException
     */
    void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException;
}