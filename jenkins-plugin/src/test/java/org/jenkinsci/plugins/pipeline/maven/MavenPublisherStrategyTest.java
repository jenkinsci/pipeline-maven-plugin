package org.jenkinsci.plugins.pipeline.maven;

import hudson.util.StreamTaskListener;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.publishers.ConcordionTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.FindbugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.GeneratedArtifactsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.InvokerRunsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JGivenTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JacocoReportPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.JunitTestsPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenLinkerPublisher2;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.SpotBugsAnalysisPublisher;
import org.jenkinsci.plugins.pipeline.maven.publishers.TasksScannerPublisher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenPublisherStrategyTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void listMavenPublishers() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        List<MavenPublisher> mavenPublishers = MavenPublisherStrategy.IMPLICIT.buildPublishersList(Collections.emptyList(), new StreamTaskListener(baos));
        assertThat(mavenPublishers.size(), CoreMatchers.is(12));

        Map<String, MavenPublisher> reportersByDescriptorId = new HashMap<>();
        for(MavenPublisher mavenPublisher : mavenPublishers) {
            reportersByDescriptorId.put(mavenPublisher.getDescriptor().getId(), mavenPublisher);
        }
        assertThat(reportersByDescriptorId.containsKey(new GeneratedArtifactsPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new FindbugsAnalysisPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new SpotBugsAnalysisPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new JunitTestsPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new TasksScannerPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new PipelineGraphPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new InvokerRunsPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new ConcordionTestsPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new JGivenTestsPublisher.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new MavenLinkerPublisher2.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new JacocoReportPublisher.DescriptorImpl().getId()), is(true));
    }
}
