package org.jenkinsci.plugins.pipeline.maven.cause;

import hudson.console.ModelHyperlinkNote;
import hudson.model.TaskListener;
import hudson.model.User;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.cause.MavenDependencyAbstractCause;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependencyCliCause extends MavenDependencyAbstractCause {
    private final String startedBy;

    public MavenDependencyCliCause(String startedBy) {
        super();
        this.startedBy = startedBy;
    }

    public MavenDependencyCliCause(String startedBy, List<MavenArtifact> mavenArtifacts) {
        super(mavenArtifacts);
        this.startedBy = startedBy;
    }

    public MavenDependencyCliCause(String startedBy, MavenArtifact... mavenArtifacts) {
        this(startedBy, Arrays.asList(mavenArtifacts));
    }

    @Override
    public String getShortDescription() {
        User user = User.get(startedBy, false, Collections.emptyMap());
        String userName = user != null ? user.getDisplayName() : startedBy;
        return "Started from command line by " + userName + " for maven artifacts " + getMavenArtifactsDescription();
    }

    @Override
    public void print(TaskListener listener) {
        listener.getLogger().println(
                "Started from command line by " + ModelHyperlinkNote.encodeTo("/user/" + startedBy, startedBy) + " for maven artifacts " + getMavenArtifactsDescription());
    }

}
