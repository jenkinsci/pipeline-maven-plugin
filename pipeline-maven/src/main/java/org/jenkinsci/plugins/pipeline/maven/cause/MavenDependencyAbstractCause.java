package org.jenkinsci.plugins.pipeline.maven.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Cause;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class MavenDependencyAbstractCause extends Cause implements MavenDependencyCause, Cloneable {

    private List<MavenArtifact> mavenArtifacts;

    private List<String> omittedPipelineFullNames;

    public MavenDependencyAbstractCause() {}

    public MavenDependencyAbstractCause(@Nullable List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @NonNull
    @Override
    public List<MavenArtifact> getMavenArtifacts() {
        if (mavenArtifacts == null) {
            mavenArtifacts = new ArrayList<>();
        }
        return mavenArtifacts;
    }

    @Override
    public void setMavenArtifacts(@NonNull List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @NonNull
    @Override
    public List<String> getOmittedPipelineFullNames() {
        if (omittedPipelineFullNames == null) {
            omittedPipelineFullNames = new ArrayList<>();
        }
        return omittedPipelineFullNames;
    }

    @Override
    public void setOmittedPipelineFullNames(List<String> omittedPipelines) {
        this.omittedPipelineFullNames = omittedPipelines;
    }

    @NonNull
    public String getMavenArtifactsDescription() {
        return getMavenArtifacts().stream()
                .map(mavenArtifact -> mavenArtifact == null ? "null" : mavenArtifact.getShortDescription())
                .collect(Collectors.joining(","));
    }

    @Override
    public MavenDependencyAbstractCause clone() throws CloneNotSupportedException {
        return (MavenDependencyAbstractCause) super.clone();
    }
}
