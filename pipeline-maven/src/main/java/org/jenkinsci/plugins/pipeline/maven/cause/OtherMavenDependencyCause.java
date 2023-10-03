package org.jenkinsci.plugins.pipeline.maven.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class OtherMavenDependencyCause extends MavenDependencyAbstractCause {
    final String shortDescription;

    public OtherMavenDependencyCause(@NonNull String shortDescription) {
        super();
        this.shortDescription = Objects.requireNonNull(shortDescription);
    }

    public OtherMavenDependencyCause(@NonNull String shortDescription, @Nullable List<MavenArtifact> mavenArtifacts) {
        super(mavenArtifacts);
        this.shortDescription = Objects.requireNonNull(shortDescription);
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }
}
