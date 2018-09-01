package org.jenkinsci.plugins.pipeline.maven;

import org.apache.commons.lang.builder.CompareToBuilder;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenArtifact implements Serializable, Comparable<MavenArtifact> {

    private static final long serialVersionUID = 1L;

    public String groupId, artifactId;
    /**
     * Gets the version of this artifact, for example "1.0-20100529-1213". Note that in case of meta versions like
     * "1.0-SNAPSHOT", the artifact's version depends on the state of the artifact. Artifacts that have been resolved or
     * deployed will usually have the meta version expanded.
     *
     * @see org.eclipse.aether.artifact.Artifact#getVersion()
     */
    public String version;
    /**
     * Gets the base version of this artifact, for example "1.0-SNAPSHOT". In contrast to the org.eclipse.aether.artifact.Artifact#getVersion(), the
     * base version will always refer to the unresolved meta version.
     *
     * @see org.eclipse.aether.artifact.Artifact#getBaseVersion()
     */
    public String baseVersion;
    public String type, classifier, extension;
    @Nullable
    public String file;
    public boolean snapshot;
    /**
     * URL on of the Maven repository on which the artifact has been deployed ("mvn deploy")
     */
    @Nullable
    public String repositoryUrl;

    /**
     * @see MavenArtifact#version
     */
    @Nonnull
    public String getFileName() {
        return artifactId + "-" + version + ((classifier == null || classifier.isEmpty()) ? "" : "-" + classifier) + "." + extension;
    }

    /**
     * @see MavenArtifact#baseVersion
     */
    @Nonnull
    public String getFileNameWithBaseVersion() {
        return artifactId + "-" + baseVersion + ((classifier == null || classifier.isEmpty()) ? "" : "-" + classifier) + "." + extension;
    }

    /**
     * @see org.apache.maven.artifact.Artifact#getId()
     */
    @Nonnull
    public String getId() {
        return groupId + ":" + artifactId  + ":" +
                type + ":" +
                ((classifier == null || classifier.isEmpty()) ? "" : classifier + ":") +
                (baseVersion == null ? version : baseVersion) ;
    }

    @Nonnull
    public String getShortDescription() {
        if (baseVersion == null) {
            return getId();
        } else {
            return groupId + ":" + artifactId  + ":" +
                    type + ":" +
                    ((classifier == null || classifier.isEmpty()) ? "" :  classifier + ":")  +
                     baseVersion + "(" + version + ")";
        }
    }


    /**
     * URL of the artifact on the maven repository on which it has been deployed if it has been deployed.
     * @return URL of the artifact or {@code null} if the artifact has not been deployed (if "{@code mvn deploy}" was not invoked)
     */
    @Nullable
    public String getUrl() {
        if (repositoryUrl == null)
            return null;
        return repositoryUrl + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + getFileNameWithBaseVersion();
    }

    @Override
    public String toString() {
        return "MavenArtifact{" +
                groupId + ":" +
                artifactId + ":" +
                type +
                (classifier == null ? "" : ":" + classifier) + ":" +
                baseVersion + "(version: " + version + ", snapshot:" + snapshot + ") " +
                (file == null ? "" : " " + file) +
                '}';
    }

    @Override
    public int compareTo(MavenArtifact o) {
        return new CompareToBuilder().
                append(this.groupId, o.groupId).
                append(this.artifactId, o.artifactId).
                append(this.baseVersion, o.baseVersion).
                append(this.version, o.version).
                append(this.type, o.type).
                append(this.classifier, o.classifier).
                toComparison();
    }

    /**
     * Artifact has been deployed to a Maven repository ("mvn deploy")
     * @see #getUrl()
     */
    public boolean isDeployed() {
        return this.repositoryUrl != null && !repositoryUrl.isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, baseVersion, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MavenArtifact other = (MavenArtifact) obj;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (baseVersion == null) {
            if (other.baseVersion != null)
                return false;
        } else if (!baseVersion.equals(other.baseVersion))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (classifier == null) {
            if (other.classifier != null)
                return false;
        } else if (!classifier.equals(other.classifier))
            return false;

        return true;
    }
}
