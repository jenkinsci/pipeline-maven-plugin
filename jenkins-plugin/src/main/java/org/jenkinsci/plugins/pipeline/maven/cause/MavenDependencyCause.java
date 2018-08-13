package org.jenkinsci.plugins.pipeline.maven.cause;

import javax.annotation.Nonnull;

/**
 *
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface MavenDependencyCause {
    @Nonnull
    String getGroupId();
    @Nonnull
    String getArtifactId();
    @Nonnull
    String getVersion();
    @Nonnull
    String getType();
}
