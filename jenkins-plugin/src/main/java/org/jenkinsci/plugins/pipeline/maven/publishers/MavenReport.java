package org.jenkinsci.plugins.pipeline.maven.publishers;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maven report for the build. Intended to be extended.
 *
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenReport implements RunAction2, SimpleBuildStep.LastBuildAction, Serializable {

    private static final long serialVersionUID = 1L;

    protected final static Logger LOGGER = Logger.getLogger(MavenReport.class.getName());

    private transient Run run;

    private transient List<MavenArtifact> generatedArtifacts;

    public MavenReport(@Nonnull Run run) {
        this.run = run;
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MavenReport.class);
    }

    public Collection<MavenArtifact> getGeneratedArtifacts() {
        if (generatedArtifacts == null) {
            List<MavenArtifact> generatedArtifacts = GlobalPipelineMavenConfig.getDao().getGeneratedArtifacts(run.getParent().getFullName(), run.getNumber());
            if (run.getResult() == null) {
                LOGGER.log(Level.FINE, "Load generated artifacts for build {0}#{1} but don't cache them as the build is not finished", new Object[]{run.getParent().getName(), run.getNumber()});
            } else {
                LOGGER.log(Level.FINE, "Load generated artifacts for build {0}#{1} and cache them", new Object[]{run.getParent().getName(), run.getNumber()});

                // build is finished, we can cache the result
                this.generatedArtifacts = generatedArtifacts;
            }
            return generatedArtifacts;
        } else {
            LOGGER.log(Level.FINE, "Use cached generated artifacts for build {0}#{1}", new Object[]{run.getParent().getName(), run.getNumber()});
            return generatedArtifacts;
        }
    }

    public Collection<MavenArtifact> getDeployedArtifacts() {
        return Collections2.filter(getGeneratedArtifacts(), new Predicate<MavenArtifact>() {
            @Override
            public boolean apply(@Nullable MavenArtifact mavenArtifact) {
                return mavenArtifact == null ? false : mavenArtifact.isDeployed();
            }
        });
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
