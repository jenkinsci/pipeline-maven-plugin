package org.jenkinsci.plugins.pipeline.maven.publishers;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
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

    public MavenReport(@Nonnull Run run) {
        this.run = run;
    }

    @Override
    public synchronized void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public synchronized void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public synchronized Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MavenReport.class);
    }

    public synchronized Collection<MavenArtifact> getGeneratedArtifacts() {
        if (generatedArtifacts == null) {
            List<MavenArtifact> generatedArtifacts = GlobalPipelineMavenConfig.get().getDao().getGeneratedArtifacts(run.getParent().getFullName(), run.getNumber());
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
        List<String> downstreamJobFullNames = GlobalPipelineMavenConfig.get().getDao().listDownstreamJobs(run.getParent().getFullName(), run.getNumber());
        Collection<Job> downstreamJobs = Collections2.transform(downstreamJobFullNames, new Function<String, Job>() {
            @Override
            public Job apply(@Nullable String jobFullName) {
                if (jobFullName == null) {
                    return null;
                }
                // security / authorization is checked by Jenkins#getItemByFullName
                try {
                    return Jenkins.getInstance().getItemByFullName(jobFullName, Job.class);
                } catch (AccessDeniedException e) {
                    return null;
                }

            }
        });

        // filter null entries resulting from security/authorization filtering
        return Collections2.filter(downstreamJobs, Predicates.notNull());
    }

    public synchronized SortedMap<MavenArtifact, Collection<Job>> getDownstreamJobsByArtifact() {
        Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact = GlobalPipelineMavenConfig.get().getDao().listDownstreamJobsByArtifact(run.getParent().getFullName(), run.getNumber());
        TreeMap<MavenArtifact, Collection<Job>> result = new TreeMap<>();

        for(Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamJobsByArtifact.entrySet()) {
            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> downstreamJobFullNames = entry.getValue();

            Collection<Job> downstreamJobs = Collections2.transform(downstreamJobFullNames, new Function<String, Job>() {
                @Override
                public Job apply(@Nullable String jobFullName) {
                    if (jobFullName == null) {
                        return null;
                    }
                    // security / authorization is checked by Jenkins#getItemByFullName
                    try {
                        return Jenkins.getInstance().getItemByFullName(jobFullName, Job.class);
                    } catch (AccessDeniedException e) {
                        return null;
                    }

                }
            });
            // filter null entries resulting from security/authorization filtering
            result.put(mavenArtifact, Collections2.filter(downstreamJobs, Predicates.notNull()));
        }

        return result;
    }

    public synchronized Collection<Run> getUpstreamBuilds() {
        Map<String, Integer> upstreamJobs = GlobalPipelineMavenConfig.get().getDao().listUpstreamJobs(run.getParent().getFullName(), run.getNumber());
        Collection<Run> upstreamBuilds = Collections2.transform(upstreamJobs.entrySet(), new Function<Map.Entry<String, Integer>, Run>() {
            @Override
            public Run apply(@Nullable Map.Entry<String, Integer> entry) {
                if (entry == null)
                    return null;
                Job job;
                // security / authorization is checked by Jenkins#getItemByFullName
                try {
                    job = Jenkins.getInstance().getItemByFullName(entry.getKey(), Job.class);
                } catch (AccessDeniedException e) {
                    return null;
                }
                if (job == null)
                    return null;
                Run run = job.getBuildByNumber(entry.getValue());
                if (run == null) {
                    return null;
                }
                return run;
            }
        });
        // filter null entries resulting from security/authorization filtering
        return Collections2.filter(upstreamBuilds, Predicates.notNull());
    }

    public synchronized Collection<MavenArtifact> getDeployedArtifacts() {
        return Collections2.filter(getGeneratedArtifacts(), new Predicate<MavenArtifact>() {
            @Override
            public boolean apply(@Nullable MavenArtifact mavenArtifact) {
                return mavenArtifact == null ? false : mavenArtifact.isDeployed();
            }
        });
    }

    public synchronized Collection<MavenDependency> getDependencies(){
        return GlobalPipelineMavenConfig.get().getDao().listDependencies(run.getParent().getFullName(), run.getNumber());
    }

    public synchronized Run getRun() {
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
