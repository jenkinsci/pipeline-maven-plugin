/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven.publishers;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultSummary;
import hudson.tasks.junit.pipeline.JUnitResultsStepExecution;
import hudson.tasks.test.PipelineTestDetails;

public class JUnitUtils {

    private static final Logger LOGGER = Logger.getLogger(JUnitUtils.class.getName());

    static JUnitResultArchiver buildArchiver(final String testResults, final boolean keepLongStdio, final Double healthScaleFactor) {
        final JUnitResultArchiver archiver = new JUnitResultArchiver(testResults);
        // even if "org.apache.maven.plugins:maven-surefire-plugin@test" succeeds, it
        // maybe with "-DskipTests" and thus not have any test results.
        archiver.setAllowEmptyResults(true);
        archiver.setKeepLongStdio(keepLongStdio);
        if (healthScaleFactor != null) {
            archiver.setHealthScaleFactor(healthScaleFactor);
        }
        return archiver;
    }

    static void archiveResults(final StepContext context, final JUnitResultArchiver archiver, final String testResults, final String publisherName)
            throws IOException, InterruptedException {
        TaskListener listener = context.get(TaskListener.class);
        FilePath workspace = context.get(FilePath.class);
        Run run = context.get(Run.class);
        Launcher launcher = context.get(Launcher.class);
        try {
            // see hudson.tasks.junit.pipeline.JUnitResultsStepExecution.run
            FlowNode node = context.get(FlowNode.class);
            String nodeId = node.getId();
            List<FlowNode> enclosingBlocks = JUnitResultsStepExecution.getEnclosingStagesAndParallels(node);
            PipelineTestDetails pipelineTestDetails = new PipelineTestDetails();
            pipelineTestDetails.setNodeId(nodeId);
            pipelineTestDetails.setEnclosingBlocks(JUnitResultsStepExecution.getEnclosingBlockIds(enclosingBlocks));
            pipelineTestDetails.setEnclosingBlockNames(JUnitResultsStepExecution.getEnclosingBlockNames(enclosingBlocks));

            if (LOGGER.isLoggable(Level.FINER)) {
                listener.getLogger().println("[withMaven] " + publisherName + " - collect test reports: testResults=" + archiver.getTestResults()
                        + ", healthScaleFactor=" + archiver.getHealthScaleFactor());
            }
            TestResultSummary testResultSummary = JUnitResultArchiver.parseAndSummarize(archiver, pipelineTestDetails, run, workspace, launcher, listener);

            if (testResultSummary == null) {
                // no unit test results found
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] " + publisherName + " - no unit test results found, ignore");
                }
            } else if (testResultSummary.getFailCount() == 0) {
                // unit tests are all successful
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] " + publisherName + " - unit tests are all successful");
                }
            } else {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println(
                            "[withMaven] " + publisherName + " - " + testResultSummary.getFailCount() + " unit test failure(s) found, mark job as unstable");
                }
                node.addAction(new WarningAction(Result.UNSTABLE).withMessage(testResultSummary.getFailCount() + " unit test failure(s) found"));
                run.setResult(Result.UNSTABLE);
            }
        } catch (RuntimeException e) {
            listener.error("[withMaven] " + publisherName + " - exception archiving JUnit results " + testResults + ": " + e + ". Failing the build.");
            LOGGER.log(Level.WARNING, "Exception processing " + testResults, e);
            run.setResult(Result.FAILURE);
        }
    }
}
