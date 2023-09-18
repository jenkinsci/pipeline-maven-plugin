package org.jenkinsci.plugins.pipeline.maven.cause;

import com.google.common.base.Preconditions;
import hudson.model.Cause;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependencyCauseHelper {

    /**
     * Return matching artifact if the given causes refer to common Maven artifact. Empty list if there are no matching artifact
     */
    @NonNull
    public static List<MavenArtifact> isSameCause(MavenDependencyCause newMavenCause, Cause oldMavenCause) {
        if (!(oldMavenCause instanceof MavenDependencyCause)) {
            return Collections.emptyList();
        }

        List<MavenArtifact> newCauseArtifacts = Preconditions.checkNotNull(newMavenCause.getMavenArtifacts(), "newMavenCause.mavenArtifacts should not be null");
        List<MavenArtifact> oldCauseArtifacts =  Preconditions.checkNotNull(((MavenDependencyCause) oldMavenCause).getMavenArtifacts(), "oldMavenCause.mavenArtifacts should not be null");

        List<MavenArtifact> matchingArtifacts = new ArrayList<>();
        for (MavenArtifact newCauseArtifact : newCauseArtifacts) {
            if (newCauseArtifact.isSnapshot() && newCauseArtifact.getVersion().contains("SNAPSHOT")) {
                // snapshot without exact version (aka base version), cannot search for same cause
            } else {
                for (MavenArtifact oldCauseArtifact : oldCauseArtifacts) {
                    if (Objects.equals(newCauseArtifact.getGroupId(), oldCauseArtifact.getGroupId()) &&
                            Objects.equals(newCauseArtifact.getArtifactId(), oldCauseArtifact.getArtifactId()) &&
                            Objects.equals(newCauseArtifact.getVersion(), oldCauseArtifact.getVersion()) &&
                            Objects.equals(newCauseArtifact.getBaseVersion(), oldCauseArtifact.getBaseVersion()) &&
                            Objects.equals(newCauseArtifact.getClassifier(), oldCauseArtifact.getClassifier()) &&
                            Objects.equals(newCauseArtifact.getType(), oldCauseArtifact.getType())) {
                        matchingArtifacts.add(newCauseArtifact);
                    }
                }
            }
        }

        return matchingArtifacts;
    }

    public static List<MavenArtifact> isSameCause(MavenDependencyCause newMavenCause, List<Cause> oldMavenCauses) {
        List<MavenArtifact> matchingArtifacts = new ArrayList<>();

        for (Cause oldMavenCause:oldMavenCauses) {
            matchingArtifacts.addAll(isSameCause(newMavenCause, oldMavenCause));
        }
        return matchingArtifacts;
    }
}
