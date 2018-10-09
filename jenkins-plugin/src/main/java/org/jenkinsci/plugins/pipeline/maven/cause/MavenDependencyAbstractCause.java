package org.jenkinsci.plugins.pipeline.maven.cause;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import hudson.model.Cause;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        return mavenArtifacts;
    }

    @Override
    public void setMavenArtifacts(@Nonnull List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @Nonnull
    public String getMavenArtifactsDescription() {
        return Joiner.on(",").join(Collections2.transform(mavenArtifacts, mavenArtifact -> mavenArtifact == null ? "null" : mavenArtifact.getShortDescription()));
    }
}
