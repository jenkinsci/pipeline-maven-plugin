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
 * handle {@code mvn spotbugs:spotbugs} invocations.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 *
 * @deprecated since TODO
 */
@Deprecated
public class SpotBugsAnalysisPublisher extends MavenPublisher {

    private static final Logger LOGGER = Logger.getLogger(SpotBugsAnalysisPublisher.class.getName());

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public SpotBugsAnalysisPublisher() {}

    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {
        throw new AbortException(
                """
        The spotbugsPublisher is deprecated as is the findbugs plugin and you should not use it.
        Alternatively, you should rely on warningsPublisher.
        Or, if continuing to use the findbugs plugin for now, run it explicitly rather than expecting withMaven to run it implicitly.
        """);
    }

    @DataBoundSetter
    public void setHealthy(String healthy) {}

    @DataBoundSetter
    public void setUnHealthy(String unHealthy) {}

    @DataBoundSetter
    public void setThresholdLimit(String thresholdLimit) {}

    @Symbol("spotbugsPublisher")
    @Extension
    // should be OptionalExtension, and not Extension, but findbugs plugin is deprecated, no more available in update
    // site, so we cannot install it automatically during test to enable this extension as optional
    // @OptionalExtension(requirePlugins = "findbugs")
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_spotbugs_analysis_description();
        }

        @Override
        public int ordinal() {
            return -1;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-spotbugs-results";
        }
    }
}
