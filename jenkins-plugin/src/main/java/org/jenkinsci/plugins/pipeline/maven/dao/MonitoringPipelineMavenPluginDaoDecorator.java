package org.jenkinsci.plugins.pipeline.maven.dao;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.listeners.DaoHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.Optional.ofNullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class MonitoringPipelineMavenPluginDaoDecorator extends AbstractPipelineMavenPluginDaoDecorator {

    private final static List<Supplier<CacheStats>> CACHE_STATS_SUPPLIERS = new ArrayList<>();

    public static void registerCacheStatsSupplier(Supplier<CacheStats> supplier) {
        CACHE_STATS_SUPPLIERS.add(supplier);
    }

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
        StringBuilder builder = new StringBuilder(ofNullable(super.toPrettyString()).orElse(""));
        builder.append("\r\n Performances: ");
        builder.append("\r\n\t find: totalDurationInMs=").append(TimeUnit.NANOSECONDS.toMillis(findDurationInNanos.get())).append(", count=").append(findCount.get());
        builder.append("\r\n\t write: totalDurationInMs=").append(TimeUnit.NANOSECONDS.toMillis(writeDurationInNanos.get())).append(", count=").append(writeCount.get());
        builder.append("\r\n Caches: ");
        CACHE_STATS_SUPPLIERS.forEach(s -> builder.append("\r\n\t ").append(cachePrettyString(s.get())));
        return builder.toString();
    }

    private String cachePrettyString(CacheStats stats) {
        double h = stats.getHits();
        double m = stats.getMisses();
        double e = h + m == 0.0 ? 0.0 : h / (h + m);
        StringBuilder builder = new StringBuilder(stats.getName());
        builder.append(": hits=");
        builder.append(h);
        builder.append(", misses=");
        builder.append(m);
        builder.append(", efficency=");
        builder.append(NumberFormat.getPercentInstance().format(e));
        return builder.toString();
    }

    private void executeMonitored(CallableWithoutResult callable) {
        long nanosBefore = System.nanoTime();
        try {
            callable.call();
        } finally {
            long nanosAfter = System.nanoTime();
            writeCount.incrementAndGet();
            writeDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    private <V> V executeMonitored(CallableWithResult<V> callable) {
        long nanosBefore = System.nanoTime();
        try {
            return callable.call();
        } finally {
            long nanosAfter = System.nanoTime();
            findCount.incrementAndGet();
            findDurationInNanos.addAndGet(nanosAfter - nanosBefore);
        }
    }

    private interface CallableWithResult<V> {
        V call();
    }

    private interface CallableWithoutResult {
        void call();
    }

}
