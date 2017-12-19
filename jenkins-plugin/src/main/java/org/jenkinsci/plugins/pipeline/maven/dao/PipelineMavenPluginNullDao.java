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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginNullDao implements PipelineMavenPluginDao {
    @Override
    public void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope, boolean ignoreUpstreamTriggers) {

    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {

    }

    @Override
    public void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String baseVersion, boolean skipDownstreamTriggers) {

    }

    @Override
    public void renameJob(String oldFullName, String newFullName) {

    }

    @Override
    public void deleteJob(String jobFullName) {

    }

    @Override
    public void deleteBuild(String jobFullName, int buildNumber) {

    }

    @Nonnull
    @Override
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return Collections.emptyList();
    }

    @Override
    public void cleanup() {

    }

    @Override
    public String toPrettyString() {
        return "PipelineMavenPluginNullDao";
    }
}
