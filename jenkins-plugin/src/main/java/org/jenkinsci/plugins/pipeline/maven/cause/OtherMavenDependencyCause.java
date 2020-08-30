package org.jenkinsci.plugins.pipeline.maven.cause;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class OtherMavenDependencyCause extends MavenDependencyAbstractCause {
    final String shortDescription;
    public OtherMavenDependencyCause(@Nonnull String shortDescription) {
        super();
        this.shortDescription = Objects.requireNonNull(shortDescription);
    }

    public OtherMavenDependencyCause(@Nonnull String shortDescription, @Nullable List<MavenArtifact> mavenArtifacts) {
        super(mavenArtifacts);
        this.shortDescription = Objects.requireNonNull(shortDescription);
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }
}
