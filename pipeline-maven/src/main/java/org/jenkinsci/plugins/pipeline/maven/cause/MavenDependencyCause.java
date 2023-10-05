package org.jenkinsci.plugins.pipeline.maven.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import java.util.List;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface MavenDependencyCause {
    @NonNull
    List<MavenArtifact> getMavenArtifacts();

    void setMavenArtifacts(List<MavenArtifact> mavenArtifacts);

    /**
     * List of pipeline whose trigger have been omitted because this pipeline trigger will subsequently trigger those pipeline.
     *
     * We have omitted these pipeline triggers to prevent excessive triggers.
     * @return list of {@link Job#getFullName()}
     */
    @NonNull
    List<String> getOmittedPipelineFullNames();

    void setOmittedPipelineFullNames(List<String> omittedPipelineFullNames);

    String getMavenArtifactsDescription();
}
