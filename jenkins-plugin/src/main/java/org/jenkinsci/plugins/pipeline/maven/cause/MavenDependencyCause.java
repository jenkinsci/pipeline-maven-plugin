package org.jenkinsci.plugins.pipeline.maven.cause;

/**
 *
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface MavenDependencyCause {
    String getGroupId();
    String getArtifactId();
    String getVersion();
}
