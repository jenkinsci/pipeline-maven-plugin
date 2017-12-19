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

import hudson.model.Item;
import hudson.model.Run;
import org.jenkinsci.plugins.pipeline.maven.publishers.PipelineGraphPublisher;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface PipelineMavenPluginDao {

    /**
     * Record a Maven dependency of a build.
     *
     * @param jobFullName            see {@link Item#getFullName()}
     * @param buildNumber            see {@link Run#getNumber()}
     * @param groupId                Maven dependency groupId
     * @param artifactId             Maven dependency artifactId
     * @param version                Maven dependency version
     * @param type                   Maven dependency type (e.g. "jar", "war", "pom", hpi"...)
     * @param scope                  Maven dependency scope ("compile", "test", "provided"...)
     * @param ignoreUpstreamTriggers see {@link PipelineGraphPublisher#isIgnoreUpstreamTriggers()} ()}
     */
    void recordDependency(@Nonnull String jobFullName, int buildNumber,
                          @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String scope,
                          boolean ignoreUpstreamTriggers);

    /**
     * Record a Maven parent project of a pom processed by this build of a build.
     *
     * @param jobFullName            see {@link Item#getFullName()}
     * @param buildNumber            see {@link Run#getNumber()}
     * @param parentGroupId                Maven dependency groupId
     * @param parentArtifactId             Maven dependency artifactId
     * @param parentVersion                Maven dependency version
     * @param ignoreUpstreamTriggers see {@link PipelineGraphPublisher#isIgnoreUpstreamTriggers()} ()}
     */
    void recordParentProject(@Nonnull String jobFullName, int buildNumber,
                             @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion,
                             boolean ignoreUpstreamTriggers);
    /**
     * Record a Maven artifact generated in a build.
     *
     * @param jobFullName            see {@link Item#getFullName()}
     * @param buildNumber            see {@link Run#getNumber()}
     * @param groupId                Maven artifact groupId
     * @param artifactId             Maven artifact artifactId
     * @param version                Maven artifact version, the "expanded version" for snapshots who have been "mvn deploy" or equivalent
     *                               (e.g. "1.1-20170808.155524-66" for "1.1-SNAPSHOT" deployed to a repo)
     * @param type                   Maven artifact type (e.g. "jar", "war", "pom", hpi"...)
     * @param baseVersion            Maven artifact version, the NOT "expanded version" for snapshots who have been "mvn deploy" or equivalent
     *                               (e.g. baseVersion is "1.1-SNAPSHOT" for a "1.1-SNAPSHOT" artifact that has been deployed to a repo and expanded
     *                               to "1.1-20170808.155524-66")
     * @param skipDownstreamTriggers see {@link PipelineGraphPublisher#isSkipDownstreamTriggers()}
     */
    void recordGeneratedArtifact(@Nonnull String jobFullName, int buildNumber,
                                 @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String baseVersion,
                                 boolean skipDownstreamTriggers);

    /**
     * Sync database when a job is renamed (see {@link hudson.model.listeners.ItemListener#onRenamed(Item, String, String)})
     *
     * @param oldFullName see {@link Item#getFullName()}
     * @param newFullName see {@link Item#getFullName()}
     * @see hudson.model.listeners.ItemListener#onRenamed(Item, String, String)
     */
    void renameJob(@Nonnull String oldFullName, @Nonnull String newFullName);

    /**
     * Sync database when a job is deleted (see {@link hudson.model.listeners.ItemListener#onDeleted(Item)})
     *
     * @param jobFullName see {@link Item#getFullName()}
     * @see hudson.model.listeners.ItemListener#onDeleted(Item)
     */
    void deleteJob(@Nonnull String jobFullName);

    /**
     * Sync database when a build is deleted (see {@link hudson.model.listeners.RunListener#onDeleted(Run)})
     *
     * @param jobFullName see {@link Item#getFullName()}
     * @see hudson.model.listeners.RunListener#onDeleted(Run)
     */
    void deleteBuild(@Nonnull String jobFullName, int buildNumber);

    /**
     * List the downstream jobs who have a dependency on an artifact that has been generated by the given build
     * (build identified by the given {@code jobFullName}, {@code buildNumber})
     *
     * @param jobFullName see {@link Item#getFullName()}
     * @param buildNumber see {@link Run#getNumber()}
     * @return list of job full names (see {@link Item#getFullName()})
     * @see Item#getFullName()
     */
    @Nonnull
    List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber);

    /**
     * Routine task to cleanup the database and reclaim disk space (if possible in the underlying database).
     */
    void cleanup();

    /**
     * Human readable toString
     */
    String toPrettyString();
}
