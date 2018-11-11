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

    public MavenArtifact() {

    }

    /**
     * @param identifier Maven {@code $groupId:$artifactId:$version } (GAV) or {@code $groupId:$artifactId:$type:$version} (GATV)
     * @throws IllegalArgumentException unsupported identifier
     */
    public MavenArtifact(String identifier) throws IllegalArgumentException {
        String[] args = identifier.split(":");
        switch (args.length) {
            case 4:
                this.setGroupId(args[0]);
                this.setArtifactId(args[1]);
                this.setType(args[2]);
                this.setVersion(args[3]);
                break;
            case 3:
                this.setGroupId(args[0]);
                this.setArtifactId(args[1]);
                this.setVersion(args[2]);
                break;
            default:
                throw new IllegalArgumentException("unsupported format: " + identifier);
        }
        if (getVersion().endsWith("SNAPSHOT")) {
            setSnapshot(true);
        }
    }

    private String groupId;
    private String artifactId;

    /**
     * Gets the version of this artifact, for example "1.0-20100529-1213". Note that in case of meta versions like
     * "1.0-SNAPSHOT", the artifact's version depends on the state of the artifact. Artifacts that have been resolved or
     * deployed will usually have the meta version expanded.
     *
     * @see org.eclipse.aether.artifact.Artifact#getVersion()
     */
    private String version;
    /**
     * Gets the base version of this artifact, for example "1.0-SNAPSHOT". In contrast to the org.eclipse.aether.artifact.Artifact#getVersion(), the
     * base version will always refer to the unresolved meta version.
     *
     * @see org.eclipse.aether.artifact.Artifact#getBaseVersion()
     */
    private String baseVersion;
    private String type;
    private String classifier;
    private String extension;
    @Nullable
    private String file;
    private boolean snapshot;
    /**
     * URL on of the Maven repository on which the artifact has been deployed ("mvn deploy")
     */
    @Nullable
    private String repositoryUrl;

    /**
     * @see MavenArtifact#version
     */
    @Nonnull
    public String getFileName() {
        return getArtifactId() + "-" + getVersion() + ((getClassifier() == null || getClassifier().isEmpty()) ? "" : "-" + getClassifier()) + "." + getExtension();
    }

    /**
     * @see MavenArtifact#baseVersion
     */
    @Nonnull
    public String getFileNameWithBaseVersion() {
        return getArtifactId() + "-" + getBaseVersion() + ((getClassifier() == null || getClassifier().isEmpty()) ? "" : "-" + getClassifier()) + "." + getExtension();
    }

    /**
     * @see org.apache.maven.artifact.Artifact#getId()
     */
    @Nonnull
    public String getId() {
        return getGroupId() + ":" + getArtifactId() + ":" +
                getType() + ":" +
                ((getClassifier() == null || getClassifier().isEmpty()) ? "" : getClassifier() + ":") +
                (getBaseVersion() == null ? getVersion() : getBaseVersion());
    }

    /**
     * Gets a human readable description of this artifact
     */
    @Nonnull
    public String getShortDescription() {
        if (getBaseVersion() == null) {
            return getId();
        } else {
            return getGroupId() + ":" + getArtifactId() + ":" +
                    getType() + ":" +
                    ((getClassifier() == null || getClassifier().isEmpty()) ? "" : getClassifier() + ":") +
                    getBaseVersion() + "(" + getVersion() + ")";
        }
    }


    /**
     * URL of the artifact on the maven repository on which it has been deployed if it has been deployed.
     *
     * @return URL of the artifact or {@code null} if the artifact has not been deployed (if "{@code mvn deploy}" was not invoked)
     */
    @Nullable
    public String getUrl() {
        if (getRepositoryUrl() == null)
            return null;
        return getRepositoryUrl() + "/" + getGroupId().replace('.', '/') + "/" + getArtifactId() + "/" + getVersion() + "/" + getFileNameWithBaseVersion();
    }

    @Override
    public String toString() {
        return "MavenArtifact{" +
                getGroupId() + ":" +
                getArtifactId() + ":" +
                getType() +
                (getClassifier() == null ? "" : ":" + getClassifier()) + ":" +
                getBaseVersion() + "(version: " + getVersion() + ", snapshot:" + isSnapshot() + ") " +
                (getFile() == null ? "" : " " + getFile()) +
                '}';
    }

    @Override
    public int compareTo(MavenArtifact o) {
        return new CompareToBuilder().
                append(this.getGroupId(), o.getGroupId()).
                append(this.getArtifactId(), o.getArtifactId()).
                append(this.getBaseVersion(), o.getBaseVersion()).
                append(this.getVersion(), o.getVersion()).
                append(this.getType(), o.getType()).
                append(this.getClassifier(), o.getClassifier()).
                toComparison();
    }

    /**
     * Artifact has been deployed to a Maven repository ("mvn deploy")
     *
     * @see #getUrl()
     */
    public boolean isDeployed() {
        return this.getRepositoryUrl() != null && !getRepositoryUrl().isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGroupId(), getArtifactId(), getBaseVersion(), getVersion());
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
        if (getGroupId() == null) {
            if (other.getGroupId() != null)
                return false;
        } else if (!getGroupId().equals(other.getGroupId()))
            return false;
        if (getArtifactId() == null) {
            if (other.getArtifactId() != null)
                return false;
        } else if (!getArtifactId().equals(other.getArtifactId()))
            return false;
        if (getBaseVersion() == null) {
            if (other.getBaseVersion() != null)
                return false;
        } else if (!getBaseVersion().equals(other.getBaseVersion()))
            return false;
        if (getVersion() == null) {
            if (other.getVersion() != null)
                return false;
        } else if (!getVersion().equals(other.getVersion()))
            return false;
        if (getType() == null) {
            if (other.getType() != null)
                return false;
        } else if (!getType().equals(other.getType()))
            return false;
        if (getClassifier() == null) {
            if (other.getClassifier() != null)
                return false;
        } else if (!getClassifier().equals(other.getClassifier()))
            return false;

        return true;
    }

    /**
     * Gets the base version of this artifact, for example "1.0-SNAPSHOT". In contrast to the org.eclipse.aether.artifact.Artifact#getVersion(), the
     * base version will always refer to the unresolved meta version.
     *
     * @see org.eclipse.aether.artifact.Artifact#getBaseVersion()
     */
    public String getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(String baseVersion) {
        this.baseVersion = baseVersion;
        if (baseVersion != null && baseVersion.endsWith("SNAPSHOT")) {
            this.snapshot = true;
        }
    }

    /**
     * Gets the version of this artifact, for example "1.0-20180318.225603-3". Note that in case of meta versions like
     * "1.0-SNAPSHOT", the artifact's version depends on the state of the artifact. Artifacts that have been resolved or
     * deployed will usually have the meta version expanded.
     *
     * @see org.eclipse.aether.artifact.Artifact#getVersion()
     */
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        if (version != null && version.endsWith("SNAPSHOT")) {
            this.snapshot = true;
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return The type of this artifact, for example "jar".
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the classifier of this artifact, for example "sources".
     *
     * @return The classifier or {@code null} if none, never empty.
     * @see org.eclipse.aether.artifact.Artifact#getClassifier()
     */
    @Nullable
    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(@Nullable String classifier) {
        this.classifier = classifier == null || classifier.isEmpty() ? null : classifier;
    }

    /**
     * Extension of the generated file
     * @return file extension (e.g. "jar", "war"...)
     * @see org.eclipse.aether.artifact.Artifact#getExtension()
     */
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * Not persisted in the database
     *
     * @return absolute path of the generated file in the build agent workspace
     */
    @Nullable
    public String getFile() {
        return file;
    }

    public void setFile(@Nullable String file) {
        this.file = file;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    public void setSnapshot(boolean snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * <p>
     * URL of the Maven repository on which the artifact has been deployed ("mvn deploy").
     * </p>
     * <p>Sample: "https://nexus.my-company.com/content/repositories/snapshots/"</p>
     */
    @Nullable
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     *
     * @param repositoryUrl URL of the maven repository the artifact was uploaded to.
     * @see #getRepositoryUrl()
     */
    public void setRepositoryUrl(@Nullable String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
