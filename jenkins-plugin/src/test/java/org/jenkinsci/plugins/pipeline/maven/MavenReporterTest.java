package org.jenkinsci.plugins.pipeline.maven;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import hudson.util.StreamTaskListener;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.reporters.FindbugsAnalysisReporter;
import org.jenkinsci.plugins.pipeline.maven.reporters.GeneratedArtifactsReporter;
import org.jenkinsci.plugins.pipeline.maven.reporters.JunitTestsReporter;
import org.jenkinsci.plugins.pipeline.maven.reporters.TasksScannerReporter;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenReporterTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void listMavenReporters() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        List<MavenReporter> mavenReporters = MavenReporter.buildReportersList(Collections.<MavenReporter>emptyList(), new StreamTaskListener(baos));
        Assert.assertThat(mavenReporters.size(), CoreMatchers.is(4));

        Map<String, MavenReporter> reportersByDescriptorId = new HashMap<>();
        for(MavenReporter mavenReporter:mavenReporters) {
            reportersByDescriptorId.put(mavenReporter.getDescriptor().getId(), mavenReporter);
        }
        assertThat(reportersByDescriptorId.containsKey(new GeneratedArtifactsReporter.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new FindbugsAnalysisReporter.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new JunitTestsReporter.DescriptorImpl().getId()), is(true));
        assertThat(reportersByDescriptorId.containsKey(new TasksScannerReporter.DescriptorImpl().getId()), is(true));
    }
}
