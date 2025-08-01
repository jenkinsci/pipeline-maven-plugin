/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.security.CredentialsHelper;
import org.jenkinsci.plugins.configfiles.maven.security.MavenServerIdRequirement;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.jenkinsci.plugins.pipeline.maven.console.MaskPasswordsConsoleLogFilter;
import org.jenkinsci.plugins.pipeline.maven.console.MavenColorizerConsoleLogFilter;
import org.jenkinsci.plugins.pipeline.maven.util.FileUtils;
import org.jenkinsci.plugins.pipeline.maven.util.MavenVersion;
import org.jenkinsci.plugins.pipeline.maven.util.MavenVersionUtils;
import org.jenkinsci.plugins.pipeline.maven.util.TaskListenerTraceWrapper;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.util.ClassUtils;

@SuppressFBWarnings(
        value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
        justification = "Contextual fields used only in start(); no onResume needed")
class WithMavenStepExecution2 extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1L;
    private static final String M2_HOME = "M2_HOME";
    private static final String MAVEN_HOME = "MAVEN_HOME";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";
    /**
     * Environment variable of the path to the wrapped "mvn" command, you can just invoke "$MVN_CMD clean package"
     */
    private static final String MVN_CMD = "MVN_CMD";

    private static final Logger LOGGER = Logger.getLogger(WithMavenStepExecution2.class.getName());

    private final transient WithMavenStep step;
    private final transient TaskListener listener;
    private final transient FilePath ws;
    private final transient Launcher launcher;
    private final transient EnvVars env;
    /*
     * TODO document the role of envOverride in regard to env. cleclerc suspects that the environment variables defined
     * in "envOverride" will override the environment variables defined in "env"
     */
    private transient EnvVars envOverride;
    private final transient Run<? extends Job<?, ?>, ? extends Run<?, ?>> build;

    private transient Computer computer;
    private transient FilePath tempBinDir;

    /**
     * Indicates if running on docker with <code>docker.image()</code>
     */
    private boolean withContainer;

    private transient TaskListenerTraceWrapper console;

    WithMavenStepExecution2(StepContext context, WithMavenStep step) throws Exception {
        super(context);
        this.step = step;
        // Or just delete these fields and inline:
        listener = context.get(TaskListener.class);
        ws = context.get(FilePath.class);
        launcher = context.get(Launcher.class);
        env = context.get(EnvVars.class);
        build = context.get(Run.class);
    }

    @Override
    public boolean start() throws Exception {
        run(this::doStart);
        return false;
    }

    protected boolean doStart() throws Exception {
        envOverride = new EnvVars();
        console = new TaskListenerTraceWrapper(listener, computeTraceability());

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Maven: {0}", step.getMaven());
            LOGGER.log(Level.FINE, "Jdk: {0}", step.getJdk());
            LOGGER.log(Level.FINE, "MavenOpts: {0}", step.getMavenOpts());
            LOGGER.log(Level.FINE, "Temporary Binary Directory: {0}", step.getTempBinDir());
            LOGGER.log(Level.FINE, "Settings Config: {0}", step.getMavenSettingsConfig());
            LOGGER.log(Level.FINE, "Settings FilePath: {0}", step.getMavenSettingsFilePath());
            LOGGER.log(Level.FINE, "Global settings Config: {0}", step.getGlobalMavenSettingsConfig());
            LOGGER.log(Level.FINE, "Global settings FilePath: {0}", step.getGlobalMavenSettingsFilePath());
            LOGGER.log(Level.FINE, "Options: {0}", step.getOptions());
            LOGGER.log(Level.FINE, "env.PATH: {0}", env.get("PATH")); // JENKINS-40484
            LOGGER.log(Level.FINE, "ws: {0}", ws.getRemote()); // JENKINS-47804
        }

        console.trace("[withMaven] Options: " + step.getOptions());
        ExtensionList<MavenPublisher> availableMavenPublishers = Jenkins.get().getExtensionList(MavenPublisher.class);
        console.trace("[withMaven] Available options: "
                + availableMavenPublishers.stream()
                        .map(MavenPublisher::toString)
                        .collect(Collectors.joining(",")));

        getComputer();

        withContainer = detectWithContainer();

        setupJDK();

        // list of credentials injected by withMaven. They will be tracked and masked in the logs
        Collection<Credentials> credentials = new ArrayList<>();
        setupMaven(credentials);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(
                    Level.FINE,
                    this.build + " - Track usage and mask password of credentials "
                            + credentials.stream()
                                    .map(new CredentialsToPrettyString())
                                    .collect(Collectors.joining(",")));
        }
        CredentialsProvider.trackAll(build, new ArrayList<>(credentials));

        ConsoleLogFilter originalFilter = getContext().get(ConsoleLogFilter.class);
        ConsoleLogFilter maskSecretsFilter = MaskPasswordsConsoleLogFilter.newMaskPasswordsConsoleLogFilter(
                credentials, getComputer().getDefaultCharset());
        MavenColorizerConsoleLogFilter mavenColorizerFilter = new MavenColorizerConsoleLogFilter(
                getComputer().getDefaultCharset().name());

        ConsoleLogFilter newFilter = BodyInvoker.mergeConsoleLogFilters(
                BodyInvoker.mergeConsoleLogFilters(originalFilter, maskSecretsFilter), mavenColorizerFilter);

        EnvironmentExpander envEx =
                EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(envOverride));

        LOGGER.log(Level.FINEST, "envOverride: {0}", envOverride); // JENKINS-40484

        getContext()
                .newBodyInvoker()
                .withContexts(envEx, newFilter)
                .withCallback(
                        new WithMavenStepExecutionCallBack(tempBinDir, step.getOptions(), step.getPublisherStrategy()))
                .start();

        return false;
    }

    /**
     * Detects if this step is running inside <code>docker.image()</code>
     * <p>
     * This has the following implications:
     * <li>Tool installers do no work, as they install in the host, see:
     * https://issues.jenkins-ci.org/browse/JENKINS-36159
     * <li>Environment variables do not apply because they belong either to the master or the agent, but not to the
     * container running the <code>sh</code> command for maven This is due to the fact that <code>docker.image()</code> all it
     * does is decorate the launcher and execute the command with a <code>docker run</code> which means that the inherited
     * environment from the OS will be totally different eg: MAVEN_HOME, JAVA_HOME, PATH, etc.
     *
     * @return true if running inside a container with <code>docker.image()</code>
     * @see <a href=
     * "https://github.com/jenkinsci/docker-workflow-plugin/blob/master/src/main/java/org/jenkinsci/plugins/docker/workflow/WithContainerStep.java">
     * WithContainerStep</a>
     */
    private boolean detectWithContainer() throws IOException {
        Launcher launcher1 = launcher;
        while (launcher1 instanceof Launcher.DecoratedLauncher) {
            String launcherClassName = launcher1.getClass().getName();
            Optional<Boolean> withContainer = isWithContainerLauncher(launcherClassName);
            if (withContainer.isPresent()) {
                boolean result = withContainer.get();
                if (result) {
                    console.trace(
                            "[withMaven] IMPORTANT \"withMaven(){...}\" step running within a Docker container. See ");
                    console.traceHyperlink(
                            "https://github.com/jenkinsci/pipeline-maven-plugin/blob/master/FAQ.adoc#how-to-use-the-pipeline-maven-plugin-with-docker",
                            "Pipeline Maven Plugin FAQ");
                    console.trace(" in case of problem.");
                }

                return result;
            }

            launcher1 = ((Launcher.DecoratedLauncher) launcher1).getInner();
        }
        return false;
    }

    /**
     * Check if the launcher class name is a known container launcher.
     * @param launcherClassName launcher class name
     * @return empty if unknown and should keep checking, true if it is a container launcher, false if it is not.
     */
    @Restricted(NoExternalUse.class)
    protected static Optional<Boolean> isWithContainerLauncher(String launcherClassName) {
        // kubernetes-plugin container step execution does not require special container handling
        if (launcherClassName.contains("org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator")) {
            LOGGER.log(Level.FINE, "Step running within Kubernetes withContainer(): {1}", launcherClassName);
            return Optional.of(false);
        }

        // for plugins that require special container handling should include this name launcher naming convention
        // since there is no common interface to detect if a step is running within a container
        if (launcherClassName.contains("ContainerExecDecorator")) {
            LOGGER.log(Level.FINE, "Step running within container exec decorator: {0}", launcherClassName);
            return Optional.of(true);
        }

        // detect docker.image().inside {} or withDockerContainer step from docker-workflow-plugin, which has the
        // launcher name org.jenkinsci.plugins.docker.workflow.WithContainerStep.Decorator
        if (launcherClassName.contains("WithContainerStep")) {
            LOGGER.log(Level.FINE, "Step running within docker.image(): {0}", launcherClassName);
            return Optional.of(true);
        }

        return Optional.empty();
    }

    /**
     * Setup the selected JDK. If none is provided nothing is done.
     */
    private void setupJDK() throws AbortException, IOException, InterruptedException {
        String jdkInstallationName = step.getJdk();
        if (StringUtils.isEmpty(jdkInstallationName)) {
            console.trace("[withMaven] using JDK installation provided by the build agent");
            return;
        }

        if (withContainer) {
            // see #detectWithContainer()
            LOGGER.log(Level.FINE, "Ignoring JDK installation parameter: {0}", jdkInstallationName);
            console.println("WARNING: \"withMaven(){...}\" step running within a container,"
                    + " tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. "
                    + "You have specified a JDK installation \""
                    + jdkInstallationName + "\", which will be ignored.");
            return;
        }

        console.trace("[withMaven] using JDK installation " + jdkInstallationName);

        JDK jdk = Jenkins.get().getJDK(jdkInstallationName);
        if (jdk == null) {
            throw new AbortException("Could not find the JDK installation: " + jdkInstallationName
                    + ". Make sure it is configured on the Global Tool Configuration page");
        }
        Node node = getComputer().getNode();
        if (node == null) {
            throw new AbortException("Could not obtain the Node for the computer: "
                    + getComputer().getName());
        }
        jdk = jdk.forNode(node, listener).forEnvironment(env);
        jdk.buildEnvVars(envOverride);
    }

    /**
     * @param credentials list of credentials injected by withMaven. They will be tracked and masked in the logs.
     * @throws IOException
     * @throws InterruptedException
     */
    private void setupMaven(@NonNull Collection<Credentials> credentials) throws IOException, InterruptedException {
        // Temp dir with the wrapper that will be prepended to the path and the temporary files used by withMaven
        // (settings files...)
        if (step.getTempBinDir() != null && !step.getTempBinDir().isEmpty()) {
            String expandedTargetLocation = step.getTempBinDir();
            try {
                expandedTargetLocation = TokenMacro.expandAll(build, ws, listener, expandedTargetLocation);
            } catch (MacroEvaluationException e) {
                listener.getLogger()
                        .println("[ERROR] failed to expand variables in target location '" + expandedTargetLocation
                                + "' : " + e.getMessage());
            }
            tempBinDir = new FilePath(ws, expandedTargetLocation);
        }
        if (tempBinDir == null) {
            tempBinDir = tempDir(ws)
                    .child("withMaven"
                            + Util.getDigestOf(UUID.randomUUID().toString()).substring(0, 8));
        }
        tempBinDir.mkdirs();
        envOverride.put("MVN_CMD_DIR", tempBinDir.getRemote());

        // SETTINGS FILES
        String settingsFilePath = setupSettingFile(credentials);
        String globalSettingsFilePath = setupGlobalSettingFile(credentials);

        // LOCAL REPOSITORY
        String mavenLocalRepo = setupMavenLocalRepo();

        // MAVEN EVENT SPY
        FilePath mavenSpyJarPath = setupMavenSpy();

        //
        // JAVA_TOOL_OPTIONS
        // https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html
        String javaToolsOptions = env.get("JAVA_TOOL_OPTIONS", "");
        if (StringUtils.isNotEmpty(javaToolsOptions)) {
            javaToolsOptions += " ";
        }
        javaToolsOptions += "-Dmaven.ext.class.path=\"" + mavenSpyJarPath.getRemote() + "\" "
                + "-Dorg.jenkinsci.plugins.pipeline.maven.reportsFolder=\"" + this.tempBinDir.getRemote() + "\" ";
        envOverride.put("JAVA_TOOL_OPTIONS", javaToolsOptions);

        // MAVEN SCRIPT WRAPPER
        String mvnExecPath = obtainMavenExec();
        MavenVersion mvnVersion = readMavenVersion(mvnExecPath);
        if (!mvnVersion.isAtLeast(3, 8)) {
            console.println("[withMaven] WARNING: You are running an old version of Maven (" + mvnVersion
                    + "), you should update to at least 3.8.x");
        }

        //
        // MAVEN_CONFIG
        boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());
        StringBuilder mavenConfig = new StringBuilder();
        mavenConfig.append("--batch-mode ");
        if (mvnVersion.isAtLeast(3, 6, 1)) {
            ifTraceabilityDisabled(() -> mavenConfig.append("--no-transfer-progress "));
        }
        ifTraceabilityEnabled(() -> mavenConfig.append("--show-version "));
        if (StringUtils.isNotEmpty(settingsFilePath)) {
            // JENKINS-57324 escape '%' as '%%'. See
            // https://en.wikibooks.org/wiki/Windows_Batch_Scripting#Quoting_and_escaping
            if (!isUnix) settingsFilePath = settingsFilePath.replace("%", "%%");
            mavenConfig.append("--settings \"").append(settingsFilePath).append("\" ");
        }
        if (StringUtils.isNotEmpty(globalSettingsFilePath)) {
            // JENKINS-57324 escape '%' as '%%'. See
            // https://en.wikibooks.org/wiki/Windows_Batch_Scripting#Quoting_and_escaping
            if (!isUnix) globalSettingsFilePath = globalSettingsFilePath.replace("%", "%%");
            mavenConfig
                    .append("--global-settings \"")
                    .append(globalSettingsFilePath)
                    .append("\" ");
        }
        if (StringUtils.isNotEmpty(mavenLocalRepo)) {
            // JENKINS-57324 escape '%' as '%%'. See
            // https://en.wikibooks.org/wiki/Windows_Batch_Scripting#Quoting_and_escaping
            if (!isUnix) mavenLocalRepo = mavenLocalRepo.replace("%", "%%");
            mavenConfig.append("\"-Dmaven.repo.local=").append(mavenLocalRepo).append("\" ");
        }

        envOverride.put("MAVEN_CONFIG", mavenConfig.toString());

        //
        // MAVEN_OPTS
        if (StringUtils.isNotEmpty(step.getMavenOpts())) {
            String mavenOpts = envOverride.expand(env.expand(step.getMavenOpts()));

            String mavenOpsOriginal = env.get(MAVEN_OPTS);
            if (mavenOpsOriginal != null) {
                mavenOpts = mavenOpts + " " + mavenOpsOriginal;
            }
            envOverride.put(MAVEN_OPTS, mavenOpts.replaceAll("[\t\r\n]+", " "));
        }

        LOGGER.log(Level.FINE, "Using temp dir: {0}", tempBinDir.getRemote());

        if (mvnExecPath == null) {
            // 'mvn' execuable not found. Cannot create a script wrapper.
        } else {
            FilePath mvnExec = new FilePath(ws.getChannel(), mvnExecPath);
            String content = generateMavenWrapperScriptContent(mvnExec, mavenConfig.toString());

            // ADD MAVEN WRAPPER SCRIPT PARENT DIRECTORY TO PATH
            // WARNING MUST BE INVOKED AFTER obtainMavenExec(), THERE SEEM TO BE A BUG IN ENVIRONMENT VARIABLE HANDLING
            // IN obtainMavenExec()
            envOverride.put("PATH+MAVEN", tempBinDir.getRemote());

            createWrapperScript(tempBinDir, mvnExec.getName(), content);
        }
    }

    private FilePath setupMavenSpy() throws IOException, InterruptedException {
        if (tempBinDir == null) {
            throw new IllegalStateException("tempBinDir not defined");
        }

        // Mostly for testing / debugging in the IDE
        final String MAVEN_SPY_JAR_URL = "org.jenkinsci.plugins.pipeline.maven.mavenSpyJarUrl";
        String mavenSpyJarUrl = System.getProperty(MAVEN_SPY_JAR_URL);
        InputStream in;
        if (mavenSpyJarUrl == null) {
            String embeddedMavenSpyJarPath = "META-INF/lib/pipeline-maven-spy.jar";
            LOGGER.log(Level.FINE, "Load embedded maven spy jar '" + embeddedMavenSpyJarPath + "'");
            // Don't use Thread.currentThread().getContextClassLoader() as it doesn't show the resources of the plugin
            Class<WithMavenStepExecution2> clazz = WithMavenStepExecution2.class;
            ClassLoader classLoader = clazz.getClassLoader();
            LOGGER.log(
                    Level.FINE,
                    "Load " + embeddedMavenSpyJarPath + " using classloader " + classLoader.getClass() + ": "
                            + classLoader);
            in = classLoader.getResourceAsStream(embeddedMavenSpyJarPath);
            if (in == null) {
                CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
                String msg = "Embedded maven spy jar not found at " + embeddedMavenSpyJarPath
                        + " in the pipeline-maven-plugin classpath. "
                        + "Maven Spy Jar URL can be defined with the system property: '"
                        + MAVEN_SPY_JAR_URL + "'" + "Classloader "
                        + classLoader.getClass() + ": " + classLoader + ". " + "Class "
                        + clazz.getName() + " loaded from "
                        + (codeSource == null ? "#unknown#" : codeSource.getLocation());
                throw new IllegalStateException(msg);
            }
        } else {
            LOGGER.log(
                    Level.FINE,
                    "Load maven spy jar provided by system property '" + MAVEN_SPY_JAR_URL + "': " + mavenSpyJarUrl);
            in = new URL(mavenSpyJarUrl).openStream();
        }

        FilePath mavenSpyJarFilePath = tempBinDir.child("pipeline-maven-spy.jar");
        mavenSpyJarFilePath.copyFrom(in);
        return mavenSpyJarFilePath;
    }

    /**
     * Find the "mvn" executable if exists, either specified by the "withMaven(){}" step or provided by the build agent.
     *
     * @return remote path to the Maven executable or {@code null} if none found
     * @throws IOException
     * @throws InterruptedException
     */
    @Nullable
    private String obtainMavenExec() throws IOException, InterruptedException {
        String mavenInstallationName = step.getMaven();
        LOGGER.log(Level.FINE, "Setting up maven: {0}", mavenInstallationName);

        StringBuilder consoleMessage = new StringBuilder("[withMaven]");
        String mvnExecPath;

        if (StringUtils.isEmpty(mavenInstallationName)) {
            // no maven installation name is passed, we will search for the Maven installation on the agent
            consoleMessage.append(" using Maven installation provided by the build agent");
        } else if (withContainer) {
            console.println(
                    "[withMaven] WARNING: Specified Maven '" + mavenInstallationName
                            + "' cannot be installed, will be ignored. "
                            + "Step running within a container, tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. ");
            LOGGER.log(
                    Level.FINE,
                    "Running in docker-pipeline, ignore Maven Installation parameter: {0}",
                    mavenInstallationName);
        } else {
            return obtainMvnExecutableFromMavenInstallation(mavenInstallationName);
        }

        // in case there are no installations available we fallback to the OS maven installation
        // first we try MAVEN_HOME and M2_HOME
        LOGGER.fine("Searching for Maven through MAVEN_HOME and M2_HOME environment variables...");

        if (withContainer) {
            // in case of docker.image we need to execute a command through the decorated launcher and get the output.
            LOGGER.fine("Calling printenv on docker container...");
            String mavenHome = readFromProcess("printenv", MAVEN_HOME);
            if (mavenHome == null) {
                mavenHome = readFromProcess("printenv", M2_HOME);
                if (StringUtils.isNotEmpty(mavenHome)) {
                    consoleMessage
                            .append(" with the environment variable M2_HOME=")
                            .append(mavenHome);
                }
            } else {
                consoleMessage
                        .append(" with the environment variable MAVEN_HOME=")
                        .append(mavenHome);
            }

            if (mavenHome == null) {
                LOGGER.log(
                        Level.FINE,
                        "NO maven installation discovered on docker container through MAVEN_HOME and M2_HOME environment variables");
                mvnExecPath = null;
            } else {
                LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                mvnExecPath = mavenHome + "/bin/mvn"; // we can safely assume *nix
            }
        } else {
            // if not on docker we can use the computer environment
            LOGGER.fine("Using computer environment...");
            LOGGER.log(Level.FINE, "Agent env: {0}", env);
            String mavenHome = env.get(MAVEN_HOME);
            if (mavenHome == null) {
                mavenHome = env.get(M2_HOME);
                if (StringUtils.isNotEmpty(mavenHome)) {
                    consoleMessage
                            .append(" with the environment variable M2_HOME=")
                            .append(mavenHome);
                }
            } else {
                consoleMessage
                        .append(" with the environment variable MAVEN_HOME=")
                        .append(mavenHome);
            }
            if (mavenHome == null) {
                LOGGER.log(
                        Level.FINE,
                        "NO maven installation discovered on build agent through MAVEN_HOME and M2_HOME environment variables");
                mvnExecPath = null;
            } else {
                LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                // Resort to maven installation to get the executable and build environment
                MavenInstallation mavenInstallation = new MavenInstallation("Maven Auto-discovered", mavenHome, null);
                mavenInstallation.buildEnvVars(envOverride);
                mvnExecPath = mavenInstallation.getExecutable(launcher);
            }
        }

        // if at this point mvnExecPath is still null try to use which/where command to find a maven executable
        if (mvnExecPath == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                console.trace(
                        "[withMaven] No Maven Installation or MAVEN_HOME found, looking for mvn executable by using which/where command");
            }
            if (Boolean.TRUE.equals(getComputer().isUnix())) {
                mvnExecPath = readFromProcess("/bin/sh", "-c", "which mvn");
            } else {
                mvnExecPath = readFromProcess("where", "mvn.cmd");
                if (mvnExecPath == null) {
                    mvnExecPath = readFromProcess("where", "mvn.bat");
                }
            }
            if (mvnExecPath == null) {
                boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());
                String mvnwScript = isUnix ? "mvnw" : "mvnw.cmd";
                boolean mvnwScriptExists = ws.child(mvnwScript).exists();
                if (mvnwScriptExists) {
                    consoleMessage =
                            new StringBuilder("[withMaven] Maven installation not specified in the 'withMaven()' step "
                                    + "and not found on the build agent but '" + mvnwScript
                                    + "' script found in the workspace.");
                } else {
                    consoleMessage =
                            new StringBuilder("[withMaven] Maven installation not specified in the 'withMaven()' step "
                                    + "and not found on the build agent");
                }
            } else {
                consoleMessage.append(" with executable ").append(mvnExecPath);
            }
        }

        console.trace(consoleMessage.toString());

        LOGGER.log(Level.FINE, "Found exec for maven on: {0}", mvnExecPath);
        return mvnExecPath;
    }

    private String obtainMvnExecutableFromMavenInstallation(String mavenInstallationName)
            throws IOException, InterruptedException {

        MavenInstallation mavenInstallation = null;
        for (MavenInstallation i : getMavenInstallations()) {
            if (mavenInstallationName.equals(i.getName())) {
                mavenInstallation = i;
                LOGGER.log(Level.FINE, "Found maven installation {0} with installation home {1}", new Object[] {
                    mavenInstallation.getName(), mavenInstallation.getHome()
                });
                break;
            }
        }
        if (mavenInstallation == null) {
            throw new AbortException("Could not find specified Maven installation '" + mavenInstallationName + "'.");
        }
        Node node = getComputer().getNode();
        if (node == null) {
            throw new AbortException("Could not obtain the Node for the computer: "
                    + getComputer().getName());
        }
        mavenInstallation = mavenInstallation.forNode(node, listener).forEnvironment(env);
        mavenInstallation.buildEnvVars(envOverride);
        console.trace("[withMaven] using Maven installation '" + mavenInstallation.getName() + "'");

        return mavenInstallation.getExecutable(launcher);
    }

    private MavenVersion readMavenVersion(String mvnExecPath) {
        try {
            try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                    ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
                ProcStarter ps = launcher.launch();
                Proc p = launcher.launch(
                        ps.cmds(mvnExecPath, "--version").stdout(stdout).stderr(stderr));
                int exitCode = p.join();
                if (exitCode == 0) {
                    Optional<String> version = stdout.toString(getComputer().getDefaultCharset())
                            .lines()
                            .filter(MavenVersionUtils.containsMavenVersion())
                            .findFirst();
                    console.trace("[withMaven] found Maven version: " + version.orElse("none"));
                    return version.map(MavenVersionUtils::parseMavenVersion).orElse(MavenVersion.UNKNOWN);
                } else {
                    console.trace("[withMaven] failed to read Maven version (" + exitCode + "): "
                            + stderr.toString(getComputer().getDefaultCharset()));
                    return MavenVersion.UNKNOWN;
                }
            }
        } catch (Exception ex) {
            console.trace("[withMaven] failed to read Maven version: " + ex.getMessage());
            return MavenVersion.UNKNOWN;
        }
    }

    /**
     * Executes a command and reads the result to a string. It uses the launcher to run the command to make sure the
     * launcher decorator is used ie. docker.image step
     *
     * @param args command arguments
     * @return output from the command or {@code null} if the command returned a non zero code
     * @throws InterruptedException if interrupted
     */
    @Nullable
    private String readFromProcess(String... args) throws InterruptedException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ProcStarter ps = launcher.launch();
            Proc p = launcher.launch(ps.cmds(args).stdout(baos));
            int exitCode = p.join();
            if (exitCode == 0) {
                return baos.toString(getComputer().getDefaultCharset().name())
                        .replaceAll("[\t\r\n]+", " ")
                        .trim();
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace(
                    console.format("Error executing command '%s' : %s%n", Arrays.toString(args), e.getMessage()));
        }
        return null;
    }

    /**
     * Generates the content of the maven wrapper script
     *
     * @param mvnExec maven executable location
     * @param mavenConfig config arguments added to the "mvn" command line
     * @return wrapper script content
     * @throws AbortException when problems creating content
     */
    private String generateMavenWrapperScriptContent(@NonNull FilePath mvnExec, @NonNull String mavenConfig)
            throws AbortException {

        boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());

        StringBuilder script = new StringBuilder();

        if (isUnix) { // Linux, Unix, MacOSX
            String lineSep = "\n";
            script.append("#!/bin/sh -e").append(lineSep);
            ifTraceabilityEnabled(() ->
                    script.append("echo ----- withMaven Wrapper script -----").append(lineSep));
            script.append("\"")
                    .append(mvnExec.getRemote())
                    .append("\" ")
                    .append(mavenConfig)
                    .append(" \"$@\"")
                    .append(lineSep);

        } else { // Windows
            String lineSep = "\r\n";
            script.append("@echo off").append(lineSep);
            ifTraceabilityEnabled(() ->
                    script.append("echo ----- withMaven Wrapper script -----").append(lineSep));
            // JENKINS-57324 escape '%' as '%%'. See
            // https://en.wikibooks.org/wiki/Windows_Batch_Scripting#Quoting_and_escaping
            mavenConfig = mavenConfig.replace("%", "%%");
            script.append("\"")
                    .append(mvnExec.getRemote())
                    .append("\" ")
                    .append(mavenConfig)
                    .append(" %*")
                    .append(lineSep);
        }

        LOGGER.log(Level.FINER, "Generated Maven wrapper script: \n{0}", script);
        return script.toString();
    }

    /**
     * Creates the actual wrapper script file and sets the permissions.
     *
     * @param tempBinDir dir to create the script file
     * @param name       the script file name
     * @param content    contents of the file
     * @return
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    private FilePath createWrapperScript(FilePath tempBinDir, String name, String content)
            throws IOException, InterruptedException {
        FilePath scriptFile = tempBinDir.child(name);
        envOverride.put(MVN_CMD, scriptFile.getRemote());

        scriptFile.write(content, getComputer().getDefaultCharset().name());
        scriptFile.chmod(0755);

        return scriptFile;
    }

    /**
     * Sets the maven repo location according to the provided parameter on the agent
     *
     * @return path on the build agent to the repo or {@code null} if not defined
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    @Nullable
    private String setupMavenLocalRepo() throws IOException, InterruptedException {
        String expandedMavenLocalRepo;
        if (StringUtils.isEmpty(step.getMavenLocalRepo())) {
            expandedMavenLocalRepo = null;
        } else {
            // resolve relative/absolute with workspace as base
            String expandedPath = envOverride.expand(env.expand(step.getMavenLocalRepo()));
            if (FileUtils.isAbsolutePath(expandedPath)) {
                expandedMavenLocalRepo = expandedPath;
            } else {
                FilePath repoPath = new FilePath(ws, expandedPath);
                repoPath.mkdirs();
                expandedMavenLocalRepo = repoPath.getRemote();
            }
        }
        LOGGER.log(Level.FINEST, "setupMavenLocalRepo({0}): {1}", new Object[] {
            step.getMavenLocalRepo(), expandedMavenLocalRepo
        });
        return expandedMavenLocalRepo;
    }

    /**
     * Obtains the selected setting file, and initializes MVN_SETTINGS When the selected file is an absolute path, the
     * file existence is checked on the build agent, if not found, it will be checked and copied from the master. The
     * file will be generated/copied to the workspace temp folder to make sure docker container can access it.
     *
     * @param credentials list of credentials injected by withMaven. They will be tracked and masked in the logs.
     * @return the maven settings file path on the agent or {@code null} if none defined
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    @Nullable
    private String setupSettingFile(@NonNull Collection<Credentials> credentials)
            throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("settings.xml");

        // Settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getMavenSettingsConfig())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                console.formatTrace(
                        "[withMaven] using Maven settings provided by the Jenkins Managed Configuration File '%s' %n",
                        step.getMavenSettingsConfig());
            }
            settingsFromConfig(step.getMavenSettingsConfig(), settingsDest, credentials);
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        // Settings from the file path
        if (StringUtils.isNotEmpty(step.getMavenSettingsFilePath())) {
            String settingsPath = step.getMavenSettingsFilePath();
            FilePath settings;

            if ((settings = ws.child(settingsPath)).exists()) {
                // settings file residing on the agent
                if (LOGGER.isLoggable(Level.FINE)) {
                    console.formatTrace(
                            "[withMaven] using Maven settings provided on the build agent '%s' %n", settingsPath);
                    LOGGER.log(Level.FINE, "Copying maven settings file from build agent {0} to {1}", new Object[] {
                        settings, settingsDest
                    });
                }
                settings.copyTo(settingsDest);
                envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
                return settingsDest.getRemote();
            } else {
                throw new AbortException("Could not find file '" + settings + "' on the build agent");
            }
        }

        SettingsProvider settingsProvider;

        MavenConfigFolderOverrideProperty overrideProperty = getMavenConfigOverrideProperty();
        StringBuilder mavenSettingsLog = new StringBuilder();

        if (overrideProperty != null && overrideProperty.getSettings() != null) {
            // Settings overridden by a folder property
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog
                        .append("[withMaven] using overridden Maven settings by folder '")
                        .append(overrideProperty.getOwner().getDisplayName())
                        .append("'. ");
            }
            settingsProvider = overrideProperty.getSettings();
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append(
                        "[withMaven] using Maven settings provided by the Jenkins global configuration. ");
            }
            // Settings provided by the global maven configuration
            settingsProvider = GlobalMavenConfig.get().getSettingsProvider();
        }

        if (settingsProvider instanceof MvnSettingsProvider) {
            MvnSettingsProvider mvnSettingsProvider = (MvnSettingsProvider) settingsProvider;
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog
                        .append("Config File Provider maven settings file '")
                        .append(mvnSettingsProvider.getSettingsConfigId())
                        .append("'");
                console.trace(mavenSettingsLog);
            }
            settingsFromConfig(mvnSettingsProvider.getSettingsConfigId(), settingsDest, credentials);
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (settingsProvider instanceof FilePathSettingsProvider) {
            FilePathSettingsProvider filePathSettingsProvider = (FilePathSettingsProvider) settingsProvider;
            String settingsPath = filePathSettingsProvider.getPath();
            FilePath settings;
            if ((settings = ws.child(settingsPath)).exists()) {
                // Settings file residing on the agent
                settings.copyTo(settingsDest);
                envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
                if (LOGGER.isLoggable(Level.FINE)) {
                    mavenSettingsLog
                            .append("Maven settings on the build agent'")
                            .append(settingsPath)
                            .append("'");
                    console.trace(mavenSettingsLog);
                }
                return settingsDest.getRemote();
            } else {
                throw new AbortException("Could not find file provided by the Jenkins global configuration '" + settings
                        + "' on the build agent");
            }

        } else if (settingsProvider instanceof DefaultSettingsProvider) {
            // do nothing
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append("Maven settings defined by 'DefaultSettingsProvider', NOT overriding it.");
                console.trace(mavenSettingsLog);
            }
        } else if (settingsProvider == null) {
            // should not happen according to the source code of jenkins.mvn.MavenConfig.getSettingsProvider() in
            // jenkins-core 2.7
            // do nothing
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append("Maven settings are null. NO settings will be defined.");
                console.trace(mavenSettingsLog);
            }
        } else {
            console.trace("[withMaven] Ignore unsupported Maven SettingsProvider " + settingsProvider);
        }

        return null;
    }

    @CheckForNull
    private MavenConfigFolderOverrideProperty getMavenConfigOverrideProperty() {
        Job<?, ?> job = build.getParent(); // Get the job

        // Iterate until we find an override or until we reach the top. We need it to be an item to be able to do
        // getParent, AbstractFolder which has the properties is also an Item
        for (ItemGroup<?> group = job.getParent();
                group instanceof Item && !(group instanceof Jenkins);
                group = ((Item) group).getParent()) {
            if (group instanceof AbstractFolder) {
                MavenConfigFolderOverrideProperty mavenConfigProperty =
                        ((AbstractFolder<?>) group).getProperties().get(MavenConfigFolderOverrideProperty.class);
                if (mavenConfigProperty != null && mavenConfigProperty.isOverride()) {
                    return mavenConfigProperty;
                }
            }
        }
        return null;
    }

    /**
     * Obtains the selected global setting file, and initializes GLOBAL_MVN_SETTINGS When the selected file is an absolute path, the
     * file existence is checked on the build agent, if not found, it will be checked and copied from the master. The
     * file will be generated/copied to the workspace temp folder to make sure docker container can access it.
     *
     * @param credentials list of credentials injected by withMaven. They will be tracked and masked in the logs.
     * @return the maven global settings file path on the agent or {@code null} if none defined
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    @Nullable
    private String setupGlobalSettingFile(@NonNull Collection<Credentials> credentials)
            throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("globalSettings.xml");

        // Global settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getGlobalMavenSettingsConfig())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                console.formatTrace(
                        "[withMaven] using Maven global settings provided by the Jenkins Managed Configuration File '%s' %n",
                        step.getGlobalMavenSettingsConfig());
            }
            globalSettingsFromConfig(step.getGlobalMavenSettingsConfig(), settingsDest, credentials);
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        // Global settings from the file path
        if (StringUtils.isNotEmpty(step.getGlobalMavenSettingsFilePath())) {
            String settingsPath = step.getGlobalMavenSettingsFilePath();
            FilePath settings;
            if ((settings = ws.child(settingsPath)).exists()) {
                // Global settings file residing on the agent
                if (LOGGER.isLoggable(Level.FINE)) {
                    console.formatTrace(
                            "[withMaven] using Maven global settings provided on the build agent '%s' %n",
                            settingsPath);
                    LOGGER.log(
                            Level.FINE,
                            "Copying maven global settings file from build agent {0} to {1}",
                            new Object[] {settings, settingsDest});
                }
                settings.copyTo(settingsDest);
                envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
                return settingsDest.getRemote();
            } else {
                throw new AbortException("Could not find file '" + settings + "' on the build agent");
            }
        }

        // Settings provided by the global maven configuration
        GlobalSettingsProvider globalSettingsProvider;
        MavenConfigFolderOverrideProperty overrideProperty = getMavenConfigOverrideProperty();

        StringBuilder mavenSettingsLog = new StringBuilder();
        if (overrideProperty == null || overrideProperty.getGlobalSettings() == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append(
                        "[withMaven] using Maven global settings provided by the Jenkins global configuration. ");
            }
            // Settings provided by the global maven configuration
            globalSettingsProvider = GlobalMavenConfig.get().getGlobalSettingsProvider();
        } else {
            // Settings overridden by a folder property
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog
                        .append("[withMaven] using overridden Maven global settings by folder '")
                        .append(overrideProperty.getOwner().getDisplayName())
                        .append("'. ");
            }
            globalSettingsProvider = overrideProperty.getGlobalSettings();
        }

        if (globalSettingsProvider instanceof MvnGlobalSettingsProvider) {
            MvnGlobalSettingsProvider mvnGlobalSettingsProvider = (MvnGlobalSettingsProvider) globalSettingsProvider;
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog
                        .append("Config File Provider maven global settings file '")
                        .append(mvnGlobalSettingsProvider.getSettingsConfigId())
                        .append("'");
            }
            globalSettingsFromConfig(mvnGlobalSettingsProvider.getSettingsConfigId(), settingsDest, credentials);
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            if (LOGGER.isLoggable(Level.FINE)) {
                console.trace(mavenSettingsLog);
            }
            return settingsDest.getRemote();
        } else if (globalSettingsProvider instanceof FilePathGlobalSettingsProvider) {
            FilePathGlobalSettingsProvider filePathGlobalSettingsProvider =
                    (FilePathGlobalSettingsProvider) globalSettingsProvider;
            String settingsPath = filePathGlobalSettingsProvider.getPath();
            FilePath settings;
            if ((settings = ws.child(settingsPath)).exists()) {
                // Global settings file residing on the agent
                if (LOGGER.isLoggable(Level.FINE)) {
                    mavenSettingsLog
                            .append("Maven global settings on the build agent '")
                            .append(settingsPath)
                            .append("'");
                }
                settings.copyTo(settingsDest);
                envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
                if (LOGGER.isLoggable(Level.FINE)) {
                    console.trace(mavenSettingsLog);
                }
                return settingsDest.getRemote();
            } else {
                throw new AbortException("Could not find file provided by the Jenkins global configuration '" + settings
                        + "' on the build agent");
            }
        } else if (globalSettingsProvider instanceof DefaultGlobalSettingsProvider) {
            // do nothing
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append(
                        "Maven global settings defined by 'DefaultSettingsProvider', NOT overriding it.");
                console.trace(mavenSettingsLog);
            }
        } else if (globalSettingsProvider == null) {
            // should not happen according to the source code of
            // jenkins.mvn.GlobalMavenConfig.getGlobalSettingsProvider() in jenkins-core 2.7
            // do nothing
            if (LOGGER.isLoggable(Level.FINE)) {
                mavenSettingsLog.append("Maven global settings are null. NO settings will be defined.");
                console.trace(mavenSettingsLog);
            }
        } else {
            console.trace("[withMaven] Ignore unsupported Maven GlobalSettingsProvider " + globalSettingsProvider);
        }

        return null;
    }

    /**
     * Reads the config file from Config File Provider, expands the credentials and stores it in a file on the temp
     * folder to use it with the maven wrapper script
     *
     * @param mavenSettingsConfigId config file id from Config File Provider
     * @param mavenSettingsFile     path to write te content to
     * @param credentials
     * @return the {@link FilePath} to the settings file
     * @throws AbortException in case of error
     */
    private void settingsFromConfig(
            String mavenSettingsConfigId, FilePath mavenSettingsFile, @NonNull Collection<Credentials> credentials)
            throws AbortException {

        Config c = ConfigFiles.getByIdOrNull(build, mavenSettingsConfigId);
        if (c == null) {
            throw new AbortException("Could not find the Maven settings.xml config file id:" + mavenSettingsConfigId
                    + ". Make sure it exists on Managed Files");
        }
        if (StringUtils.isBlank(c.content)) {
            throw new AbortException("Could not create Maven settings.xml config file id:" + mavenSettingsConfigId
                    + ". Content of the file is empty");
        }

        MavenSettingsConfig mavenSettingsConfig;
        if (c instanceof MavenSettingsConfig) {
            mavenSettingsConfig = (MavenSettingsConfig) c;
        } else {
            mavenSettingsConfig = new MavenSettingsConfig(
                    c.id, c.name, c.comment, c.content, MavenSettingsConfig.isReplaceAllDefault, null);
        }

        try {
            final Map<String, StandardUsernameCredentials> resolvedCredentialsByMavenServerId =
                    resolveCredentials(mavenSettingsConfig.getServerCredentialMappings(), "Maven settings");

            String mavenSettingsFileContent;
            if (resolvedCredentialsByMavenServerId.isEmpty()) {
                mavenSettingsFileContent = mavenSettingsConfig.content;
                if (LOGGER.isLoggable(Level.FINE)) {
                    console.trace("[withMaven] using Maven settings.xml '" + mavenSettingsConfig.id
                            + "' with NO Maven servers credentials provided by Jenkins");
                }
            } else {
                credentials.addAll(resolvedCredentialsByMavenServerId.values());
                List<String> tempFiles = new ArrayList<>();
                mavenSettingsFileContent = CredentialsHelper.fillAuthentication(
                        mavenSettingsConfig.content,
                        mavenSettingsConfig.isReplaceAll,
                        resolvedCredentialsByMavenServerId,
                        tempBinDir,
                        tempFiles);
                if (LOGGER.isLoggable(Level.FINE)) {
                    console.trace("[withMaven] using Maven settings.xml '" + mavenSettingsConfig.id
                            + "' with Maven servers credentials provided by Jenkins " + "(replaceAll: "
                            + mavenSettingsConfig.isReplaceAll + "): "
                            + resolvedCredentialsByMavenServerId.entrySet().stream()
                                    .map(new MavenServerToCredentialsMappingToStringFunction())
                                    .sorted()
                                    .collect(Collectors.joining(", ")));
                }
            }

            mavenSettingsFile.write(mavenSettingsFileContent, "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Exception injecting Maven settings.xml " + mavenSettingsConfig.id + " during the build: " + build
                            + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * Reads the global config file from Config File Provider, expands the credentials and stores it in a file on the temp
     * folder to use it with the maven wrapper script
     *
     * @param mavenGlobalSettingsConfigId global config file id from Config File Provider
     * @param mavenGlobalSettingsFile     path to write te content to
     * @param credentials
     * @return the {@link FilePath} to the settings file
     * @throws AbortException in case of error
     */
    private void globalSettingsFromConfig(
            String mavenGlobalSettingsConfigId, FilePath mavenGlobalSettingsFile, Collection<Credentials> credentials)
            throws AbortException {

        Config c = ConfigFiles.getByIdOrNull(build, mavenGlobalSettingsConfigId);
        if (c == null) {
            throw new AbortException("Could not find the Maven global settings.xml config file id:"
                    + mavenGlobalSettingsFile + ". Make sure it exists on Managed Files");
        }
        if (StringUtils.isBlank(c.content)) {
            throw new AbortException("Could not create Maven global settings.xml config file id:"
                    + mavenGlobalSettingsFile + ". Content of the file is empty");
        }

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig;
        if (c instanceof GlobalMavenSettingsConfig) {
            mavenGlobalSettingsConfig = (GlobalMavenSettingsConfig) c;
        } else {
            mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig(
                    c.id, c.name, c.comment, c.content, MavenSettingsConfig.isReplaceAllDefault, null);
        }

        try {
            final Map<String, StandardUsernameCredentials> resolvedCredentialsByMavenServerId = resolveCredentials(
                    mavenGlobalSettingsConfig.getServerCredentialMappings(), " Global Maven settings");

            String mavenGlobalSettingsFileContent;
            if (resolvedCredentialsByMavenServerId.isEmpty()) {
                mavenGlobalSettingsFileContent = mavenGlobalSettingsConfig.content;
                console.trace("[withMaven] using Maven global settings.xml '" + mavenGlobalSettingsConfig.id
                        + "' with NO Maven servers credentials provided by Jenkins");

            } else {
                credentials.addAll(resolvedCredentialsByMavenServerId.values());

                List<String> tempFiles = new ArrayList<>();
                mavenGlobalSettingsFileContent = CredentialsHelper.fillAuthentication(
                        mavenGlobalSettingsConfig.content,
                        mavenGlobalSettingsConfig.isReplaceAll,
                        resolvedCredentialsByMavenServerId,
                        tempBinDir,
                        tempFiles);
                console.trace("[withMaven] using Maven global settings.xml '" + mavenGlobalSettingsConfig.id
                        + "' with Maven servers credentials provided by Jenkins " + "(replaceAll: "
                        + mavenGlobalSettingsConfig.isReplaceAll + "): "
                        + resolvedCredentialsByMavenServerId.entrySet().stream()
                                .map(new MavenServerToCredentialsMappingToStringFunction())
                                .sorted()
                                .collect(Collectors.joining(", ")));
            }

            mavenGlobalSettingsFile.write(mavenGlobalSettingsFileContent, "UTF-8");
            LOGGER.log(Level.FINE, "Created global config file {0}", new Object[] {mavenGlobalSettingsFile});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Exception injecting Maven settings.xml " + mavenGlobalSettingsConfig.id + " during the build: "
                            + build + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     *
     * @param serverCredentialMappings
     * @param logMessagePrefix
     * @return credentials by Maven server Id
     */
    @NonNull
    public Map<String, StandardUsernameCredentials> resolveCredentials(
            @Nullable final List<ServerCredentialMapping> serverCredentialMappings, String logMessagePrefix) {
        // CredentialsHelper.removeMavenServerDefinitions() requires a Map implementation that supports `null` values.
        // `HashMap` supports `null` values, `TreeMap` doesn't
        // https://github.com/jenkinsci/config-file-provider-plugin/blob/config-file-provider-2.16.4/src/main/java/org/jenkinsci/plugins/configfiles/maven/security/CredentialsHelper.java#L252
        Map<String, StandardUsernameCredentials> mavenServerIdToCredentials = new HashMap<>();
        if (serverCredentialMappings == null) {
            return mavenServerIdToCredentials;
        }
        List<ServerCredentialMapping> unresolvedServerCredentialsMappings = new ArrayList<>();
        for (ServerCredentialMapping serverCredentialMapping : serverCredentialMappings) {

            List<DomainRequirement> domainRequirements = StringUtils.isBlank(serverCredentialMapping.getServerId())
                    ? Collections.emptyList()
                    : Collections.singletonList(new MavenServerIdRequirement(serverCredentialMapping.getServerId()));
            @Nullable
            final StandardUsernameCredentials credentials = CredentialsProvider.findCredentialById(
                    serverCredentialMapping.getCredentialsId(),
                    StandardUsernameCredentials.class,
                    build,
                    domainRequirements);

            if (credentials == null) {
                unresolvedServerCredentialsMappings.add(serverCredentialMapping);
            } else {
                mavenServerIdToCredentials.put(serverCredentialMapping.getServerId(), credentials);
            }
        }
        if (!unresolvedServerCredentialsMappings.isEmpty()) {
            /*
             * we prefer to print a warning message rather than failing the build with an AbortException if some credentials are NOT found for backward compatibility reasons.
             * The behaviour of o.j.p.configfiles.m.s.CredentialsHelper.resolveCredentials(model.Run, List<ServerCredentialMapping>, TaskListener)` is to just print a warning message
             */
            console.println("[withMaven] WARNING " + logMessagePrefix
                    + " - Silently skip Maven server Ids with missing associated Jenkins credentials: "
                    + unresolvedServerCredentialsMappings.stream()
                            .map(new ServerCredentialMappingToStringFunction())
                            .collect(Collectors.joining(", ")));
        }
        return mavenServerIdToCredentials;
    }

    private void ifTraceabilityDisabled(Runnable runnable) {
        if (!computeTraceability()) {
            runnable.run();
        }
    }

    private void ifTraceabilityEnabled(Runnable runnable) {
        if (computeTraceability()) {
            runnable.run();
        }
    }

    private boolean computeTraceability() {
        return GlobalPipelineMavenConfig.get().isGlobalTraceability() && step.isTraceability() == null
                || Boolean.TRUE.equals(step.isTraceability());
    }

    /**
     * Takes care of overriding the environment with our defined overrides
     */
    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String, String> overrides;

        private ExpanderImpl(EnvVars overrides) {
            LOGGER.log(Level.FINEST, "ExpanderImpl(overrides: {0})", new Object[] {overrides});
            this.overrides = new HashMap<>();
            for (Entry<String, String> entry : overrides.entrySet()) {
                this.overrides.put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            LOGGER.log(
                    Level.FINEST, "ExpanderImpl.expand - env before expand: {0}", new Object[] {env}); // JENKINS-40484
            env.overrideAll(overrides);
            LOGGER.log(
                    Level.FINEST, "ExpanderImpl.expand - env after expand: {0}", new Object[] {env}); // JENKINS-40484
        }
    }

    /**
     * Callback to cleanup tmp script after finishing the job
     */
    private class WithMavenStepExecutionCallBack extends TailCall {
        @Deprecated
        private FilePath tempBinDir;

        private final String tempBinDirPath;

        private final MavenPublisherStrategy mavenPublisherStrategy;

        private final List<MavenPublisher> options;

        private final MavenSpyLogProcessor mavenSpyLogProcessor = new MavenSpyLogProcessor();

        private WithMavenStepExecutionCallBack(
                @NonNull FilePath tempBinDir,
                @NonNull List<MavenPublisher> options,
                @NonNull MavenPublisherStrategy mavenPublisherStrategy) {
            this.tempBinDirPath = tempBinDir.getRemote();
            this.options = options;
            this.mavenPublisherStrategy = mavenPublisherStrategy;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            TaskListener listener = context.get(TaskListener.class);
            if (tempBinDir == null) { // normal case
                FilePath ws = context.get(FilePath.class);
                if (ws == null) {
                    listener.getLogger().println("Missing agent to clean up " + tempBinDirPath);
                    return;
                }
                tempBinDir = ws.child(tempBinDirPath);
            } // else resuming old build

            mavenSpyLogProcessor.processMavenSpyLogs(context, tempBinDir, options, mavenPublisherStrategy);

            try {
                tempBinDir.deleteRecursive();
            } catch (IOException | InterruptedException e) {
                try {
                    if (e instanceof IOException) {
                        Util.displayIOException((IOException) e, listener); // Better IOException display on windows
                    }
                    Functions.printStackTrace(e, listener.fatalError("Error deleting temporary files"));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * @return maven installations on this instance
     */
    private static MavenInstallation[] getMavenInstallations() {
        return Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
    }

    /**
     * Gets the computer for the current launcher.
     *
     * @return the computer
     * @throws AbortException in case of error.
     */
    @NonNull
    private Computer getComputer() throws AbortException {
        if (computer != null) {
            return computer;
        }

        String node = null;
        Jenkins j = Jenkins.get();

        for (Computer c : j.getComputers()) {
            if (c.getChannel() == launcher.getChannel()) {
                node = c.getName();
                break;
            }
        }

        if (node == null) {
            throw new AbortException("Could not find computer for the job");
        }

        computer = j.getComputer(node);
        if (computer == null) {
            throw new AbortException("No such computer " + node);
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Computer: {0}", computer.getName());
            try {
                LOGGER.log(Level.FINE, "Env: {0}", computer.getEnvironment());
            } catch (IOException | InterruptedException e) { // ignored
            }
        }
        return computer;
    }

    /**
     * Calculates a temporary dir path
     *
     * @param ws current workspace
     * @return the temporary dir
     */
    private static FilePath tempDir(FilePath ws) {
        return WorkspaceList.tempDir(ws);
    }

    private static class ServerCredentialMappingToStringFunction implements Function<ServerCredentialMapping, String> {
        @Override
        public String apply(ServerCredentialMapping mapping) {
            return "[mavenServerId: " + mapping.getServerId() + ", jenkinsCredentials: " + mapping.getCredentialsId()
                    + "]";
        }
    }

    /**
     * ToString of the mapping mavenServerId -> Credentials
     */
    private static class MavenServerToCredentialsMappingToStringFunction
            implements Function<Entry<String, StandardUsernameCredentials>, String> {
        @Override
        public String apply(@Nullable Entry<String, StandardUsernameCredentials> entry) {
            if (entry == null) return null;
            String mavenServerId = entry.getKey();
            StandardUsernameCredentials credentials = entry.getValue();
            return "[" + "mavenServerId: '"
                    + mavenServerId + "', " + "jenkinsCredentials: '"
                    + credentials.getId() + "'" + "]";
        }
    }

    private static class CredentialsToPrettyString implements Function<Credentials, String> {
        @Override
        public String apply(@javax.annotation.Nullable Credentials credentials) {
            if (credentials == null) return "null";

            String result = ClassUtils.getShortName(credentials.getClass()) + "[";
            if (credentials instanceof IdCredentials) {
                IdCredentials idCredentials = (IdCredentials) credentials;
                result += "id: " + idCredentials.getId();
            }

            result += "]";
            return result;
        }
    }
}
