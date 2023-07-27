package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.util.StreamTaskListener;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@WithJenkins
public class MavenPublisherStrategyTest {

    @Test
    public void listMavenPublishers(JenkinsRule r) throws Exception {
        assertThat(r.jenkins).isNotNull();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        List<MavenPublisher> mavenPublishers = MavenPublisherStrategy.IMPLICIT.buildPublishersList(Collections.emptyList(), new StreamTaskListener(baos));
        assertThat(mavenPublishers).hasSize(12);

        Map<String, MavenPublisher> reportersByDescriptorId = new HashMap<>();
        for (MavenPublisher mavenPublisher : mavenPublishers) {
            reportersByDescriptorId.put(mavenPublisher.getDescriptor().getId(), mavenPublisher);
        }
        assertThat(reportersByDescriptorId).containsKey(new GeneratedArtifactsPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new FindbugsAnalysisPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new SpotBugsAnalysisPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new JunitTestsPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new TasksScannerPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new PipelineGraphPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new InvokerRunsPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new ConcordionTestsPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new JGivenTestsPublisher.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new MavenLinkerPublisher2.DescriptorImpl().getId());
        assertThat(reportersByDescriptorId).containsKey(new JacocoReportPublisher.DescriptorImpl().getId());
    }
}
