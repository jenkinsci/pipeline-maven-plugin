package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

import java.io.IOException;


/**
 *
 * Likely to disappear as we improve the rendering of Maven build details.
 *
 * Renamed "MavenLinkerPublisher" into "MavenLinkerPublisher2" to prevent
 * <pre><code>
 * WARNING: could not load /var/lib/jenkins/jobs/github/jobs/apache.org/jobs/maven/branches/master/builds/38
 * java.lang.ClassCastException: org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher cannot be cast to hudson.model.Action
 *         at hudson.model.Run.onLoad(Run.java:353)
 *         at org.jenkinsci.plugins.workflow.job.WorkflowRun.onLoad(WorkflowRun.java:594)
 *         at hudson.model.RunMap.retrieve(RunMap.java:225)
 *         at hudson.model.RunMap.retrieve(RunMap.java:57)
 *         at jenkins.model.lazy.AbstractLazyLoadRunMap.load(AbstractLazyLoadRunMap.java:500)
 *         at jenkins.model.lazy.AbstractLazyLoadRunMap.load(AbstractLazyLoadRunMap.java:482)
 * </code></pre>
 */
public class MavenLinkerPublisher2 extends MavenPublisher {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public MavenLinkerPublisher2() {
        // default DataBoundConstructor
    }

    /**
     * Synchronize because {@link Run#addOrReplaceAction(hudson.model.Action)} is not thread safe
     */
    @Override
    public synchronized void process(StepContext context, Element mavenSpyLogsElt) throws IOException, InterruptedException {
        Run<?, ?> run = context.get(Run.class);
        // we replace instead of because we want to refresh the cache org.jenkinsci.plugins.pipeline.maven.publishers.MavenReport.getGeneratedArtifacts()
        run.addOrReplaceAction(new MavenReport(run));
    }

    @Symbol("mavenLinkerPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {

        @Override
        public String getSkipFileName() {
            return ".skip-maven-linker-publisher";
        }

        @Override
        public String getDisplayName() {
            return "Maven Linker Publisher";
        }
    }
}
