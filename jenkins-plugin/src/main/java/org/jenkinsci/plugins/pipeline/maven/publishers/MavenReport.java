package org.jenkinsci.plugins.pipeline.maven.publishers;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maven report for the build. Intended to be extended.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenReport implements RunAction2, SimpleBuildStep.LastBuildAction, Serializable {

    private static final long serialVersionUID = 1L;

    protected final static Logger LOGGER = Logger.getLogger(MavenReport.class.getName());

    private transient Run run;

    private transient List<MavenArtifact> generatedArtifacts;

    private transient Collection<Job> downstreamJobs;

    private transient Collection<Run> upstreamBuilds;

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
    public synchronized Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MavenReport.class);
    }

    public synchronized Collection<MavenArtifact> getGeneratedArtifacts() {
        if (generatedArtifacts == null) {
            List<MavenArtifact> generatedArtifacts = GlobalPipelineMavenConfig.getDao().getGeneratedArtifacts(run.getParent().getFullName(), run.getNumber());
            if (run.getResult() == null) {
                LOGGER.log(Level.FINE, "Load generated artifacts for build {0}#{1} but don't cache them as the build is not finished: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), generatedArtifacts.size()});
            } else {
                LOGGER.log(Level.FINE, "Load generated artifacts for build {0}#{1} and cache them: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), generatedArtifacts.size()});

                // build is finished, we can cache the result
                this.generatedArtifacts = generatedArtifacts;
            }
            return generatedArtifacts;
        } else {
            LOGGER.log(Level.FINE, "Use cached generated artifacts for build {0}#{1}: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), generatedArtifacts.size()});
            return generatedArtifacts;
        }
    }

    public synchronized Collection<Job> getDownstreamJobs() {
        if (downstreamJobs == null) {
            List<String> downstreamJobFullNames = GlobalPipelineMavenConfig.getDao().listDownstreamJobs(run.getParent().getFullName(), run.getNumber());
            Collection<Job> downstreamJobs = Collections2.transform(downstreamJobFullNames, new Function<String, Job>() {
                @Override
                public Job apply(@Nullable String jobFullName) {
                    if (jobFullName == null) {
                        return null;
                    }
                    return Jenkins.getInstance().getItemByFullName(jobFullName, Job.class);

                }
            });
            if (run.isBuilding()) {
                LOGGER.log(Level.FINE, "Load downstream jobs for build {0}#{1} but don't cache them as the build is not finished: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), downstreamJobs.size()});
                return downstreamJobs;
            } else {
                LOGGER.log(Level.FINE, "Load downstream jobs for build {0}#{1} and cache them: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), downstreamJobs.size()});
                this.downstreamJobs = downstreamJobs;
                return downstreamJobs;
            }
        } else {
            LOGGER.log(Level.FINE, "Use cached downstream jobs for build {0}#{1}: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), downstreamJobs.size()});
            return downstreamJobs;
        }
    }

    public synchronized Collection<Run> getUpstreamBuilds() {
        if (upstreamBuilds == null) {
            Map<String, Integer> upstreamJobs = GlobalPipelineMavenConfig.getDao().listUpstreamJobs(run.getParent().getFullName(), run.getNumber());
            Collection<Run> upstreamBuilds = Collections2.transform(upstreamJobs.entrySet(), new Function<Map.Entry<String, Integer>, Run>() {
                @Override
                public Run apply(@Nullable Map.Entry<String, Integer> entry) {
                    if (entry == null)
                        return null;
                    Job job = Jenkins.getInstance().getItemByFullName(entry.getKey(), Job.class);
                    if (job == null)
                        return null;
                    Run run = job.getBuildByNumber(entry.getValue());
                    if (run == null) {
                        return null;
                    }
                    return run;
                }
            });
            if (run.isBuilding()) {
                LOGGER.log(Level.FINE, "Load upstream builds for build {0}#{1} but don't cache them as the build is not finished: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), upstreamBuilds.size()});
                return upstreamBuilds;
            } else {
                LOGGER.log(Level.FINE, "Load upstream builds for build {0}#{1} and cache them: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), upstreamBuilds.size()});
                this.upstreamBuilds = upstreamBuilds;
                return upstreamBuilds;
            }
        } else {
            LOGGER.log(Level.FINE, "Use cached upstream builds for build {0}#{1}: {2} entries", new Object[]{run.getParent().getName(), run.getNumber(), upstreamBuilds.size()});
            return upstreamBuilds;
        }
    }

    public synchronized Collection<MavenArtifact> getDeployedArtifacts() {
        return Collections2.filter(getGeneratedArtifacts(), new Predicate<MavenArtifact>() {
            @Override
            public boolean apply(@Nullable MavenArtifact mavenArtifact) {
                return mavenArtifact == null ? false : mavenArtifact.isDeployed();
            }
        });
    }

    public Run getRun() {
        return run;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/pipeline-maven/images/24x24/apache-maven.png";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Maven";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "maven";
    }
}
