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

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginNullDao implements PipelineMavenPluginDao {
    private static Logger LOGGER = Logger.getLogger(PipelineMavenPluginNullDao.class.getName());

    @Override
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope, boolean ignoreUpstreamTriggers, String classifier) {
        LOGGER.log(Level.INFO, "recordDependency({0}#{1}, {2}:{3}:{4}:{5}, {6}, ignoreUpstreamTriggers:{7}})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers});
    }

    @Nonnull
    @Override
    public List<MavenDependency> listDependencies(@Nonnull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        LOGGER.log(Level.INFO, "recordParentProject({0}#{1}, {2}:{3} ignoreUpstreamTriggers:{5}})",
                new Object[]{jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers});

    }

    @Override
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String baseVersion, String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        LOGGER.log(Level.INFO, "recordGeneratedArtifact({0}#{1}, {2}:{3}:{4}:{5}, version:{6}, repositoryUrl:{7}, skipDownstreamTriggers:{8})",
                new Object[]{jobFullName, buildNumber, groupId, artifactId, baseVersion, type, version, repositoryUrl, skipDownstreamTriggers});

    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        LOGGER.log(Level.INFO, "recordBuildUpstreamCause(upstreamBuild: {0}#{1}, downstreamBuild: {2}#{3})",
                new Object[]{upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber});
    }

    @Override
    public void renameJob(String oldFullName, String newFullName) {
        LOGGER.log(Level.INFO, "renameJob({0}, {1})", new Object[]{oldFullName, newFullName});

    }

    @Override
    public void deleteJob(String jobFullName) {
        LOGGER.log(Level.INFO, "deleteJob({0})", new Object[]{jobFullName});

    }

    @Override
    public void deleteBuild(String jobFullName, int buildNumber) {
        LOGGER.log(Level.INFO, "deleteBuild({0}#{1})", new Object[]{jobFullName, buildNumber});

    }

    @Nonnull
    @Override
    @Deprecated
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@Nonnull String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }

    @Nonnull
    @Override
    public Map<String, Integer> listUpstreamJobs(String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }
    
    @Nonnull
    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(String jobFullName, int buildNumber) {
        return Collections.emptyMap();
    }

    @Override
    public void cleanup() {
        LOGGER.log(Level.INFO, "cleanup()");
    }

    @Nonnull
    @Override
    public List<MavenArtifact> getGeneratedArtifacts(@Nonnull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Override
    public void updateBuildOnCompletion(@Nonnull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        LOGGER.log(Level.INFO, "updateBuildOnCompletion({0}, {1}, result: {2}, startTime): {3}, duration: {4}",
                new Object[]{jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis});
    }

    @Override
    public String toPrettyString() {
        return "PipelineMavenPluginNullDao";
    }
}
