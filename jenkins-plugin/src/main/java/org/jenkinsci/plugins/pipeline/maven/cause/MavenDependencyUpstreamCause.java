package org.jenkinsci.plugins.pipeline.maven.cause;

import hudson.model.Cause;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.Objects;

public class MavenDependencyUpstreamCause extends Cause.UpstreamCause implements MavenDependencyCause {
    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenDependencyUpstreamCause(Run<?, ?> up, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version) {
        super(up);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MavenDependencyUpstreamCause that = (MavenDependencyUpstreamCause) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), groupId, artifactId, version);
    }
}
