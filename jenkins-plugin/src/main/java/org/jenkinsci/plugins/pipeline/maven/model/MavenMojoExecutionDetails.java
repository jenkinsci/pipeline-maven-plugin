package org.jenkinsci.plugins.pipeline.maven.model;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenMojoExecutionDetails implements Comparable<MavenMojoExecutionDetails>, Serializable {

    private static final long serialVersionUID = 1L;

    @Nonnull
    private final MavenArtifact project;
    @Nonnull
    private final MavenArtifact plugin;
    @Nonnull
    private final String executionId;
    @Nonnull
    private final String goal;
    @Nonnull
    private final String lifecyclePhase;
    @Nonnull
    private final ZonedDateTime start;
    @Nonnull
    private ZonedDateTime stop;
    @Nonnull
    private MavenExecutionEventType type;

    public MavenMojoExecutionDetails(@Nonnull MavenArtifact project, @Nonnull MavenArtifact plugin, @Nonnull String executionId, @Nonnull String lifecyclePhase, @Nonnull String goal, @Nonnull ZonedDateTime start, @Nonnull MavenExecutionEventType type) {
        this.project = project;
        this.plugin = plugin;
        this.executionId = executionId;
        this.lifecyclePhase = lifecyclePhase;
        this.goal = goal;
        this.start = start;
        this.stop = start;
        this.type = type;
    }

    /**
     * See {@code org.apache.maven.execution.ExecutionEvent.Type#MojoStarted}
     */
    @Nonnull
    public ZonedDateTime getStart() {
        return start;
    }

    /**
     * See {@code org.apache.maven.execution.ExecutionEvent.Type#MojoSucceeded} and {@code org.apache.maven.execution.ExecutionEvent.Type#MojoFailed}
     */
    @Nonnull
    public ZonedDateTime getStop() {
        return stop;
    }

    public void stop(@Nonnull ZonedDateTime stop, MavenExecutionEventType type) {
        this.stop = stop;
        this.type = type;
    }

    @Nonnull
    public MavenArtifact getProject() {
        return project;
    }

    @Nonnull
    public MavenArtifact getPlugin() {
        return plugin;
    }

    @Nonnull
    public String getExecutionId() {
        return executionId;
    }

    @Nonnull
    public String getLifecyclePhase() {
        return lifecyclePhase;
    }

    @Nonnull
    public String getGoal() {
        return goal;
    }

    @Override
    public String toString() {
        return "MavenMojoExecutionDetails{" +
                "project=" + project.getId() +
                ", plugin=" + plugin.getId() +
                ", executionId='" + executionId + '\'' +
                ", lifecyclePhase='" + lifecyclePhase + '\'' +
                ", goal='" + goal + '\'' +
                ", start=" + start +
                ", stop=" + stop +
                ", type=" + type +
                '}';
    }

    @Override
    public int compareTo(MavenMojoExecutionDetails other) {
        int comparison = this.getStart().compareTo(other.getStart());

        if (comparison == 0) {
            comparison = this.getStop().compareTo(other.getStop());
        }
        return comparison;
    }

    @Nonnull
    public String getDuration() {
        return Duration.between(start, stop).getSeconds() + "s";
    }

    @Nonnull
    public long getDurationMillis() {
        return start.until(stop, ChronoUnit.MILLIS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MavenMojoExecutionDetails that = (MavenMojoExecutionDetails) o;

        if (!project.equals(that.project)) return false;
        if (!plugin.equals(that.plugin)) return false;
        if (!executionId.equals(that.executionId)) return false;
        return goal.equals(that.goal);
    }

    @Override
    public int hashCode() {
        int result = project.hashCode();
        result = 31 * result + plugin.hashCode();
        result = 31 * result + executionId.hashCode();
        result = 31 * result + goal.hashCode();
        return result;
    }
}
