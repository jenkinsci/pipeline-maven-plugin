package org.jenkinsci.plugins.pipeline.maven.cause;

import com.google.common.collect.Collections2;
import hudson.model.Cause;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependencyCauseHelper {

    /**
     * Return {@code true} if the given causes refer to at least one common Maven artifact.
     *
     * TODO add unit tests
     */
    public static boolean isSameCause(MavenDependencyCause newMavenCause, Cause oldMavenCause) {
        if (!(oldMavenCause instanceof MavenDependencyCause)) {
            return false;
        }

        List<MavenArtifact> newCauseArtifacts = newMavenCause.getMavenArtifacts();
        List<MavenArtifact> oldCauseArtifacts = ((MavenDependencyCause) oldMavenCause).getMavenArtifacts();

        for (MavenArtifact newCauseArtifact : newCauseArtifacts) {
            if (newCauseArtifact.isSnapshot() && newCauseArtifact.getVersion().contains("SNAPSHOT")) {
                // snapshot without exact version, cannot search for same cause
            } else {
                for (MavenArtifact oldCauseArtifact : oldCauseArtifacts) {
                    if (
                            Objects.equals(newCauseArtifact.getGroupId(),       oldCauseArtifact.getGroupId()) &&
                            Objects.equals(newCauseArtifact.getArtifactId(),    oldCauseArtifact.getArtifactId()) &&
                            Objects.equals(newCauseArtifact.getVersion(),       oldCauseArtifact.getVersion()) &&
                            Objects.equals(newCauseArtifact.getBaseVersion(),   oldCauseArtifact.getBaseVersion()) &&
                            Objects.equals(newCauseArtifact.getType(),          oldCauseArtifact.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
