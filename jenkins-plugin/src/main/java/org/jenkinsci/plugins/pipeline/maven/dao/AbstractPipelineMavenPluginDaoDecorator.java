package org.jenkinsci.plugins.pipeline.maven.dao;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public abstract class AbstractPipelineMavenPluginDaoDecorator implements PipelineMavenPluginDao {

    protected final PipelineMavenPluginDao delegate;

    public AbstractPipelineMavenPluginDaoDecorator(@Nonnull PipelineMavenPluginDao delegate) {
        this.delegate = delegate;
    }

    @Override
    public void recordDependency(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String scope, boolean ignoreUpstreamTriggers, String classifier) {
        delegate.recordDependency(jobFullName, buildNumber, groupId, artifactId, version, type, scope, ignoreUpstreamTriggers, classifier);
    }

    @Override
    public void recordParentProject(@Nonnull String jobFullName, int buildNumber, @Nonnull String parentGroupId, @Nonnull String parentArtifactId, @Nonnull String parentVersion, boolean ignoreUpstreamTriggers) {
        delegate.recordParentProject(jobFullName, buildNumber, parentGroupId, parentArtifactId, parentVersion, ignoreUpstreamTriggers);
    }

    @Override
    public void recordGeneratedArtifact(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String baseVersion, @Nullable String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        delegate.recordGeneratedArtifact(jobFullName, buildNumber, groupId, artifactId, version, type, baseVersion, repositoryUrl, skipDownstreamTriggers, extension, classifier);
    }

    @Override
    public void recordBuildUpstreamCause(String upstreamJobName, int upstreamBuildNumber, String downstreamJobName, int downstreamBuildNumber) {
        delegate.recordBuildUpstreamCause(upstreamJobName, upstreamBuildNumber, downstreamJobName, downstreamBuildNumber);
    }

    @Nonnull
    @Override
    public List<MavenDependency> listDependencies(@Nonnull String jobFullName, int buildNumber) {
        return delegate.listDependencies(jobFullName, buildNumber);
    }

    @Nonnull
    @Override
    public List<MavenArtifact> getGeneratedArtifacts(@Nonnull String jobFullName, int buildNumber) {
        return delegate.getGeneratedArtifacts(jobFullName, buildNumber);
    }

    @Override
    public void renameJob(@Nonnull String oldFullName, @Nonnull String newFullName) {
        delegate.renameJob(oldFullName, newFullName);
    }

    @Override
    public void deleteJob(@Nonnull String jobFullName) {
        delegate.deleteJob(jobFullName);
    }

    @Override
    public void deleteBuild(@Nonnull String jobFullName, int buildNumber) {
        delegate.deleteBuild(jobFullName, buildNumber);
    }

    @Nonnull
    @Override
    public List<String> listDownstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return delegate.listDownstreamJobs(jobFullName, buildNumber);
    }

    @Nonnull
    @Override
    public Map<MavenArtifact, SortedSet<String>> listDownstreamJobsByArtifact(@Nonnull String jobFullName, int buildNumber) {
        return delegate.listDownstreamJobsByArtifact(jobFullName, buildNumber);
    }

    @Nonnull
    @Override
    public SortedSet<String> listDownstreamJobs(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nullable String baseVersion, @Nonnull String type, @Nullable String classifier) {
        return delegate.listDownstreamJobs(groupId, artifactId, version, baseVersion, type, classifier);
    }

    @Nonnull
    @Override
    public Map<String, Integer> listUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return delegate.listUpstreamJobs(jobFullName, buildNumber);
    }

    @Nonnull
    @Override
    public Map<String, Integer> listTransitiveUpstreamJobs(@Nonnull String jobFullName, int buildNumber) {
        return delegate.listTransitiveUpstreamJobs(jobFullName, buildNumber);
    }

	@Override
	public Map<String, Integer> listTransitiveUpstreamJobs(String jobFullName, int buildNumber,
			UpstreamMemory upstreamMemory) {
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
    public void updateBuildOnCompletion(@Nonnull String jobFullName, int buildNumber, int buildResultOrdinal, long startTimeInMillis, long durationInMillis) {
        delegate.updateBuildOnCompletion(jobFullName, buildNumber, buildResultOrdinal, startTimeInMillis, durationInMillis);
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
