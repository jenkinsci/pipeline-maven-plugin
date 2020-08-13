package org.jenkinsci.plugins.pipeline.maven.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.time.ZonedDateTime;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenExecutionPhaseDetails implements Comparable<MavenExecutionPhaseDetails> {
    @NonNull
    private final String phase;
    @NonNull
    private SortedSet<MavenMojoExecutionDetails> mojoExecutionDetails = new TreeSet<>();

    public MavenExecutionPhaseDetails(@NonNull String phase) {
        this.phase = phase;
    }

    @Nonnull
    public ZonedDateTime getStart() {
        return mojoExecutionDetails.first().getStart();
    }

    @Nonnull
    public ZonedDateTime getStop() {
        return mojoExecutionDetails.last().getStop();
    }

    /**
     * Duration in seconds
     * @return duration (e.g. "12s")
     */
    @Nonnull
    public String getDuration() {
        int durationInSecs = 0;
        for (MavenMojoExecutionDetails mojoExecutionDetails:getMojoExecutionDetails()) {
            durationInSecs += mojoExecutionDetails.getDurationMillis();
        }
        return TimeUnit.SECONDS.convert(durationInSecs, TimeUnit.MILLISECONDS) + "s";
    }

    /**
     * Maven lifecycle phase
     * @return phase (e.g. "compile")
     */
    @NonNull
    public String getPhase() {
        return phase;
    }

    public SortedSet<MavenMojoExecutionDetails> getMojoExecutionDetails() {
        return mojoExecutionDetails;
    }

    @Override
    public int compareTo(MavenExecutionPhaseDetails other) {
        return new CompareToBuilder()
                .append(this.getStart(), other.getStart())
                .append(this.getStop(), other.getStop())
                .toComparison();
    }

    @Override
    public String toString() {
        return "MavenExecutionPhaseDetails{" +
                "phase=" + phase +
                ", mojoExecutionDetails=" + mojoExecutionDetails +
                '}';
    }
}
