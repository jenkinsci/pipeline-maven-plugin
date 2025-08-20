package org.jenkinsci.plugins.maven;

import java.util.function.Consumer;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.PageArea;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;

@Describable("Provide Maven environment")
public class WithMaven extends PageAreaImpl {

    private final Control maven = control("maven");
    private final Control jdk = control("jdk");
    private final Control tempBinDir = control("tempBinDir");
    private final Control mavenSettingsConfig = control("mavenSettingsConfig");
    private final Control mavenSettingsFilePath = control("mavenSettingsFilePath");
    private final Control globalMavenSettingsConfig = control("globalMavenSettingsConfig");
    private final Control globalMavenSettingsFilePath = control("globalMavenSettingsFilePath");
    private final Control mavenOpts = control("mavenOpts");
    private final Control traceability = control("traceability");
    private final Control mavenLocalRepo = control("mavenLocalRepo");
    private final Control publisherStrategy = control("publisherStrategy");
    private final Control options = findRepeatableAddButtonFor("options");

    public WithMaven(final WithMavenSnippetGenerator parent, final String path) {
        super(parent, path);
    }

    public String getMaven() {
        return maven.get();
    }

    public WithMaven setMaven(String maven) {
        this.maven.select(maven);

        return this;
    }

    public String getJDK() {
        return jdk.get();
    }

    public WithMaven setJDK(String jdk) {
        this.jdk.select(jdk);

        return this;
    }

    public String getTempBinDir() {
        return tempBinDir.get();
    }

    public WithMaven setTempBinDir(String tempBinDir) {
        this.tempBinDir.set(tempBinDir);

        return this;
    }

    public String getMavenSettingsConfig() {
        return mavenSettingsConfig.get();
    }

    public WithMaven setMavenSettingsConfig(String mavenSettingsConfig) {
        this.mavenSettingsConfig.select(mavenSettingsConfig);

        return this;
    }

    public String getMavenSettingsFilePath() {
        return mavenSettingsFilePath.get();
    }

    public WithMaven setMavenSettingsFilePath(String mavenSettingsFilePath) {
        this.mavenSettingsFilePath.set(mavenSettingsFilePath);

        return this;
    }

    public String getGlobalMavenSettingsConfig() {
        return globalMavenSettingsConfig.get();
    }

    public WithMaven setGlobalMavenSettingsConfig(String globalMavenSettingsConfig) {
        this.globalMavenSettingsConfig.select(globalMavenSettingsConfig);

        return this;
    }

    public String getGlobalMavenSettingsFilePath() {
        return globalMavenSettingsFilePath.get();
    }

    public WithMaven setGlobalMavenSettingsFilePath(String globalMavenSettingsFilePath) {
        this.globalMavenSettingsFilePath.set(globalMavenSettingsFilePath);

        return this;
    }

    public String getMavenOpts() {
        return mavenOpts.get();
    }

    public WithMaven setMavenOpts(String mavenOpts) {
        this.mavenOpts.set(mavenOpts);

        return this;
    }

    public String getTraceability() {
        return traceability.get();
    }

    public WithMaven setTraceability(boolean traceability) {
        this.traceability.check(traceability);

        return this;
    }

    public String getMavenLocalRepo() {
        return mavenLocalRepo.get();
    }

    public WithMaven setMavenLocalRepo(String mavenLocalRepo) {
        this.mavenLocalRepo.set(mavenLocalRepo);

        return this;
    }

    public String getPublisherStrategy() {
        return publisherStrategy.get();
    }

    public WithMaven setPublisherStrategy(final PublisherStrategy publisherStrategy) {
        this.publisherStrategy.select(publisherStrategy.toString());

        return this;
    }

    public Publisher addPublisher(final String publisherName) {
        return createPublisherArea(publisherName);
    }

    public Publisher addPublisher(final String publisherName, final Consumer<Publisher> configuration) {
        Publisher tool = addPublisher(publisherName);
        configuration.accept(tool);
        return tool;
    }

    private Publisher createPublisherArea(final String publisherName) {
        String path = createPageArea("options", () -> options.selectDropdownMenu(publisherName));

        return new Publisher(this, path);
    }

    private Control findRepeatableAddButtonFor(final String propertyName) {
        return control(by.xpath("//button[@suffix='" + propertyName + "' and contains(@class,'-add')]"));
    }

    public enum PublisherStrategy {
        IMPLICIT,
        EXPLICIT
    }

    public enum SourceCodeRetention {
        NEVER,
        LAST_BUILD,
        EVERY_BUILD,
        MODIFIED
    }

    public enum LifecycleThreshold {
        PACKAGE,
        INSTALL,
        DEPLOY
    }

    public static class Publisher extends PageAreaImpl {
        private final Control disabled = control("disabled");

        private final Control sourceCodeRetention = control("sourceCodeRetention");
        private final Control coberturaExtraPattern = control("coberturaExtraPattern");
        private final Control jacocoExtraPattern = control("jacocoExtraPattern");

        private final Control includeSnapshotVersions = control("includeSnapshotVersions");
        private final Control includeReleaseVersions = control("includeReleaseVersions");
        private final Control includeScopeCompile = control("includeScopeCompile");
        private final Control includeScopeRuntime = control("includeScopeRuntime");
        private final Control includeScopeProvided = control("includeScopeProvided");
        private final Control includeScopeTest = control("includeScopeTest");

        private final Control ignoreAttachments = control("ignoreAttachments");
        private final Control healthScaleFactor = control("healthScaleFactor");
        private final Control keepLongStdio = control("keepLongStdio");

        private final Control lifecycleThreshold = control("lifecycleThreshold");
        private final Control skipDownstreamTriggers = control("skipDownstreamTriggers");
        private final Control ignoreUpstreamTriggers = control("ignoreUpstreamTriggers");

        Publisher(final PageArea issuesRecorder, final String path) {
            super(issuesRecorder, path);
        }

        public Publisher setDisabled(boolean disabled) {
            this.disabled.check(disabled);
            return this;
        }

        public Publisher setSourceCodeRetention(SourceCodeRetention sourceCodeRetention) {
            this.sourceCodeRetention.select(sourceCodeRetention.toString());
            return this;
        }

        public Publisher setCoberturaExtraPattern(String coberturaExtraPattern) {
            this.coberturaExtraPattern.set(coberturaExtraPattern);
            return this;
        }

        public Publisher setJacocoExtraPattern(String jacocoExtraPattern) {
            this.jacocoExtraPattern.set(jacocoExtraPattern);
            return this;
        }

        public Publisher setIncludeSnapshotVersions(boolean includeSnapshotVersions) {
            this.includeSnapshotVersions.check(includeSnapshotVersions);
            return this;
        }

        public Publisher setIncludeReleaseVersions(boolean includeReleaseVersions) {
            this.includeReleaseVersions.check(includeReleaseVersions);
            return this;
        }

        public Publisher setIncludeScopeCompile(boolean includeScopeCompile) {
            this.includeScopeCompile.check(includeScopeCompile);
            return this;
        }

        public Publisher setIncludeScopeRuntime(boolean includeScopeRuntime) {
            this.includeScopeRuntime.check(includeScopeRuntime);
            return this;
        }

        public Publisher setIncludeScopeProvided(boolean includeScopeProvided) {
            this.includeScopeProvided.check(includeScopeProvided);
            return this;
        }

        public Publisher setIncludeScopeTest(boolean includeScopeTest) {
            this.includeScopeTest.check(includeScopeTest);
            return this;
        }

        public Publisher setIgnoreAttachments(boolean ignoreAttachments) {
            this.ignoreAttachments.check(ignoreAttachments);
            return this;
        }

        public Publisher setHealthScaleFactor(String healthScaleFactor) {
            this.healthScaleFactor.set(healthScaleFactor);
            return this;
        }

        public Publisher setKeepLongStdio(boolean keepLongStdio) {
            this.keepLongStdio.check(keepLongStdio);
            return this;
        }

        public Publisher setLifecycleThreshold(LifecycleThreshold lifecycleThreshold) {
            this.lifecycleThreshold.select(lifecycleThreshold.toString().toLowerCase());
            return this;
        }

        public Publisher setSkipDownstreamTriggers(boolean skipDownstreamTriggers) {
            this.skipDownstreamTriggers.check(skipDownstreamTriggers);
            return this;
        }

        public Publisher setIgnoreUpstreamTriggers(boolean ignoreUpstreamTriggers) {
            this.ignoreUpstreamTriggers.check(ignoreUpstreamTriggers);
            return this;
        }
    }
}
