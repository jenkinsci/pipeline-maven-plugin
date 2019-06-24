package org.jenkinsci.plugins.pipeline.maven.model;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenProjectExecutionDetails implements Comparable<MavenProjectExecutionDetails>, Serializable {
    private static final long serialVersionUID = 1L;

    @Nonnull
    private MavenArtifact project;
    @Nonnull
    private SortedSet<MavenMojoExecutionDetails> mojoExecutionDetails = new TreeSet<>();

    public MavenProjectExecutionDetails(@Nonnull MavenArtifact project) {
        this.project = project;
    }

    /**
     * See {@code org.apache.maven.execution.ExecutionEvent.Type#MojoStarted}
     */
    @Nonnull
    public ZonedDateTime getStart() {
        return mojoExecutionDetails.first().getStart();
    }

    /**
     * See {@code org.apache.maven.execution.ExecutionEvent.Type#MojoSucceeded} and {@code org.apache.maven.execution.ExecutionEvent.Type#MojoFailed}
     */
    @Nonnull
    public ZonedDateTime getStop() {
        return mojoExecutionDetails.last().getStop();
    }

    @Override
    public String toString() {
        return "MavenProjectExecutionDetails{" +
                "artifact=" + project +
                ", start=" + getStart() +
                ", stop=" + getStop() +
                '}';
    }

    @Override
    public int compareTo(MavenProjectExecutionDetails other) {
        int comparison = this.getStart().compareTo(other.getStart());

        if (comparison == 0) {
            comparison = this.getStop().compareTo(other.getStop());
        }
        return comparison;
    }

    @Nonnull
    public MavenArtifact getProject() {
        return project;
    }

    @Nonnull
    public SortedSet<MavenMojoExecutionDetails> getMojoExecutionDetails() {
        return mojoExecutionDetails;
    }

    public void add(@Nonnull MavenMojoExecutionDetails timer) {
        mojoExecutionDetails.add(timer);
    }

    @Nonnull
    public String getDuration() {
        return Duration.between(getStart(), getStop()).getSeconds() + "s";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MavenProjectExecutionDetails that = (MavenProjectExecutionDetails) o;

        return project.equals(that.project);
    }

    @Override
    public int hashCode() {
        return project.hashCode();
    }
}
