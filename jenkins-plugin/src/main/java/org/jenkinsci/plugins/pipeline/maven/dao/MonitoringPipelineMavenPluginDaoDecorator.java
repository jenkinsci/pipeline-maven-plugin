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

public class MonitoringPipelineMavenPluginDaoDecorator extends AbstractPipelineMavenPluginDaoDecorator {

    private final AtomicLong findDurationInNanos = new AtomicLong();
    private final AtomicInteger findCount = new AtomicInteger();
    private final AtomicLong writeDurationInNanos = new AtomicLong();
    private final AtomicInteger writeCount = new AtomicInteger();

    public MonitoringPipelineMavenPluginDaoDecorator(@Nonnull PipelineMavenPluginDao delegate) {
        super(delegate);
    }

    @Override
    public void recordDependency(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String scope, boolean ignoreUpstreamTriggers, String classifier) {
        executeMonitored(() -> super.recordDependency(jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers, classifier));
    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        executeMonitored(() -> super.recordParentProject(jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers));
    }

    @Override
    public void recordGeneratedArtifact(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String baseVersion, @Nullable String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        executeMonitored(() -> super.recordGeneratedArtifact(jobFullName, buildNumber, groupId, artifactId, version, type, baseVersion, repositoryUrl, skipDownstreamTriggers, extension, classifier));
    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        executeMonitored(() -> super.recordBuildUpstreamCause(upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber));
    }

    @Override
    @Nonnull
    public List<MavenDependency> listDependencies(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.listDependencies(jobFullName, buildNumber));
    }

    @Override
    @Nonnull
    public List<MavenArtifact> getGeneratedArtifacts(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.getGeneratedArtifacts(jobFullName, buildNumber));
    }

    @Override
    public void renameJob(@Nonnull String oldFullName, @Nonnull String newFullName) {
        executeMonitored(() -> super.renameJob(oldFullName, newFullName));
    }

    @Override
    public void deleteJob(@Nonnull String jobFullName) {
        executeMonitored(() -> super.deleteJob(jobFullName));
    }

    @Override
    public void deleteBuild(@Nonnull String jobFullName, int buildNumber) {
        executeMonitored(() -> super.deleteBuild(jobFullName, buildNumber));
    }

    @Override
    @Nonnull
    @Deprecated
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.listDownstreamJobs(jobFullName, buildNumber));
    }

    @Nonnull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.listDownstreamJobsByArtifact(jobFullName, buildNumber));
    }

    @Nonnull
    @Override
    public SortedSet<String> listDownstreamJobs(String groupId, String artifactId, String version, String baseVersion, String type, String classifier) {
        return executeMonitored(() -> super.listDownstreamJobs(groupId, artifactId, version, baseVersion, type, classifier));
    }

    @Override
    @Nonnull
    public Map<String, Integer> listUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.listUpstreamJobs(jobFullName, buildNumber));
    }

    @Override
    @Nonnull
    public Map<String, Integer> listTransitiveUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return executeMonitored(() -> super.listTransitiveUpstreamJobs(jobFullName, buildNumber));
    }

    @Override
    public void cleanup() {
        executeMonitored(super::cleanup);
    }

    @Override
    public void updateBuildOnCompletion(@Nonnull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        executeMonitored(() -> super.updateBuildOnCompletion(jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis));
    }

    @Override
    public String toPrettyString() {
        return super.toPrettyString() +
                "\r\n Performances: " +
                "\r\n\t find: totalDurationInMs=" + TimeUnit.NANOSECONDS.toMillis(findDurationInNanos.get()) + ", count=" + findCount.get() +
                "\r\n\t write: totalDurationInMs=" + TimeUnit.NANOSECONDS.toMillis(writeDurationInNanos.get()) + ", count=" + writeCount.get();
    }

    private void executeMonitored(CallableWithoutResult callable) {
        executeMonitored(() -> {
            callable.call();
            return null;
        });
    }

    private <V> V executeMonitored(CallableWithResult<V> callable) {
        long nanosBefore = System.nanoTime();
        try {
            return callable.call();
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    private interface CallableWithResult<V> {
        V call();
    }

    private interface CallableWithoutResult {
        void call();
    }

}
