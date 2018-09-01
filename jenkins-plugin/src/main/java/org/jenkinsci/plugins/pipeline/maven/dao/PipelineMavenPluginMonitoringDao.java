package org.jenkinsci.plugins.pipeline.maven.dao;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PipelineMavenPluginMonitoringDao implements PipelineMavenPluginDao {

    protected final PipelineMavenPluginDao delegate;

    protected final AtomicLong findDurationInNanos = new AtomicLong();
    protected final AtomicInteger findCount = new AtomicInteger();
    protected final AtomicLong writeDurationInNanos = new AtomicLong();
    protected final AtomicInteger writeCount = new AtomicInteger();


    public PipelineMavenPluginMonitoringDao(@Nonnull PipelineMavenPluginDao delegate) {
        this.delegate = delegate;
    }

    @Override
    public void recordDependency(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String scope, boolean ignoreUpstreamTriggers, String classifier) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.recordDependency(jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers, classifier);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.recordParentProject(jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void recordGeneratedArtifact(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String baseVersion, @Nullable String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.recordGeneratedArtifact(jobFullName, buildNumber, groupId, artifactId, version, type, baseVersion, repositoryUrl, skipDownstreamTriggers, extension, classifier);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.recordBuildUpstreamCause(upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    @Nonnull
    public List<MavenDependency> listDependencies(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.listDependencies(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    @Nonnull
    public List<MavenArtifact> getGeneratedArtifacts(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.getGeneratedArtifacts(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void renameJob(@Nonnull String oldFullName, @Nonnull String newFullName) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.renameJob(oldFullName, newFullName);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void deleteJob(@Nonnull String jobFullName) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.deleteJob(jobFullName);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void deleteBuild(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.deleteBuild(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    @Nonnull
    @Deprecated
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.listDownstreamJobs(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Nonnull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.listDownstreamJobsByArtifact(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    @Nonnull
    public Map<String, Integer> listUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.listUpstreamJobs(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    @Nonnull
    public Map<String, Integer> listTransitiveUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        long nanosBefore = System.nanoTime();
        try {
            return delegate.listTransitiveUpstreamJobs(jobFullName, buildNumber);
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void cleanup() {
        long nanosBefore = System.nanoTime();
        try {
            delegate.cleanup();
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public void updateBuildOnCompletion(@Nonnull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        long nanosBefore = System.nanoTime();
        try {
            delegate.updateBuildOnCompletion(jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis);
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    @Override
    public String toPrettyString() {
        return delegate.toPrettyString() +
                "\r\n Performances: " +
                "\r\n\t find: totalDurationInMs=" + TimeUnit.NANOSECONDS.toMillis(findDurationInNanos.get()) + ", count=" + findCount.get() +
                "\r\n\t write: totalDurationInMs=" + TimeUnit.NANOSECONDS.toMillis(writeDurationInNanos.get()) + ", count=" + writeCount.get();
    }
}
