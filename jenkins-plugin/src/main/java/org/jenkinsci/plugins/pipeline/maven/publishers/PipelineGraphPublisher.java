package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import static org.jenkinsci.plugins.pipeline.maven.publishers.DependenciesLister.*;

/**
 * Fingerprint the dependencies of the maven project.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineGraphPublisher extends MavenPublisher {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PipelineGraphPublisher.class.getName());

    private boolean includeSnapshotVersions = true;

    private boolean includeReleaseVersions;

    private boolean includeScopeCompile = true;

    private boolean includeScopeRuntime = true;

    private boolean includeScopeTest;

    private boolean includeScopeProvided = true;

    private boolean skipDownstreamTriggers;

    /**
     * Lifecycle phase threshold to trigger downstream pipelines, "deploy" or "install" or "package" or ...
     * If this phase has not been successfully reached during the build, then we don't trigger downstream pipelines
     */
    private String lifecycleThreshold = "deploy";

    private boolean ignoreUpstreamTriggers;

    @DataBoundConstructor
    public PipelineGraphPublisher() {
        super();
    }

    protected Set<String> getIncludedScopes() {
        Set<String> includedScopes = new TreeSet<>();
        if (includeScopeCompile)
            includedScopes.add("compile");
        if (includeScopeRuntime)
            includedScopes.add("runtime");
        if (includeScopeProvided)
            includedScopes.add("provided");
        if (includeScopeTest)
            includedScopes.add("test");
        return includedScopes;
    }

    @Override
    public void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException {
        Run run = context.get(Run.class);
        TaskListener listener = context.get(TaskListener.class);

        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao();

        List<MavenArtifact> parentProjects = listParentProjects(mavenSpyLogsElt, LOGGER);
        List<MavenDependency> dependencies = listDependencies(mavenSpyLogsElt, LOGGER);
        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogsElt, true);
        List<String> executedLifecyclePhases = XmlUtils.getExecutedLifecyclePhases(mavenSpyLogsElt);
        
        recordParentProject(parentProjects, generatedArtifacts, run,listener, dao);
        recordDependencies(dependencies, generatedArtifacts, run, listener, dao);
        recordGeneratedArtifacts(generatedArtifacts, executedLifecyclePhases, run, listener, dao);
    }

    protected void recordParentProject(List<MavenArtifact> parentProjects, List<MavenArtifact> generatedArtifacts,
                                       @Nonnull Run run, @Nonnull TaskListener listener, @Nonnull PipelineMavenPluginDao dao) {
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] pipelineGraphPublisher - recordParentProject - filter: " +
                    "versions[snapshot: " + isIncludeSnapshotVersions() + ", release: " + isIncludeReleaseVersions() + "]");
        }

        for (MavenArtifact parentProject : parentProjects) {
            // Exclude self-generated artifacts (#47996)
            if(generatedArtifacts.contains(parentProject)) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording parent project to generated artifact: " + parentProject.getId());
                }
                continue;
            }
            if (parentProject.snapshot) {
                if (!includeSnapshotVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording snapshot parent project: " + parentProject.getId());
                    }
                    continue;
                }
            } else {
                if (!includeReleaseVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording release parent project: " + parentProject.getId());
                    }
                    continue;
                }
            }

            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] pipelineGraphPublisher - Record parent project: " + parentProject.getId() + ", ignoreUpstreamTriggers: " + ignoreUpstreamTriggers);
                }

                dao.recordParentProject(run.getParent().getFullName(), run.getNumber(),
                        parentProject.groupId, parentProject.artifactId, parentProject.version,
                        this.ignoreUpstreamTriggers);

            } catch (RuntimeException e) {
                listener.error("[withMaven] pipelineGraphPublisher - WARNING: Exception recording parent project " + parentProject.getId() + " on build, skip");
                e.printStackTrace(listener.getLogger());
                listener.getLogger().flush();
            }
        }

    }

    protected void recordDependencies(List<MavenDependency> dependencies, List<MavenArtifact> generatedArtifacts,
                                      @Nonnull Run run, @Nonnull TaskListener listener, @Nonnull PipelineMavenPluginDao dao) {
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] pipelineGraphPublisher - recordDependencies - filter: " +
                    "versions[snapshot: " + isIncludeSnapshotVersions() + ", release: " + isIncludeReleaseVersions() + "], " +
                    "scopes:" + getIncludedScopes());
        }

        for (MavenDependency dependency : dependencies) {
            // Exclude self-generated artifacts (#47996)
            if(generatedArtifacts.contains(dependency.asMavenArtifact())) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording dependency to generated artifact: " + dependency.getId());
                }
                continue;
            }
            if (dependency.snapshot) {
                if (!includeSnapshotVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording snapshot dependency: " + dependency.getId());
                    }
                    continue;
                }
            } else {
                if (!includeReleaseVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording release dependency: " + dependency.getId());
                    }
                    continue;
                }
            }
            if (!getIncludedScopes().contains(dependency.getScope())) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] pipelineGraphPublisher - Skip recording dependency with ignored scope: " + dependency.getId());
                }
                continue;
            }

            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    listener.getLogger().println("[withMaven] pipelineGraphPublisher - Record dependency: " + dependency.getId() + ", ignoreUpstreamTriggers: " + ignoreUpstreamTriggers);
                }

                dao.recordDependency(run.getParent().getFullName(), run.getNumber(),
                        dependency.groupId, dependency.artifactId, dependency.baseVersion, dependency.type, dependency.getScope(),
                        this.ignoreUpstreamTriggers, null);

            } catch (RuntimeException e) {
                listener.error("[withMaven] pipelineGraphPublisher - WARNING: Exception recording " + dependency.getId() + " on build, skip");
                e.printStackTrace(listener.getLogger());
                listener.getLogger().flush();
            }
        }
    }

    /**
     * @param generatedArtifacts           deployed artifacts
     * @param executedLifecyclePhases Maven lifecycle phases that have been gone through during the Maven execution (e.g. "..., compile, test, package..." )
     * @param run
     * @param listener
     * @param dao
     */
    protected void recordGeneratedArtifacts(List<MavenArtifact> generatedArtifacts, List<String> executedLifecyclePhases, @Nonnull Run run, @Nonnull TaskListener listener, @Nonnull PipelineMavenPluginDao dao) {
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] pipelineGraphPublisher - recordGeneratedArtifacts...");
        }
        for (MavenArtifact artifact : generatedArtifacts) {
            boolean skipDownstreamPipelines = this.skipDownstreamTriggers ||
                    (!executedLifecyclePhases.contains(this.lifecycleThreshold));

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Build {0}#{1} - record generated {2}:{3}, version:{4}, " +
                                "executedLifecyclePhases: {5}, " +
                                "skipDownstreamTriggers:{6}, lifecycleThreshold: {7}",
                        new Object[]{run.getParent().getFullName(), run.getNumber(),
                                artifact.getId(), artifact.type, artifact.version,
                                executedLifecyclePhases,
                                skipDownstreamTriggers, lifecycleThreshold});
                listener.getLogger().println("[withMaven] pipelineGraphPublisher - Record generated artifact: " + artifact.getId() + ", version: " + artifact.version +
                        ", executedLifecyclePhases: " + executedLifecyclePhases +
                        ", skipDownstreamTriggers: " + skipDownstreamTriggers + ", lifecycleThreshold:" + lifecycleThreshold +
                        ", file: " + artifact.file);
            }
            dao.recordGeneratedArtifact(run.getParent().getFullName(), run.getNumber(),
                    artifact.groupId, artifact.artifactId, artifact.version, artifact.type, artifact.baseVersion,
                    artifact.repositoryUrl, skipDownstreamPipelines, artifact.extension, artifact.classifier);
            if (("bundle".equals(artifact.type) || "nbm".equals(artifact.type)) && "jar".equals(artifact.extension)) {
                // JENKINS-47069 org.apache.felix:maven-bundle-plugin:bundle uses the type "bundle" for "jar" files
                // record artifact as both "bundle" and "jar"
                dao.recordGeneratedArtifact(run.getParent().getFullName(), run.getNumber(),
                        artifact.groupId, artifact.artifactId, artifact.version, "jar", artifact.baseVersion,
                        null, skipDownstreamPipelines,  artifact.extension, artifact.classifier);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" +
                "disabled=" + isDisabled() + ", " +
                "scopes=" + getIncludedScopes() + ", " +
                "versions={snapshot:" + isIncludeSnapshotVersions() + ", release:" + isIncludeReleaseVersions() + "}, " +
                "skipDownstreamTriggers=" + isSkipDownstreamTriggers() + ", " +
                "lifecycleThreshold=" + getLifecycleThreshold() + ", " +
                "ignoreUpstreamTriggers=" + isIgnoreUpstreamTriggers() +
                ']';
    }

    public boolean isIncludeSnapshotVersions() {
        return includeSnapshotVersions;
    }

    @DataBoundSetter
    public void setIncludeSnapshotVersions(boolean includeSnapshotVersions) {
        this.includeSnapshotVersions = includeSnapshotVersions;
    }

    public boolean isIncludeReleaseVersions() {
        return includeReleaseVersions;
    }

    @DataBoundSetter
    public void setIncludeReleaseVersions(boolean includeReleaseVersions) {
        this.includeReleaseVersions = includeReleaseVersions;
    }

    public boolean isIncludeScopeCompile() {
        return includeScopeCompile;
    }

    @DataBoundSetter
    public void setIncludeScopeCompile(boolean includeScopeCompile) {
        this.includeScopeCompile = includeScopeCompile;
    }

    public boolean isIncludeScopeRuntime() {
        return includeScopeRuntime;
    }

    @DataBoundSetter
    public void setIncludeScopeRuntime(boolean includeScopeRuntime) {
        this.includeScopeRuntime = includeScopeRuntime;
    }

    public boolean isIncludeScopeTest() {
        return includeScopeTest;
    }

    @DataBoundSetter
    public void setIncludeScopeTest(boolean includeScopeTest) {
        this.includeScopeTest = includeScopeTest;
    }

    public boolean isIncludeScopeProvided() {
        return includeScopeProvided;
    }

    @DataBoundSetter
    public void setIncludeScopeProvided(boolean includeScopeProvided) {
        this.includeScopeProvided = includeScopeProvided;
    }

    public boolean isSkipDownstreamTriggers() {
        return skipDownstreamTriggers;
    }

    @DataBoundSetter
    public void setSkipDownstreamTriggers(boolean skipDownstreamTriggers) {
        this.skipDownstreamTriggers = skipDownstreamTriggers;
    }

    public boolean isIgnoreUpstreamTriggers() {
        return ignoreUpstreamTriggers;
    }

    @DataBoundSetter
    public void setIgnoreUpstreamTriggers(boolean ignoreUpstreamTriggers) {
        this.ignoreUpstreamTriggers = ignoreUpstreamTriggers;
    }

    public String getLifecycleThreshold() {
        return lifecycleThreshold;
    }

    @DataBoundSetter
    public void setLifecycleThreshold(String lifecycleThreshold) {
        this.lifecycleThreshold = lifecycleThreshold;
    }

    @Symbol("pipelineGraphPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Pipeline Graph Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-pipeline-graph";
        }

        /**
         * Only propose "package", "install" and "deploy" because the other lifecycle phases are unlikely to be useful
         * @return
         */
        public ListBoxModel doFillLifecycleThresholdItems() {
            ListBoxModel options = new ListBoxModel();

            options.add("package");
            options.add("install");
            options.add("deploy");

            return options;
        }
    }
}
