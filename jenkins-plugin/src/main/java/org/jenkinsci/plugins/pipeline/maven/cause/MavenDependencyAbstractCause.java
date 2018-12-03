package org.jenkinsci.plugins.pipeline.maven.cause;

import hudson.model.Cause;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class MavenDependencyAbstractCause extends Cause implements MavenDependencyCause {

    private List<MavenArtifact> mavenArtifacts;

    public MavenDependencyAbstractCause() {
    }

    public MavenDependencyAbstractCause(List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @Nonnull
    @Override
    public List<MavenArtifact> getMavenArtifacts() {
        if (mavenArtifacts == null) {
            mavenArtifacts = new ArrayList<>();
        }
        return mavenArtifacts;
    }

    @Override
    public void setMavenArtifacts(@Nonnull List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @Nonnull
    public String getMavenArtifactsDescription() {
        return mavenArtifacts.stream()
                .map(mavenArtifact -> mavenArtifact == null ? "null" : mavenArtifact.getShortDescription())
                .collect(Collectors.joining(","));
    }
}
