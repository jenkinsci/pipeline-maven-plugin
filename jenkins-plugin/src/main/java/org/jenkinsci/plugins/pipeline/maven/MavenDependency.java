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
                getGroupId() + ":" +
                getArtifactId() + ":" +
                getType() +
                (getClassifier() == null ? "" : ":" + getClassifier()) + ":" +
                getBaseVersion() + ", " +
                "scope: " + scope + ", " +
                " optional: " + optional +
                " version: " + getVersion() +
                " snapshot: " + isSnapshot() +
                (getFile() == null ? "" : " " + getFile()) +
                '}';
    }

    public MavenArtifact asMavenArtifact() {
        MavenArtifact result = new MavenArtifact();

        result.setGroupId(getGroupId());
        result.setArtifactId(getArtifactId());
        result.setVersion(getVersion());
        result.setBaseVersion(getBaseVersion());
        result.setType(getType());
        result.setClassifier(getClassifier());
        result.setExtension(getExtension());
        result.setFile(getFile());
        result.setSnapshot(isSnapshot());

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
            return other.scope == null;
        } else return scope.equals(other.scope);
    }
}
