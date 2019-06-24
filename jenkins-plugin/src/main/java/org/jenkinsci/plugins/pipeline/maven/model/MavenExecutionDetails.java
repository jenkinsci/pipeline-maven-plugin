package org.jenkinsci.plugins.pipeline.maven.model;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenExecutionDetails implements Comparable<MavenExecutionDetails>, Serializable {

    private static final long serialVersionUID = 1L;

    private SortedSet<MavenProjectExecutionDetails> mavenProjectExecutionDetails = new TreeSet<>();

    @Nonnull
    private final ZonedDateTime start;
    @Nonnull
    private ZonedDateTime stop;

    public MavenExecutionDetails(@Nonnull ZonedDateTime start) {
        this.start = start;
    }

    public SortedSet<MavenProjectExecutionDetails> getMavenProjectExecutionDetails() {
        return mavenProjectExecutionDetails;
    }

    @Nonnull
    public ZonedDateTime getStart() {
        return start;
    }

    @Nonnull
    public ZonedDateTime getStop() {
        return stop;
    }

    public void setStop(@Nonnull ZonedDateTime stop) {
        this.stop = stop;
    }

    @Override
    public int compareTo(MavenExecutionDetails o) {
        return this.start.compareTo(o.start);
    }

    /**
     * it's a poor comparison but there is no risk of having 2 builds starting at the same time
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MavenExecutionDetails that = (MavenExecutionDetails) o;

        return start.equals(that.start);
    }

    @Override
    public int hashCode() {
        return start.hashCode();
    }

    public String getExecutionDurationDetails() {

        Map<String, MavenExecutionPhaseDetails> phaseDetailsByPhase = new HashMap<>();

        for (MavenProjectExecutionDetails projectExecutionDetails :
                getMavenProjectExecutionDetails()) {
            for (MavenMojoExecutionDetails mojoExecutionDetails : projectExecutionDetails.getMojoExecutionDetails()) {

                MavenExecutionPhaseDetails mavenExecutionPhaseDetails = phaseDetailsByPhase.computeIfAbsent(mojoExecutionDetails.getLifecyclePhase(), phase -> new MavenExecutionPhaseDetails(phase));
                mavenExecutionPhaseDetails.getMojoExecutionDetails().add(mojoExecutionDetails);
            }
        }

        StringBuilder sb = new StringBuilder();

        int phasesCounter = 0;
        int mojoExecutionCounter = 0;
        List<MavenExecutionPhaseDetails> mavenExecutionPhaseDetailsList = phaseDetailsByPhase.values().stream().sorted().collect(Collectors.toList());

        for (MavenExecutionPhaseDetails phaseDetails : mavenExecutionPhaseDetailsList) {
            phasesCounter++;
            if (phaseDetails.getMojoExecutionDetails().isEmpty()) {

            } else {
                sb.append(" * " + phaseDetails.getPhase() + " --- " + phaseDetails.getDuration() + "\n");
                for (MavenMojoExecutionDetails mojoExecutionDetails : phaseDetails.getMojoExecutionDetails().stream()/*.filter(m -> m.getDurationMillis() > 1000)*/.collect(Collectors.toList())) {
                    mojoExecutionCounter++;
                    sb.append("   * " + mojoExecutionDetails.getPlugin().getArtifactId() + ":" + mojoExecutionDetails.getGoal() + " (" + mojoExecutionDetails.getLifecyclePhase() + " - " + mojoExecutionDetails.getExecutionId() + ")" + " @ " + mojoExecutionDetails.getProject().getArtifactId() + " --- " + mojoExecutionDetails.getDuration() + "\n");
                }
            }
        }

        sb.append("\n####\n");
        for (MavenExecutionPhaseDetails phaseDetails : mavenExecutionPhaseDetailsList) {
            if (phaseDetails.getMojoExecutionDetails().isEmpty()) {
            } else {
                sb.append(" * " + phaseDetails.getPhase() + " --- " + phaseDetails.getDuration() + "\n");
            }
        }

        sb.append("phases:" + phasesCounter +  ", mojoExecutions:" + mojoExecutionCounter);

        return sb.toString();
    }
}
