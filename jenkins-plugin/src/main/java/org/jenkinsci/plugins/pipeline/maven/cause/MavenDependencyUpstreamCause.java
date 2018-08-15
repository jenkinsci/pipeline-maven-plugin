package org.jenkinsci.plugins.pipeline.maven.cause;

import hudson.model.Cause;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.Objects;

public class MavenDependencyUpstreamCause extends Cause.UpstreamCause implements MavenDependencyCause {
    private final String mavenArtifactGroupId;
    private final String mavenArtifactArtifactId;
    private final String mavenArtifactVersion;
    private final String mavenArtifactType;

    public MavenDependencyUpstreamCause(Run<?, ?> up, @Nonnull String mavenArtifactGroupId, @Nonnull String mavenArtifactArtifactId, @Nonnull String mavenArtifactVersion, @Nonnull String mavenArtifactType) {
        super(up);
        this.mavenArtifactGroupId = mavenArtifactGroupId;
        this.mavenArtifactArtifactId = mavenArtifactArtifactId;
        this.mavenArtifactVersion = mavenArtifactVersion;
        this.mavenArtifactType = mavenArtifactType;
    }

    @Override
    public String getShortDescription() {
        return "Started by upstream project \"" + getUpstreamProject() + "\" build number " + getUpstreamBuild() + " modifying Maven dependency " + getId();
    }

    @Override
    public String getMavenArtifactGroupId() {
        return mavenArtifactGroupId;
    }

    @Override
    public String getMavenArtifactArtifactId() {
        return mavenArtifactArtifactId;
    }

    @Override
    public String getMavenArtifactVersion() {
        return mavenArtifactVersion;
    }

    @Override
    public String getMavenArtifactType() {
        return mavenArtifactType;
    }

    /**
     * @see org.apache.maven.artifact.Artifact#getId()
     */
    public String getId() {
        return mavenArtifactGroupId + ":" + mavenArtifactArtifactId + ":" + mavenArtifactVersion + " - " + mavenArtifactType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MavenDependencyUpstreamCause that = (MavenDependencyUpstreamCause) o;
        return Objects.equals(mavenArtifactGroupId, that.mavenArtifactGroupId) &&
                Objects.equals(mavenArtifactArtifactId, that.mavenArtifactArtifactId) &&
                Objects.equals(mavenArtifactVersion, that.mavenArtifactVersion) &&
                Objects.equals(mavenArtifactType, that.mavenArtifactType);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), mavenArtifactGroupId, mavenArtifactArtifactId, mavenArtifactVersion, mavenArtifactType);
    }
}
