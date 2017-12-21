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


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.security.CredentialsHelper;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.jenkinsci.plugins.pipeline.maven.console.MaskPasswordsConsoleLogFilter;
import org.jenkinsci.plugins.pipeline.maven.console.MavenColorizerConsoleLogFilter;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.springframework.util.ClassUtils;

@SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Contextual fields used only in start(); no onResume needed")
class WithMavenStepExecution extends StepExecution {

    private static final long serialVersionUID = 1L;
    private static final String M2_HOME = "M2_HOME";
    private static final String MAVEN_HOME = "MAVEN_HOME";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";
    /**
     * Environment variable of the path to the wrapped "mvn" command, you can just invoke "$MVN_CMD clean package"
     */
    private static final String MVN_CMD = "MVN_CMD";
    /**
     * Environment variable of the path to the parent folder of the wrapper of the "mvn" command, you can add it to the "PATH" with "export PATH=$MVN_CMD_DIR:$PATH"
     */
    private static final String MVN_CMD_DIR = "MVN_CMD_DIR";

    private static final Logger LOGGER = Logger.getLogger(WithMavenStepExecution.class.getName());

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
    private transient BodyExecution body;

    /**
     * Indicates if running on docker with <tt>docker.image()</tt>
     */
    private boolean withContainer;

    private transient PrintStream console;

    WithMavenStepExecution(StepContext context, WithMavenStep step) throws Exception {
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
        envOverride = new EnvVars();
        console = listener.getLogger();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Maven: {0}", step.getMaven());
            LOGGER.log(Level.FINE, "Jdk: {0}", step.getJdk());
            LOGGER.log(Level.FINE, "MavenOpts: {0}", step.getMavenOpts());
            LOGGER.log(Level.FINE, "Settings Config: {0}", step.getMavenSettingsConfig());
            LOGGER.log(Level.FINE, "Settings FilePath: {0}", step.getMavenSettingsFilePath());
            LOGGER.log(Level.FINE, "Global settings Config: {0}", step.getGlobalMavenSettingsConfig());
            LOGGER.log(Level.FINE, "Global settings FilePath: {0}", step.getGlobalMavenSettingsFilePath());
            LOGGER.log(Level.FINE, "Options: {0}", step.getOptions());
            LOGGER.log(Level.FINE, "env.PATH: {0}", env.get("PATH")); // JENKINS-40484
            LOGGER.log(Level.FINE, "ws: {0}", ws.getRemote()); // JENKINS-47804
        }

        listener.getLogger().println("[withMaven] Options: " + step.getOptions());
        ExtensionList<MavenPublisher> availableMavenPublishers = Jenkins.getInstance().getExtensionList(MavenPublisher.class);
        listener.getLogger().println("[withMaven] Available options: " + Joiner.on(",").join(availableMavenPublishers));

        getComputer();

        withContainer = detectWithContainer();

        if (withContainer) {
            listener.getLogger().println("[withMaven] WARNING: \"withMaven(){...}\" step running within \"docker.image('image').inside {...}\"." +
                    " Since the Docker Pipeline Plugin version 1.14, you MUST:");
            listener.getLogger().println("[withMaven] * Either prepend the 'MVN_CMD_DIR' environment variable" +
                    " to the 'PATH' environment variable in every 'sh' step that invokes 'mvn' (e.g. \"sh \'export PATH=$MVN_CMD_DIR:$PATH && mvn clean deploy\' \"). ");
            listener.getLogger().print("[withMaven] * Or use ");
            listener.hyperlink("https://github.com/takari/maven-wrapper", "Takari's Maven Wrapper");
            listener.getLogger().println(" (e.g. \"sh './mvnw clean deploy'\")");
            listener.getLogger().print("[withMaven] See ");
            listener.hyperlink("https://wiki.jenkins.io/display/JENKINS/Pipeline+Maven+Plugin#PipelineMavenPlugin-HowtousethePipelineMavenPluginwithDocker", "Pipeline Maven Plugin FAQ");
            listener.getLogger().println(".");
        }

        setupJDK();

        // list of credentials injected by withMaven. They will be tracked and masked in the logs
        Collection<Credentials> credentials = new ArrayList<>();
        setupMaven(credentials);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, this.build + " - Track usage and mask password of credentials " + Joiner.on(", ").join(Collections2.transform(credentials, new CredentialsToPrettyString())));
        }
        CredentialsProvider.trackAll(build, new ArrayList<>(credentials));

        ConsoleLogFilter originalFilter = getContext().get(ConsoleLogFilter.class);
        ConsoleLogFilter maskSecretsFilter = MaskPasswordsConsoleLogFilter.newMaskPasswordsConsoleLogFilter(credentials, getComputer().getDefaultCharset());
        MavenColorizerConsoleLogFilter mavenColorizerFilter = new MavenColorizerConsoleLogFilter(getComputer().getDefaultCharset().name());

        ConsoleLogFilter newFilter = BodyInvoker.mergeConsoleLogFilters(
                BodyInvoker.mergeConsoleLogFilters(originalFilter, maskSecretsFilter),
                mavenColorizerFilter);

        EnvironmentExpander envEx = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(envOverride));

        LOGGER.log(Level.FINEST, "envOverride: {0}", envOverride); // JENKINS-40484

        body = getContext().newBodyInvoker().withContexts(envEx, newFilter).withCallback(new WorkspaceCleanupCallback(tempBinDir, step.getOptions())).start();

        return false;
    }

    /**
     * Detects if this step is running inside <tt>docker.image()</tt>
     * <p>
     * This has the following implications:
     * <li>Tool intallers do no work, as they install in the host, see:
     * https://issues.jenkins-ci.org/browse/JENKINS-36159
     * <li>Environment variables do not apply because they belong either to the master or the agent, but not to the
     * container running the <tt>sh</tt> command for maven This is due to the fact that <tt>docker.image()</tt> all it
     * does is decorate the launcher and excute the command with a <tt>docker run</tt> which means that the inherited
     * environment from the OS will be totally different eg: MAVEN_HOME, JAVA_HOME, PATH, etc.
     *
     * @return true if running inside docker container with <tt>docker.image()</tt>
     * @see <a href=
     * "https://github.com/jenkinsci/docker-workflow-plugin/blob/master/src/main/java/org/jenkinsci/plugins/docker/workflow/WithContainerStep.java#L213">
     * WithContainerStep</a>
     */
    private boolean detectWithContainer() {
        Launcher launcher1 = launcher;
        while (launcher1 instanceof Launcher.DecoratedLauncher) {
            if (launcher1.getClass().getName().contains("WithContainerStep")) {
                LOGGER.fine("Step running within docker.image()");
                return true;
            }
            launcher1 = ((Launcher.DecoratedLauncher) launcher1).getInner();
        }
        return false;
    }

    /**
     * Setup the selected JDK. If none is provided nothing is done.
     */
    private void setupJDK() throws AbortException, IOException, InterruptedException {
        String jdkInstallationName = step.getJdk();
        if (StringUtils.isEmpty(jdkInstallationName)) {
            console.println("[withMaven] use JDK installation provided by the build agent");
            return;
        }

        if (withContainer) {
            // see #detectWithContainer()
            LOGGER.log(Level.FINE, "Ignoring JDK installation parameter: {0}", jdkInstallationName);
            console.println("WARNING: \"withMaven(){...}\" step running within \"docker.image().inside{...}\"," +
                    " tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. " +
                    "You have specified a JDK installation \"" + jdkInstallationName + "\", which will be ignored.");
            return;
        }

        console.println("[withMaven] use JDK installation " + jdkInstallationName);

        JDK jdk = Jenkins.getActiveInstance().getJDK(jdkInstallationName);
        if (jdk == null) {
            throw new AbortException("Could not find the JDK installation: " + jdkInstallationName + ". Make sure it is configured on the Global Tool Configuration page");
        }
        Node node = getComputer().getNode();
        if (node == null) {
            throw new AbortException("Could not obtain the Node for the computer: " + getComputer().getName());
        }
        jdk = jdk.forNode(node, listener).forEnvironment(env);
        jdk.buildEnvVars(envOverride);

    }

    /**
     * @param credentials list of credentials injected by withMaven. They will be tracked and masked in the logs.
     * @throws IOException
     * @throws InterruptedException
     */
    private void setupMaven(@Nonnull Collection<Credentials> credentials) throws IOException, InterruptedException {
        // Temp dir with the wrapper that will be prepended to the path and the temporary files used by withMaven (settings files...)
        tempBinDir = tempDir(ws).child("withMaven" + Util.getDigestOf(UUID.randomUUID().toString()).substring(0, 8));
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
        javaToolsOptions += "-Dmaven.ext.class.path=\"" + mavenSpyJarPath.getRemote() + "\" " +
                "-Dorg.jenkinsci.plugins.pipeline.maven.reportsFolder=\"" + this.tempBinDir.getRemote() + "\" ";
        envOverride.put("JAVA_TOOL_OPTIONS", javaToolsOptions);

        //
        // MAVEN_CONFIG
        StringBuilder mavenConfig = new StringBuilder();
        mavenConfig.append("--batch-mode ");
        mavenConfig.append("--show-version ");
        if (StringUtils.isNotEmpty(settingsFilePath)) {
            mavenConfig.append("--settings \"" + settingsFilePath + "\" ");
        }
        if (StringUtils.isNotEmpty(globalSettingsFilePath)) {
            mavenConfig.append("--global-settings \"" + globalSettingsFilePath + "\" ");
        }
        if (StringUtils.isNotEmpty(mavenLocalRepo)) {
            mavenConfig.append("-Dmaven.repo.local=\"" + mavenLocalRepo + "\" ");
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

        // MAVEN SCRIPT WRAPPER
        String mvnExecPath = obtainMavenExec();

        LOGGER.log(Level.FINE, "Using temp dir: {0}", tempBinDir.getRemote());

        if (mvnExecPath == null) {
            throw new AbortException("Couldn\u2019t find any maven executable");
        }

        FilePath mvnExec = new FilePath(ws.getChannel(), mvnExecPath);
        String content = generateMavenWrapperScriptContent(mvnExec, mavenConfig.toString());

        // ADD MAVEN WRAPPER SCRIPT PARENT DIRECTORY TO PATH
        // WARNING MUST BE INVOKED AFTER obtainMavenExec(), THERE SEEM TO BE A BUG IN ENVIRONMENT VARIABLE HANDLING IN obtainMavenExec()
        envOverride.put("PATH+MAVEN", tempBinDir.getRemote());

        createWrapperScript(tempBinDir, mvnExec.getName(), content);

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
            Class<WithMavenStepExecution> clazz = WithMavenStepExecution.class;
            ClassLoader classLoader = clazz.getClassLoader();
            LOGGER.log(Level.FINE, "Load " + embeddedMavenSpyJarPath + " using classloader " + classLoader.getClass() + ": " + classLoader);
            in = classLoader.getResourceAsStream(embeddedMavenSpyJarPath);
            if (in == null) {
                CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
                String msg = "Embedded maven spy jar not found at " + embeddedMavenSpyJarPath + " in the pipeline-maven-plugin classpath. " +
                        "Maven Spy Jar URL can be defined with the system property: '" + MAVEN_SPY_JAR_URL + "'" +
                        "Classloader " + classLoader.getClass() + ": " + classLoader + ". " +
                        "Class " + clazz.getName() + " loaded from " + (codeSource == null ? "#unknown#" : codeSource.getLocation());
                throw new IllegalStateException(msg);
            }
        } else {
            LOGGER.log(Level.FINE, "Load maven spy jar provided by system property '" + MAVEN_SPY_JAR_URL + "': " + mavenSpyJarUrl);
            in = new URL(mavenSpyJarUrl).openStream();
        }

        FilePath mavenSpyJarFilePath = tempBinDir.child("pipeline-maven-spy.jar");
        mavenSpyJarFilePath.copyFrom(in);
        return mavenSpyJarFilePath;
    }

    private String obtainMavenExec() throws IOException, InterruptedException {
        String mavenInstallationName = step.getMaven();
        LOGGER.log(Level.FINE, "Setting up maven: {0}", mavenInstallationName);

        StringBuilder consoleMessage = new StringBuilder("[withMaven]");

        MavenInstallation mavenInstallation;
        if (StringUtils.isEmpty(mavenInstallationName)) {
            // no maven installation name is passed, we will search for the Maven installation on the agent
            consoleMessage.append(" use Maven installation provided by the build agent");
            mavenInstallation = null;
        } else if (withContainer) {
            console.println(
                    "WARNING: Specified Maven '" + mavenInstallationName + "' cannot be installed, will be ignored." +
                            "Step running within docker.image() tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. ");
            LOGGER.log(Level.FINE, "Ignoring Maven Installation parameter: {0}", mavenInstallationName);
            mavenInstallation = null;
        } else {
            mavenInstallation = null;
            for (MavenInstallation i : getMavenInstallations()) {
                if (mavenInstallationName.equals(i.getName())) {
                    mavenInstallation = i;
                    consoleMessage.append(" use Maven installation '" + mavenInstallation.getName() + "'");
                    LOGGER.log(Level.FINE, "Found maven installation {0} with installation home {1}", new Object[]{mavenInstallation.getName(), mavenInstallation.getHome()});
                    break;
                }
            }
            if (mavenInstallation == null) {
                throw new AbortException("Could not find Maven installation '" + mavenInstallationName + "'.");
            }
        }


        String mvnExecPath;
        if (mavenInstallation == null) {
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
                        consoleMessage.append(" with the environment variable M2_HOME=" + mavenHome);
                    }
                } else {
                    consoleMessage.append(" with the environment variable MAVEN_HOME=" + mavenHome);
                }

                if (mavenHome == null) {
                    LOGGER.log(Level.FINE, "NO maven installation discovered on docker container through MAVEN_HOME and M2_HOME environment variables");
                    mvnExecPath = null;
                } else {
                    LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                    mvnExecPath = mavenHome + "/bin/mvn"; // we can safely assume *nix
                }
            } else {
                // if not on docker we can use the computer environment
                LOGGER.fine("Using computer environment...");
                EnvVars agentEnv = getComputer().getEnvironment();
                LOGGER.log(Level.FINE, "Agent env: {0}", agentEnv);
                String mavenHome = agentEnv.get(MAVEN_HOME);
                if (mavenHome == null) {
                    mavenHome = agentEnv.get(M2_HOME);
                    if (StringUtils.isNotEmpty(mavenHome)) {
                        consoleMessage.append(" with the environment variable M2_HOME=" + mavenHome);
                    }
                } else {
                    consoleMessage.append(" with the environment variable MAVEN_HOME=" + mavenHome);
                }
                if (mavenHome == null) {
                    LOGGER.log(Level.FINE, "NO maven installation discovered on build agent through MAVEN_HOME and M2_HOME environment variables");
                    mvnExecPath = null;
                } else {
                    LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                    // Resort to maven installation to get the executable and build environment
                    mavenInstallation = new MavenInstallation("Maven Auto-discovered", mavenHome, null);
                    mavenInstallation.buildEnvVars(envOverride);
                    mvnExecPath = mavenInstallation.getExecutable(launcher);
                }
            }
        } else {
            Node node = getComputer().getNode();
            if (node == null) {
                throw new AbortException("Could not obtain the Node for the computer: " + getComputer().getName());
            }
            mavenInstallation = mavenInstallation.forNode(node, listener).forEnvironment(env);
            mavenInstallation.buildEnvVars(envOverride);
            mvnExecPath = mavenInstallation.getExecutable(launcher);
        }

        // if at this point mvnExecPath is still null try to use which/where command to find a maven executable
        if (mvnExecPath == null) {
            LOGGER.fine("No Maven Installation or MAVEN_HOME found, looking for mvn executable by using which/where command");
            if (Boolean.TRUE.equals(getComputer().isUnix())) {
                mvnExecPath = readFromProcess("/bin/sh", "-c", "which mvn");
            } else {
                mvnExecPath = readFromProcess("where", "mvn.cmd");
                if (mvnExecPath == null) {
                    mvnExecPath = readFromProcess("where", "mvn.bat");
                }
            }
            consoleMessage.append(" with executable " + mvnExecPath);
        }
        console.println(consoleMessage.toString());

        if (mvnExecPath == null) {
            throw new AbortException("Could not find maven executable, please set up a Maven Installation or configure MAVEN_HOME or M2_HOME environment variable");
        }

        LOGGER.log(Level.FINE, "Found exec for maven on: {0}", mvnExecPath);
        return mvnExecPath;
    }

    /**
     * Executes a command and reads the result to a string. It uses the launcher to run the command to make sure the
     * launcher decorator is used ie. docker.image step
     *
     * @param args command arguments
     * @return output from the command
     * @throws InterruptedException if interrupted
     */
    @Nullable
    private String readFromProcess(String... args) throws InterruptedException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ProcStarter ps = launcher.launch();
            Proc p = launcher.launch(ps.cmds(args).stdout(baos));
            int exitCode = p.join();
            if (exitCode == 0) {
                return baos.toString(getComputer().getDefaultCharset().name()).replaceAll("[\t\r\n]+", " ").trim();
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace(console.format("Error executing command '%s' : %s%n", Arrays.toString(args), e.getMessage()));
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
    private String generateMavenWrapperScriptContent(@Nonnull FilePath mvnExec, @Nonnull String mavenConfig) throws AbortException {

        boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());

        StringBuilder script = new StringBuilder();

        if (isUnix) { // Linux, Unix, MacOSX
            String lineSep = "\n";
            script.append("#!/bin/sh -e").append(lineSep);
            script.append("echo ----- withMaven Wrapper script -----").append(lineSep);
            script.append("\"" + mvnExec.getRemote() + "\" " + mavenConfig + " \"$@\"").append(lineSep);

        } else { // Windows
            String lineSep = "\r\n";
            script.append("@echo off").append(lineSep);
            script.append("echo ----- withMaven Wrapper script -----").append(lineSep);
            script.append("\"" + mvnExec.getRemote() + "\" " + mavenConfig + " %*").append(lineSep);
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
    private FilePath createWrapperScript(FilePath tempBinDir, String name, String content) throws IOException, InterruptedException {
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
            FilePath repoPath = new FilePath(ws, expandedPath);
            repoPath.mkdirs();
            expandedMavenLocalRepo = repoPath.getRemote();
        }
        LOGGER.log(Level.FINEST, "setupMavenLocalRepo({0}): {1}", new Object[]{step.getMavenLocalRepo(), expandedMavenLocalRepo});
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
    private String setupSettingFile(@Nonnull Collection<Credentials> credentials) throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("settings.xml");

        // Settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getMavenSettingsConfig())) {
            console.format("[withMaven] use Maven settings provided by the Jenkins Managed Configuration File '%s' %n", step.getMavenSettingsConfig());
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
                console.format("[withMaven] use Maven settings provided on the build agent '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven settings file from build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file '" + settings + "' on the build agent");
            }
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        SettingsProvider settingsProvider;

        MavenConfigFolderOverrideProperty overrideProperty = getMavenConfigOverrideProperty();
        if (overrideProperty != null) {
            // Settings overriden by a folder property
            console.format("[withMaven] using overriden Maven settings by folder '%s'%n", overrideProperty.getOwner().getDisplayName());
            settingsProvider = overrideProperty.getSettings();
        } else {
            console.format("[withMaven] using Maven settings provided by the Jenkins global configuration%n");
            // Settings provided by the global maven configuration
            settingsProvider = GlobalMavenConfig.get().getSettingsProvider();
        }

        if (settingsProvider instanceof MvnSettingsProvider) {
            MvnSettingsProvider mvnSettingsProvider = (MvnSettingsProvider) settingsProvider;
            console.format("[withMaven] use Config File Provide maven settings file '%s' %n", mvnSettingsProvider.getSettingsConfigId());
            settingsFromConfig(mvnSettingsProvider.getSettingsConfigId(), settingsDest, credentials);
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (settingsProvider instanceof FilePathSettingsProvider) {
            FilePathSettingsProvider filePathSettingsProvider = (FilePathSettingsProvider) settingsProvider;
            String settingsPath = filePathSettingsProvider.getPath();
            FilePath settings;
            if ((settings = ws.child(settingsPath)).exists()) {
                // Settings file residing on the agent
                console.format("[withMaven] use Maven settings on the build agent '%s' %n", settingsPath);
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file provided by the Jenkins global configuration '" + settings + "' on the build agent");
            }
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (settingsProvider instanceof DefaultSettingsProvider) {
            // do nothing
        } else if (settingsProvider == null) {
            // should not happen according to the source code of jenkins.mvn.MavenConfig.getSettingsProvider() in jenkins-core 2.7
            // do nothing
        } else {
            console.println("[withMaven] Ignore unsupported Maven SettingsProvider " + settingsProvider);
        }

        return null;
    }

    @CheckForNull
    private MavenConfigFolderOverrideProperty getMavenConfigOverrideProperty() {
        Job<?, ?> job = build.getParent();
        ItemGroup<?> group = job.getParent();
        while (group != null && !(group instanceof Jenkins) && group instanceof AbstractFolder) {
            AbstractFolder<?> folder = (AbstractFolder<?>) group;
            MavenConfigFolderOverrideProperty mavenConfigProperty = folder.getProperties().get(MavenConfigFolderOverrideProperty.class);
            if (mavenConfigProperty != null && mavenConfigProperty.isOverride()) {
                return mavenConfigProperty;
            }
            group = folder.getParent();
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
    private String setupGlobalSettingFile(@Nonnull Collection<Credentials> credentials) throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("globalSettings.xml");

        // Global settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getGlobalMavenSettingsConfig())) {
            console.format("[withMaven] use Maven global settings provided by the Jenkins Managed Configuration File '%s' %n", step.getGlobalMavenSettingsConfig());
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
                console.format("[withMaven] use Maven global settings provided on the build agent '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven global settings file from build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file '" + settings + "' on the build agent");
            }
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        // Settings provided by the global maven configuration
        GlobalSettingsProvider globalSettingsProvider;

        MavenConfigFolderOverrideProperty overrideProperty = getMavenConfigOverrideProperty();
        if (overrideProperty != null) {
            // Settings overriden by a folder property
            console.format("[withMaven] using overriden Maven global settings by folder '%s'%n", overrideProperty.getOwner().getDisplayName());
            globalSettingsProvider = overrideProperty.getGlobalSettings();
        } else {
            console.format("[withMaven] using Maven global settings provided by the Jenkins global configuration%n");
            // Settings provided by the global maven configuration
            globalSettingsProvider = GlobalMavenConfig.get().getGlobalSettingsProvider();
        }

        if (globalSettingsProvider instanceof MvnGlobalSettingsProvider) {
            MvnGlobalSettingsProvider mvnGlobalSettingsProvider = (MvnGlobalSettingsProvider) globalSettingsProvider;
            console.format("[withMaven] use Config File Provide maven global settings file '%s' %n", mvnGlobalSettingsProvider.getSettingsConfigId());
            globalSettingsFromConfig(mvnGlobalSettingsProvider.getSettingsConfigId(), settingsDest, credentials);
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (globalSettingsProvider instanceof FilePathGlobalSettingsProvider) {
            FilePathGlobalSettingsProvider filePathGlobalSettingsProvider = (FilePathGlobalSettingsProvider) globalSettingsProvider;
            String settingsPath = filePathGlobalSettingsProvider.getPath();
            FilePath settings;
            if ((settings = ws.child(settingsPath)).exists()) {
                // Global settings file residing on the agent
                console.format("[withMaven] use Maven global settings on the build agent '%s' %n", settingsPath);
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file provided by the Jenkins global configuration '" + settings + "' on the build agent");
            }
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (globalSettingsProvider instanceof DefaultGlobalSettingsProvider) {
            // do nothing
        } else if (globalSettingsProvider == null) {
            // should not happen according to the source code of jenkins.mvn.GlobalMavenConfig.getGlobalSettingsProvider() in jenkins-core 2.7
            // do nothing
        } else {
            console.println("[withMaven] Ignore unsupported Maven GlobalSettingsProvider " + globalSettingsProvider);
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
    private void settingsFromConfig(String mavenSettingsConfigId, FilePath mavenSettingsFile, @Nonnull Collection<Credentials> credentials) throws AbortException {

        Config c = ConfigFiles.getByIdOrNull(build, mavenSettingsConfigId);
        if (c == null) {
            throw new AbortException("Could not find the Maven settings.xml config file id:" + mavenSettingsConfigId + ". Make sure it exists on Managed Files");
        }
        if (StringUtils.isBlank(c.content)) {
            throw new AbortException("Could not create Maven settings.xml config file id:" + mavenSettingsConfigId + ". Content of the file is empty");
        }

        MavenSettingsConfig mavenSettingsConfig;
        if (c instanceof MavenSettingsConfig) {
            mavenSettingsConfig = (MavenSettingsConfig) c;
        } else {
            mavenSettingsConfig = new MavenSettingsConfig(c.id, c.name, c.comment, c.content, MavenSettingsConfig.isReplaceAllDefault, null);
        }

        try {

            // JENKINS-43787 handle null
            final List<ServerCredentialMapping> serverCredentialMappings = Objects.firstNonNull(mavenSettingsConfig.getServerCredentialMappings(), Collections.<ServerCredentialMapping>emptyList());

            final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build, serverCredentialMappings);

            credentials.addAll(resolvedCredentials.values());

            String mavenSettingsFileContent;
            if (resolvedCredentials.isEmpty()) {
                mavenSettingsFileContent = mavenSettingsConfig.content;
                console.println("[withMaven] use Maven settings.xml '" + mavenSettingsConfig.id + "' with NO Maven servers credentials provided by Jenkins");
            } else {
                List<String> tempFiles = new ArrayList<String>();
                mavenSettingsFileContent = CredentialsHelper.fillAuthentication(mavenSettingsConfig.content, mavenSettingsConfig.isReplaceAll, resolvedCredentials, tempBinDir, tempFiles);
                console.println("[withMaven] use Maven settings.xml '" + mavenSettingsConfig.id + "' with Maven servers credentials provided by Jenkins " +
                        "(replaceAll: " + mavenSettingsConfig.isReplaceAll + "): " +
                        Joiner.on(", ").skipNulls().join(Iterables.transform(resolvedCredentials.entrySet(), new MavenServerToCredentialsMappingToStringFunction())));
            }

            mavenSettingsFile.write(mavenSettingsFileContent, getComputer().getDefaultCharset().name());
        } catch (Exception e) {
            throw new IllegalStateException("Exception injecting Maven settings.xml " + mavenSettingsConfig.id +
                    " during the build: " + build + ": " + e.getMessage(), e);
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
    private void globalSettingsFromConfig(String mavenGlobalSettingsConfigId, FilePath mavenGlobalSettingsFile, Collection<Credentials> credentials) throws AbortException {

        Config c = ConfigFiles.getByIdOrNull(build, mavenGlobalSettingsConfigId);
        if (c == null) {
            throw new AbortException("Could not find the Maven global settings.xml config file id:" + mavenGlobalSettingsFile + ". Make sure it exists on Managed Files");
        }
        if (StringUtils.isBlank(c.content)) {
            throw new AbortException("Could not create Maven global settings.xml config file id:" + mavenGlobalSettingsFile + ". Content of the file is empty");
        }

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig;
        if (c instanceof GlobalMavenSettingsConfig) {
            mavenGlobalSettingsConfig = (GlobalMavenSettingsConfig) c;
        } else {
            mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig(c.id, c.name, c.comment, c.content, MavenSettingsConfig.isReplaceAllDefault, null);
        }

        try {
            // JENKINS-43787 handle null
            final List<ServerCredentialMapping> serverCredentialMappings = Objects.firstNonNull(mavenGlobalSettingsConfig.getServerCredentialMappings(), Collections.<ServerCredentialMapping>emptyList());

            final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build, serverCredentialMappings);

            credentials.addAll(resolvedCredentials.values());

            String mavenGlobalSettingsFileContent;
            if (resolvedCredentials.isEmpty()) {
                mavenGlobalSettingsFileContent = mavenGlobalSettingsConfig.content;
                console.println("[withMaven] use Maven global settings.xml '" + mavenGlobalSettingsConfig.id + "' with NO Maven servers credentials provided by Jenkins");

            } else {
                List<String> tempFiles = new ArrayList<String>();
                mavenGlobalSettingsFileContent = CredentialsHelper.fillAuthentication(mavenGlobalSettingsConfig.content, mavenGlobalSettingsConfig.isReplaceAll, resolvedCredentials, tempBinDir, tempFiles);
                console.println("[withMaven] use Maven global settings.xml '" + mavenGlobalSettingsConfig.id + "' with Maven servers credentials provided by Jenkins " +
                        "(replaceAll: " + mavenGlobalSettingsConfig.isReplaceAll + "): " +
                        Joiner.on(", ").skipNulls().join(Iterables.transform(resolvedCredentials.entrySet(), new MavenServerToCredentialsMappingToStringFunction())));

            }


            mavenGlobalSettingsFile.write(mavenGlobalSettingsFileContent, getComputer().getDefaultCharset().name());
            LOGGER.log(Level.FINE, "Created global config file {0}", new Object[]{mavenGlobalSettingsFile});
        } catch (Exception e) {
            throw new IllegalStateException("Exception injecting Maven settings.xml " + mavenGlobalSettingsConfig.id +
                    " during the build: " + build + ": " + e.getMessage(), e);
        }
    }

    /**
     * Takes care of overriding the environment with our defined overrides
     */
    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String, String> overrides;

        private ExpanderImpl(EnvVars overrides) {
            LOGGER.log(Level.FINEST, "ExpanderImpl(overrides: {0})", new Object[]{overrides});
            this.overrides = new HashMap<>();
            for (Entry<String, String> entry : overrides.entrySet()) {
                this.overrides.put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            LOGGER.log(Level.FINEST, "ExpanderImpl.expand - env before expand: {0}", new Object[]{env}); // JENKINS-40484
            env.overrideAll(overrides);
            LOGGER.log(Level.FINEST, "ExpanderImpl.expand - env after expand: {0}", new Object[]{env}); // JENKINS-40484
        }
    }

    /**
     * Callback to cleanup tmp script after finishing the job
     */
    private static class WorkspaceCleanupCallback extends BodyExecutionCallback.TailCall {
        private final FilePath tempBinDir;

        private final List<MavenPublisher> options;

        private final MavenSpyLogProcessor mavenSpyLogProcessor = new MavenSpyLogProcessor();

        public WorkspaceCleanupCallback(@Nonnull FilePath tempBinDir, @Nonnull List<MavenPublisher> options) {
            this.tempBinDir = tempBinDir;
            this.options = options;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            mavenSpyLogProcessor.processMavenSpyLogs(context, tempBinDir, options);

            try {
                tempBinDir.deleteRecursive();
            } catch (IOException | InterruptedException e) {
                BuildListener listener = context.get(BuildListener.class);
                try {
                    if (e instanceof IOException) {
                        Util.displayIOException((IOException) e, listener); // Better IOException display on windows
                    }
                    e.printStackTrace(listener.fatalError("Error deleting temporary files"));
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
        return Jenkins.getActiveInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body != null) {
            body.cancel(cause);
        }
    }

    /**
     * Gets the computer for the current launcher.
     *
     * @return the computer
     * @throws AbortException in case of error.
     */
    @Nonnull
    private Computer getComputer() throws AbortException {
        if (computer != null) {
            return computer;
        }

        String node = null;
        Jenkins j = Jenkins.getActiveInstance();

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
            } catch (IOException | InterruptedException e) {// ignored
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

    /**
     * ToString of the mapping mavenServerId -> Credentials
     */
    private static class MavenServerToCredentialsMappingToStringFunction implements Function<Entry<String, StandardUsernameCredentials>, String> {
        @Override
        public String apply(@Nullable Entry<String, StandardUsernameCredentials> entry) {
            if (entry == null)
                return null;
            String mavenServerId = entry.getKey();
            StandardUsernameCredentials credentials = entry.getValue();
            return "[" +
                    "mavenServerId: '" + mavenServerId + "', " +
                    "jenkinsCredentials: '" + credentials.getId() + "', " +
                    "username: '" + credentials.getUsername() + "', " +
                    "type: '" + ClassUtils.getShortName(credentials.getClass()) +
                    "']";
        }
    }

    private static class CredentialsToPrettyString implements Function<Credentials, String> {
        @Override
        public String apply(@javax.annotation.Nullable Credentials credentials) {
            if (credentials == null)
                return "null";

            String result = ClassUtils.getShortName(credentials.getClass()) + "[";
            if (credentials instanceof IdCredentials) {
                IdCredentials idCredentials = (IdCredentials) credentials;
                result += "id: " + idCredentials.getId() + ",";
            }

            if (credentials instanceof UsernameCredentials) {
                UsernameCredentials usernameCredentials = (UsernameCredentials) credentials;
                result += "username: " + usernameCredentials.getUsername() + "";
            }
            result += "]";
            return result;
        }
    }
}
