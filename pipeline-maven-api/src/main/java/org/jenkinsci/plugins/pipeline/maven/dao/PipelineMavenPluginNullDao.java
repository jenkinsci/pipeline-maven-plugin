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

package org.jenkinsci.plugins.pipeline.maven.dao;

import hudson.Extension;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class PipelineMavenPluginNullDao implements PipelineMavenPluginDao {
    private static Logger LOGGER = Logger.getLogger(PipelineMavenPluginNullDao.class.getName());

    @Override
    public String getDescription() {
        // TODO i18n
        return "Pipeline Maven Plugin no storage mode";
    }

    @Override
    public PipelineMavenPluginDao.Builder getBuilder() {
        return new Builder() {
            @Override
            public PipelineMavenPluginDao build(Config config) {
                return new PipelineMavenPluginNullDao();
            }

            @Override
            public FormValidation validateConfiguration(Config config) {
                return FormValidation.ok();
            }
        };
    }

    @Override
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope, boolean ignoreUpstreamTriggers, String classifier) {
        LOGGER.log(Level.FINEST, "NOT recordDependency({0}#{1}, {2}:{3}:{4}:{5}, {6}, ignoreUpstreamTriggers:{7}})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers});
    }

    @NonNull
    @Override
    public List<MavenDependency> listDependencies(@NonNull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Override
    public void recordParentProject(@NonNull String jobFullName, int buildNumber, @NonNull String parentGroupId, @NonNull String parentArtifactId, @NonNull String parentVersion, boolean ignoreUpstreamTriggers) {
        LOGGER.log(Level.FINEST, "NOT recordParentProject({0}#{1}, {2}:{3} ignoreUpstreamTriggers:{5}})",
                new Object[]{jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers});

    }

    @Override
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String baseVersion, String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        LOGGER.log(Level.FINEST, "NOT recordGeneratedArtifact({0}#{1}, {2}:{3}:{4}:{5}, version:{6}, repositoryUrl:{7}, skipDownstreamTriggers:{8})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, baseVersion, type, version, repositoryUrl, skipDownstreamTriggers});

    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        LOGGER.log(Level.FINEST, "NOT recordBuildUpstreamCause(upstreamBuild: {0}#{1}, downstreamBuild: {2}#{3})",
                new Object[]{upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber});
    }

    @Override
    public void renameJob(String oldFullName, String newFullName) {
        LOGGER.log(Level.FINEST, "NOT renameJob({0}, {1})", new Object[]{oldFullName, newFullName});

    }

    @Override
    public void deleteJob(String jobFullName) {
        LOGGER.log(Level.FINEST, "NOT deleteJob({0})", new Object[]{jobFullName});

    }

    @Override
    public void deleteBuild(String jobFullName, int buildNumber) {
        LOGGER.log(Level.FINEST, "NOT deleteBuild({0}#{1})", new Object[]{jobFullName, buildNumber});

    }

    @NonNull
    @Override
    @Deprecated
    public List<String> listDownstreamJobs(@NonNull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@NonNull String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public SortedSet<String> listDownstreamJobs(String groupId, String artifactId, String version, String baseVersion, String type, String classifier) {
        return new TreeSet<>();
    }

    @NonNull
    @Override
    public Map<String, Integer> listUpstreamJobs(String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(String jobFullName, int buildNumber,
            UpstreamMemory upstreamMemory) {
        return Collections.emptyMap();
    }

    @Override
    public void cleanup() {
        LOGGER.log(Level.FINEST, "cleanup()");
    }

    @NonNull
    @Override
    public List<MavenArtifact> getGeneratedArtifacts(@NonNull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Override
    public void updateBuildOnCompletion(@NonNull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        LOGGER.log(Level.FINEST, "NOOT updateBuildOnCompletion({0}, {1}, result: {2}, startTime): {3}, duration: {4}",
                new Object[]{jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis});
    }

    @Override
    public String toPrettyString() {
        return "PipelineMavenPluginNullDao";
    }

    @Override
    public boolean isEnoughProductionGradeForTheWorkload() {
        return true;
    }

    @Override
    public void close() throws IOException {
        // no op
    }

}
