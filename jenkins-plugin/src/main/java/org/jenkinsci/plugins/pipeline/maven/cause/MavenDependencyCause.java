package org.jenkinsci.plugins.pipeline.maven.cause;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.List;

import javax.annotation.Nonnull;

/**
 *
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface MavenDependencyCause {
    @Nonnull
    List<MavenArtifact> getMavenArtifacts();
}
