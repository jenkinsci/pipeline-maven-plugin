package org.jenkinsci.plugins.pipeline.maven.dao;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface PipelineMavenPluginDao {

    void recordDependency(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type, String scope);

    void recordGeneratedArtifact(String jobFullName, int buildNumber, String groupId, String artifactId, String version, String type);

    void renameJob(String oldFullName, String newFullName);

    void deleteJob(String jobFullName);

    void deleteBuild(String jobFullName, int buildNumber);

    /**
     * Routine task to cleanup the database
     */
    void cleanup();
}
