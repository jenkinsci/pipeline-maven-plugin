package org.jenkinsci.plugins.pipeline.maven.dao;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

public abstract class AbstractPipelineMavenPluginDaoDecorator implements PipelineMavenPluginDao {

    protected final PipelineMavenPluginDao delegate;

    public AbstractPipelineMavenPluginDaoDecorator(@NonNull PipelineMavenPluginDao delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Builder getBuilder() {
        return delegate.getBuilder();
    }

    @Override
    public void recordDependency(
            @NonNull String jobFullName,
            int buildNumber,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @NonNull String type,
            @NonNull String scope,
            boolean ignoreUpstreamTriggers,
            String classifier) {
        delegate.recordDependency(
                jobFullName,
                buildNumber,
                groupId,
                artifactId,
                version,
                type,
                scope,
                ignoreUpstreamTriggers,
                classifier);
    }

    @Override
    public void recordParentProject(
            @NonNull String jobFullName,
            int buildNumber,
            @NonNull String parentGroupId,
            @NonNull String parentArtifactId,
            @NonNull String parentVersion,
            boolean ignoreUpstreamTriggers) {
        delegate.recordParentProject(
                jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers);
    }

    @Override
    public void recordGeneratedArtifact(
            @NonNull String jobFullName,
            int buildNumber,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @NonNull String type,
            @NonNull String baseVersion,
            @Nullable String repositoryUrl,
            boolean skipDownstreamTriggers,
            String extension,
            String classifier) {
        delegate.recordGeneratedArtifact(
                jobFullName,
                buildNumber,
                groupId,
                artifactId,
                version,
                type,
                baseVersion,
                repositoryUrl,
                skipDownstreamTriggers,
                extension,
                classifier);
    }

    @Override
    public void recordBuildUpstreamCause(
            String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        delegate.recordBuildUpstreamCause(
                upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber);
    }

    @NonNull
    @Override
    public List<MavenDependency> listDependencies(@NonNull String jobFullName, int buildNumber) {
        return delegate.listDependencies(jobFullName, buildNumber);
    }

    @NonNull
    @Override
    public List<MavenArtifact> getGeneratedArtifacts(@NonNull String jobFullName, int buildNumber) {
        return delegate.getGeneratedArtifacts(jobFullName, buildNumber);
    }

    @Override
    public void renameJob(@NonNull String oldFullName, @NonNull String newFullName) {
        delegate.renameJob(oldFullName, newFullName);
    }

    @Override
    public void deleteJob(@NonNull String jobFullName) {
        delegate.deleteJob(jobFullName);
    }

    @Override
    public void deleteBuild(@NonNull String jobFullName, int buildNumber) {
        delegate.deleteBuild(jobFullName, buildNumber);
    }

    @NonNull
    @Override
    public List<String> listDownstreamJobs(@NonNull String jobFullName, int buildNumber) {
        return delegate.listDownstreamJobs(jobFullName, buildNumber);
    }

    @NonNull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(
            @NonNull String jobFullName, int buildNumber) {
        return delegate.listDownstreamJobsByArtifact(jobFullName, buildNumber);
    }

    @NonNull
    @Override
    public SortedSet<String> listDownstreamJobs(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @Nullable String baseVersion,
            @NonNull String type,
            @Nullable String classifier) {
        return delegate.listDownstreamJobs(groupId, artifactId, version, baseVersion, type, classifier);
    }

    @NonNull
    @Override
    public Map<String, Integer> listUpstreamJobs(@NonNull String jobFullName, int buildNumber) {
        return delegate.listUpstreamJobs(jobFullName, buildNumber);
    }

    @NonNull
    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(@NonNull String jobFullName, int buildNumber) {
        return delegate.listTransitiveUpstreamJobs(jobFullName, buildNumber);
    }

    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(
            String jobFullName, int buildNumber, UpstreamMemory upstreamMemory) {
        return delegate.listTransitiveUpstreamJobs(jobFullName, buildNumber, upstreamMemory);
    }

    @Override
    public void cleanup() {
        delegate.cleanup();
    }

    @Override
    public String toPrettyString() {
        return delegate.toPrettyString();
    }

    @Override
    public void updateBuildOnCompletion(
            @NonNull String jobFullName,
            int buildNumber,
            int buildResultOrdinal,
            long startTimeInMillis,
            long durationInMillis) {
        delegate.updateBuildOnCompletion(
                jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis);
    }

    @Override
    public boolean isEnoughProductionGradeForTheWorkload() {
        return delegate.isEnoughProductionGradeForTheWorkload();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
