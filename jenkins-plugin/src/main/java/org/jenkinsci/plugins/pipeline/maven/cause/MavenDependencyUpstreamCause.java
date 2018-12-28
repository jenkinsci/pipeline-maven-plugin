package org.jenkinsci.plugins.pipeline.maven.cause;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MavenDependencyUpstreamCause extends Cause.UpstreamCause implements MavenDependencyCause {
    private List<MavenArtifact> mavenArtifacts;

    private List<String> omittedPipelineFullNames;

    public MavenDependencyUpstreamCause(Run<?, ?> up, @Nonnull MavenArtifact... mavenArtifact) {
        super(up);
        this.mavenArtifacts = Arrays.asList(mavenArtifact);
    }

    public MavenDependencyUpstreamCause(Run<?, ?> up, @Nullable Collection<MavenArtifact> mavenArtifacts, @Nullable Collection<String> omittedPipelineFullNames) {
        super(up);
        this.mavenArtifacts = mavenArtifacts == null ? Collections.emptyList() : new ArrayList<>(mavenArtifacts);
        this.omittedPipelineFullNames = omittedPipelineFullNames == null ? Collections.emptyList() : new ArrayList<>(omittedPipelineFullNames);
    }

    @Override
    public String getShortDescription() {
        return "Started by upstream build \"" + getUpstreamProject() + "\" #" + getUpstreamBuild() + " generating Maven artifacts: " + getMavenArtifactsDescription();
    }

    /**
     * TODO create a PR on jenkins-core to make {@link hudson.model.Cause.UpstreamCause#indent(TaskListener, int)} protected instead of private
     * and delete this method once the PR is merged
     *
     * @see UpstreamCause#print(TaskListener)
     */
    @Override
    public void print(TaskListener listener) {
        print(listener, 0);
    }

    /**
     * TODO create a PR on jenkins-core to make {@link hudson.model.Cause.UpstreamCause#indent(TaskListener, int)} protected instead of private
     * and delete this method once the PR is merged
     *
     * @see UpstreamCause#print(TaskListener)
     */
    private void indent(TaskListener listener, int depth) {
        for (int i = 0; i < depth; i++) {
            listener.getLogger().print(' ');
        }
    }

    /**
     * TODO create a PR on jenkins-core to make {@link hudson.model.Cause.UpstreamCause#print(TaskListener, int)} protected instead of private
     * <p>
     * Mimic {@link hudson.model.Cause.UpstreamCause#print(TaskListener, int)} waiting for this method to become protected instead of private
     *
     * @see UpstreamCause#print(TaskListener, int)
     */
    private void print(TaskListener listener, int depth) {
        indent(listener, depth);
        Run<?, ?> upstreamRun = getUpstreamRun();

        if (upstreamRun == null) {
            listener.getLogger().println("Started by upstream build " + ModelHyperlinkNote.encodeTo('/' + getUpstreamUrl(), getUpstreamProject()) +
                    "\" #" + ModelHyperlinkNote.encodeTo('/' + getUpstreamUrl() + getUpstreamBuild(), Integer.toString(getUpstreamBuild())) +
                    " generating Maven artifact: " + getMavenArtifactsDescription());
        } else {
            listener.getLogger().println("Started by upstream build " +
                    ModelHyperlinkNote.encodeTo('/' + upstreamRun.getUrl(), upstreamRun.getFullDisplayName()) + " generating Maven artifacts: " + getMavenArtifactsDescription());
        }

        if (getUpstreamCauses() != null && !getUpstreamCauses().isEmpty()) {
            indent(listener, depth);
            listener.getLogger().println("originally caused by:");
            for (Cause cause : getUpstreamCauses()) {
                if (cause instanceof MavenDependencyUpstreamCause) {
                    ((MavenDependencyUpstreamCause) cause).print(listener, depth + 1);
                } else {
                    indent(listener, depth + 1);
                    cause.print(listener);
                }
            }
        }
    }

    @Nonnull
    public String getMavenArtifactsDescription() {
        return mavenArtifacts.stream()
                .map(mavenArtifact -> mavenArtifact == null ? "null" : mavenArtifact.getShortDescription())
                .collect(Collectors.joining(","));
    }

    @Nonnull
    @Override
    public List<MavenArtifact> getMavenArtifacts() {
        if (mavenArtifacts == null) {
            mavenArtifacts = new ArrayList<>();
        }
        return mavenArtifacts;
    }

    @Nonnull
    @Override
    public List<String> getOmittedPipelineFullNames() {
        if (omittedPipelineFullNames == null) {
            omittedPipelineFullNames = new ArrayList<>();
        }
        return omittedPipelineFullNames;
    }

    @Override
    public void setOmittedPipelineFullNames(List<String> omittedPipelineFullNames) {
        this.omittedPipelineFullNames = omittedPipelineFullNames;
    }

    @Override
    public void setMavenArtifacts(List<MavenArtifact> mavenArtifacts) {
        this.mavenArtifacts = mavenArtifacts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MavenDependencyUpstreamCause that = (MavenDependencyUpstreamCause) o;
        return Objects.equals(mavenArtifacts, that.mavenArtifacts);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), mavenArtifacts);
    }
}
