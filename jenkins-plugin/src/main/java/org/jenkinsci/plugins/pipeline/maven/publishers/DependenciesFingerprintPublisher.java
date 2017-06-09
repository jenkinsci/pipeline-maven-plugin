package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Fingerprinter;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Fingerprint the dependencies of the maven project.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependenciesFingerprintPublisher extends MavenPublisher {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MavenSpyLogProcessor.class.getName());

    private boolean includeSnapshotVersions = true;

    private boolean includeReleaseVersions;

    private boolean includeScopeCompile = true;

    private boolean includeScopeRuntime;

    private boolean includeScopeTest;

    private boolean includeScopeProvided = true;

    @DataBoundConstructor
    public DependenciesFingerprintPublisher() {
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

        FilePath workspace = context.get(FilePath.class);

        List<MavenSpyLogProcessor.MavenDependency> dependencies = listDependencies(mavenSpyLogsElt);

        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] dependenciesFingerprintPublisher - filter: " +
                    "versions[snapshot: " + isIncludeSnapshotVersions() + ", release: " + isIncludeReleaseVersions() + "], " +
                    "scopes:" + getIncludedScopes());
        }

        Map<String, String> artifactsToFingerPrint = new HashMap<>(); // artifactPathInFingerprintZone -> artifactMd5
        for (MavenSpyLogProcessor.MavenDependency dependency : dependencies) {
            if (dependency.isSnapshot()) {
                if (!includeSnapshotVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] Skip fingerprinting snapshot dependency: " + dependency);
                    }
                    continue;
                }
            } else {
                if (!includeReleaseVersions) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] Skip fingerprinting release dependency: " + dependency);
                    }
                    continue;
                }
            }
            if (!getIncludedScopes().contains(dependency.getScope())) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    listener.getLogger().println("[withMaven] Skip fingerprinting dependency with ignored scope: " + dependency);
                }
                continue;
            }

            try {
                if (StringUtils.isEmpty(dependency.file)) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        listener.getLogger().println("[withMaven] Can't fingerprint maven dependency with no file attached: " + dependency);
                    }
                    continue;
                }

                FilePath dependencyFilePath = new FilePath(workspace, dependency.file);

                if (!(dependency.file.endsWith("." + dependency.extension))) {
                    if (dependencyFilePath.isDirectory()) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            listener.getLogger().println("[withMaven] Skip fingerprinting of maven dependency of type directory " + dependency);
                        }
                        continue;
                    }
                }

                String dependencyMavenRepoStyleFilePath =
                        dependency.groupId.replace('.', '/') + "/" +
                                dependency.artifactId + "/" +
                                dependency.version + "/" +
                                dependency.getFileName();


                if (dependencyFilePath.exists()) {
                    // the subsequent call to digest could test the existence but we don't want to prematurely optimize performances
                    if (LOGGER.isLoggable(Level.FINE)) {
                        listener.getLogger().println("[withMaven] Fingerprint dependency " + dependencyMavenRepoStyleFilePath);
                    }
                    String artifactDigest = dependencyFilePath.digest();
                    artifactsToFingerPrint.put(dependencyMavenRepoStyleFilePath, artifactDigest);
                } else {
                    listener.getLogger().println("[withMaven] FAILURE to fingerprint " + dependencyMavenRepoStyleFilePath + ", file not found");
                }

            } catch (IOException | RuntimeException e) {
                listener.error("[withMaven] WARNING: Exception fingerprinting " + dependency + ", skip");
                e.printStackTrace(listener.getLogger());
                listener.getLogger().flush();
            }
        }
        LOGGER.log(Level.FINER, "Fingerprint {0}", artifactsToFingerPrint);

        // FINGERPRINT GENERATED MAVEN ARTIFACT
        FingerprintMap fingerprintMap = Jenkins.getInstance().getFingerprintMap();
        for (Map.Entry<String, String> artifactToFingerprint : artifactsToFingerPrint.entrySet()) {
            String artifactPathInFingerprintZone = artifactToFingerprint.getKey();
            String artifactMd5 = artifactToFingerprint.getValue();
            fingerprintMap.getOrCreate(run, artifactPathInFingerprintZone, artifactMd5).addFor(run);
        }

        // add action
        Fingerprinter.FingerprintAction fingerprintAction = run.getAction(Fingerprinter.FingerprintAction.class);
        if (fingerprintAction == null) {
            run.addAction(new Fingerprinter.FingerprintAction(run, artifactsToFingerPrint));
        } else {
            fingerprintAction.add(artifactsToFingerPrint);
        }
    }

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenSpyLogProcessor.MavenArtifact}
     */
    @Nonnull
    public List<MavenSpyLogProcessor.MavenDependency> listDependencies(Element mavenSpyLogs) {

        List<MavenSpyLogProcessor.MavenDependency> result = new ArrayList<>();

        for (Element dependencyResolutionResult : XmlUtils.getChildrenElements(mavenSpyLogs, "DependencyResolutionResult")) {
            Element resolvedDependenciesElt = XmlUtils.getUniqueChildElementOrNull(dependencyResolutionResult, "resolvedDependencies");

            if (resolvedDependenciesElt == null) {
                continue;
            }

            for (Element dependencyElt : XmlUtils.getChildrenElements(resolvedDependenciesElt, "dependency")) {
                MavenSpyLogProcessor.MavenDependency dependencyArtifact = XmlUtils.newMavenDependency(dependencyElt);

                Element fileElt = XmlUtils.getUniqueChildElementOrNull(dependencyElt, "file");
                if (fileElt == null | fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                    LOGGER.log(Level.WARNING, "listDependencies: no associated file found for " + dependencyArtifact + " in " + XmlUtils.toString(dependencyElt));
                } else {
                    dependencyArtifact.file = StringUtils.trim(fileElt.getTextContent());
                }

                result.add(dependencyArtifact);
            }
        }

        return result;
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

    @Symbol("dependenciesFingerprintPublisher")
    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Dependencies Fingerprint Publisher";
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @Nonnull
        @Override
        public String getSkipFileName() {
            return ".skip-fingerprint-maven-dependencies";
        }
    }
}
