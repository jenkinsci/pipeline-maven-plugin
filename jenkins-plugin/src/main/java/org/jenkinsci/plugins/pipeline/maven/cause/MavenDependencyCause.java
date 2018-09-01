package org.jenkinsci.plugins.pipeline.maven.cause;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import javax.annotation.Nonnull;

/**
 *
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface MavenDependencyCause {
    @Nonnull
    MavenArtifact getMavenArtifact();
}
