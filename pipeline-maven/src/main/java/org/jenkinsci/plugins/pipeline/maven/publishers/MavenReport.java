package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.Messages;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    public MavenReport(@NonNull Run run) {
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

    /**
     * Looks up a job by name.
     * Returns null rather than throwing {@link AccessDeniedException} in case the user has {@link Item#DISCOVER} but not {@link Item#READ}.
     */
    private static @CheckForNull Job<?, ?> getJob(String fullName) {
        try {
            return Jenkins.get().getItemByFullName(fullName, Job.class);
        } catch (RuntimeException x) { // TODO switch to simple catch (AccessDeniedException) when baseline includes Spring Security
            if (x.getClass().getSimpleName().startsWith("AccessDeniedException")) {
                return null;
            } else {
                throw x;
            }
        }
    }

    public synchronized Collection<Job> getDownstreamJobs() {
        List<String> downstreamJobFullNames = GlobalPipelineMavenConfig
                .get()
                .getDao()
                .listDownstreamJobsByArtifact(run.getParent().getFullName(), run.getNumber())
                .values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toList());
        return downstreamJobFullNames.stream().map(jobFullName -> {
            if (jobFullName == null) {
                return null;
            }
            return getJob(jobFullName);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public synchronized SortedMap<MavenArtifact, Collection<Job>> getDownstreamJobsByArtifact() {
        Map<MavenArtifact, SortedSet<String>> downstreamJobsByArtifact = GlobalPipelineMavenConfig.get().getDao().listDownstreamJobsByArtifact(run.getParent().getFullName(), run.getNumber());
        TreeMap<MavenArtifact, Collection<Job>> result = new TreeMap<>();

        for(Map.Entry<MavenArtifact, SortedSet<String>> entry: downstreamJobsByArtifact.entrySet()) {
            MavenArtifact mavenArtifact = entry.getKey();
            SortedSet<String> downstreamJobFullNames = entry.getValue();
            result.put(mavenArtifact, downstreamJobFullNames.stream().map(jobFullName -> {
                if (jobFullName == null) {
                    return null;
                }
                return getJob(jobFullName);
            }).filter(Objects::nonNull).collect(Collectors.toList()));
        }

        return result;
    }

    public synchronized Collection<Run> getUpstreamBuilds() {
        Map<String, Integer> upstreamJobs = GlobalPipelineMavenConfig.get().getDao().listUpstreamJobs(run.getParent().getFullName(), run.getNumber());
        return upstreamJobs.entrySet().stream().map(entry -> {
            if (entry == null)
                return null;
            Job<?, ?> job = getJob(entry.getKey());
            if (job == null)
                return null;
            Run run = job.getBuildByNumber(entry.getValue());
            return run;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public synchronized Collection<MavenArtifact> getDeployedArtifacts() {
        return getGeneratedArtifacts()
                .stream()
                .filter(mavenArtifact -> mavenArtifact != null && mavenArtifact.isDeployed())
                .collect(Collectors.toList());
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
        return Messages.report_maven_description();
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "maven";
    }
}
