package org.jenkinsci.plugins.pipeline.maven.cause;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import hudson.model.Cause;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependencyCauseHelper {
    public static boolean isSameCause(MavenDependencyCause newMavenCause, Cause olderMavenCause) {
        if (!(olderMavenCause instanceof MavenDependencyCause)) {
            return false;
        }

        List<MavenArtifact> newCauseArtifacts = newMavenCause.getMavenArtifacts();
        List<MavenArtifact> olderCauseArtifacts = ((MavenDependencyCause) olderMavenCause).getMavenArtifacts();

        final List<MavenArtifact> duplicateArtifacts = new ArrayList<>();
        for (MavenArtifact newCauseArtifact : newCauseArtifacts) {
            if (newCauseArtifact.snapshot && Objects.equals(newCauseArtifact.baseVersion, newCauseArtifact.baseVersion)) {
                // snapshot without exact version, cannot search for same cause
            } else {
                for (MavenArtifact olderCauseArtifact : olderCauseArtifacts) {
                    if (Objects.equals(newCauseArtifact.groupId, olderCauseArtifact.groupId) &&
                            Objects.equals(newCauseArtifact.artifactId, olderCauseArtifact.artifactId) &&
                            Objects.equals(newCauseArtifact.version, olderCauseArtifact.version) &&
                            Objects.equals(newCauseArtifact.baseVersion, olderCauseArtifact.baseVersion) &&
                            Objects.equals(newCauseArtifact.type, olderCauseArtifact.type)) {
                        duplicateArtifacts.add(newCauseArtifact);
                        break;
                    }
                }
            }
        }

        Collection<MavenArtifact> nonDuplicateArtifacts = Collections2.filter(newCauseArtifacts, new Predicate<MavenArtifact>() {
            @Override
            public boolean apply(@Nullable MavenArtifact mavenArtifact) {
                return !duplicateArtifacts.contains(mavenArtifact);
            }
        });

        return nonDuplicateArtifacts.isEmpty();
    }
}
