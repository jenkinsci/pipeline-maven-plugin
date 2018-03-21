package org.jenkinsci.plugins.pipeline.maven;

import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependency extends MavenArtifact {

    private String scope;
    public boolean optional;

    @Nonnull
    public String getScope() {
        return scope == null ? "compile" : scope;
    }

    public void setScope(String scope) {
        this.scope = scope == null || scope.isEmpty() ? null : scope;
    }

    @Override
    public String toString() {
        return "MavenDependency{" +
                groupId + ":" +
                artifactId + ":" +
                type +
                (classifier == null ? "" : ":" + classifier) + ":" +
                baseVersion + ", " +
                "scope: " + scope + ", " +
                " optional: " + optional +
                " version: " + version +
                " snapshot: " + snapshot +
                (file == null ? "" : " " + file) +
                '}';
    }

    public MavenArtifact asMavenArtifact() {
        MavenArtifact result = new MavenArtifact();

        result.groupId = groupId;
        result.artifactId = artifactId;
        result.version = version;
        result.baseVersion = baseVersion;
        result.type = type;
        result.classifier = classifier;
        result.extension = extension;
        result.file = file;
        result.snapshot = snapshot;

        return result;
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), optional, scope);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        MavenDependency other = (MavenDependency) obj;
        if (optional != other.optional)
            return false;
        if (scope == null) {
            if (other.scope != null)
                return false;
        } else if (!scope.equals(other.scope))
            return false;
        return true;
    }
}
