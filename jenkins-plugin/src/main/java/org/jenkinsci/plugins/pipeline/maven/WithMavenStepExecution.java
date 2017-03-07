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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilderFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.security.CredentialsHelper;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.*;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.util.ArgumentListBuilder;
import jenkins.model.*;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.springframework.util.ClassUtils;
import org.w3c.dom.Document;

@SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Contextual fields used only in start(); no onResume needed")
class WithMavenStepExecution extends StepExecution {

    private static final long serialVersionUID = 1L;
    private static final String MAVEN_HOME = "MAVEN_HOME";
    private static final String M2_HOME = "M2_HOME";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";

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
    private final transient Run<?, ?> build;

    private transient Computer computer;
    private transient FilePath tempBinDir;
    private transient BodyExecution body;

    /**
     * Inidicates if running on docker with <tt>docker.image()</tt>
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
        }

        getComputer();

        withContainer = detectWithContainer();

        setupJDK();
        setupMaven();

        ConsoleLogFilter consFilter = BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), new MavenConsoleFilter(getComputer().getDefaultCharset().name()));
        EnvironmentExpander envEx = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(envOverride));

        body = getContext().newBodyInvoker().withContexts(envEx, consFilter).withCallback(new Callback(tempBinDir)).start();

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

    private void setupMaven() throws AbortException, IOException, InterruptedException {
        String mvnExecPath = obtainMavenExec();

        // Temp dir with the wrapper that will be prepended to the path
        tempBinDir = tempDir(ws).child("withMaven" + Util.getDigestOf(UUID.randomUUID().toString()).substring(0, 8));
        tempBinDir.mkdirs();
        // set the path to our script
        envOverride.put("PATH+MAVEN", tempBinDir.getRemote());

        LOGGER.log(Level.FINE, "Using temp dir: {0}", tempBinDir.getRemote());

        if (mvnExecPath == null) {
            throw new AbortException("Couldn\u2019t find any maven executable");
        }

        FilePath mvnExec = new FilePath(ws.getChannel(), mvnExecPath);
        FilePath mavenSpyJarPath = setupMavenSpy();

        // JAVA_TOOL_OPTIONS: https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html
        String javaToolsOptions = env.get("JAVA_TOOL_OPTIONS", "");
        if (StringUtils.isNotEmpty(javaToolsOptions)) {
            javaToolsOptions += " ";
        }
        javaToolsOptions += "-Dmaven.ext.class.path=\"" + mavenSpyJarPath.getRemote() + "\" " +
                "-Dorg.jenkinsci.plugins.pipeline.maven.reportsFolder=\"" + this.tempBinDir.getRemote() + "\" ";
        envOverride.put("JAVA_TOOL_OPTIONS", javaToolsOptions);

        String content = mavenWrapperContent(mvnExec, setupSettingFile(), setupGlobalSettingFile(), setupMavenLocalRepo());

        createWrapperScript(tempBinDir, mvnExec.getName(), content);

        // Maven Ops
        if (StringUtils.isNotEmpty(step.getMavenOpts())) {
            String mavenOpts = envOverride.expand(env.expand(step.getMavenOpts()));

            String mavenOpsOriginal = env.get(MAVEN_OPTS);
            if (mavenOpsOriginal != null) {
                mavenOpts = mavenOpts + " " + mavenOpsOriginal;
            }
            envOverride.put(MAVEN_OPTS, mavenOpts.replaceAll("[\t\r\n]+", " "));
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

    private String obtainMavenExec() throws AbortException, IOException, InterruptedException {
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
                    LOGGER.log(Level.FINE, "Found maven installation {0} with installation home {1}", new Object[]{mavenInstallation.getName(),  mavenInstallation.getHome()});
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
     * Generates the content of the maven wrapper script that works
     *
     * @param mvnExec            maven executable location
     * @param settingsFile       settings file
     * @param globalSettingsFile global settings file
     * @param mavenLocalRepo     maven local repo location
     * @return wrapper script content
     * @throws AbortException when problems creating content
     */
    private String mavenWrapperContent(FilePath mvnExec, String settingsFile, String globalSettingsFile, String mavenLocalRepo) throws AbortException {

        ArgumentListBuilder argList = new ArgumentListBuilder(mvnExec.getRemote());

        boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());

        String lineSep = isUnix ? "\n" : "\r\n";

        if (StringUtils.isNotEmpty(settingsFile)) {
            argList.add("--settings", settingsFile);
        }

        if (StringUtils.isNotEmpty(globalSettingsFile)) {
            argList.add("--global-settings", globalSettingsFile);
        }

        if (StringUtils.isNotEmpty(mavenLocalRepo)) {
            argList.addKeyValuePair(null, "maven.repo.local", mavenLocalRepo, false);
        }

        argList.add("--batch-mode");
        argList.add("--show-version");

        StringBuilder c = new StringBuilder();

        if (isUnix) {
            c.append("#!/bin/sh -e").append(lineSep);
        } else { // Windows
            c.append("@echo off").append(lineSep);
        }

        c.append("echo ----- withMaven Wrapper script -----").append(lineSep);
        c.append(argList.toString()).append(isUnix ? " \"$@\"" : " %*").append(lineSep);

        String content = c.toString();
        LOGGER.log(Level.FINE, "Generated wrapper: {0}", content);
        return content;
    }

    /**
     * Creates the actual wrapper script file and sets the permissions.
     *
     * @param tempBinDir dir to create the script file on
     * @param name       the script file name
     * @param content    contents of the file
     * @return
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    private FilePath createWrapperScript(FilePath tempBinDir, String name, String content) throws IOException, InterruptedException {
        FilePath scriptFile = tempBinDir.child(name);

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
        if (StringUtils.isEmpty(step.getMavenLocalRepo())) {
            return null;
        } else {
            // resolve relative/absolute with workspace as base
            String expandedPath = envOverride.expand(env.expand(step.getMavenLocalRepo()));
            FilePath repoPath = new FilePath(ws, expandedPath);
            repoPath.mkdirs();
            return repoPath.getRemote();
        }
    }

    /**
     * Obtains the selected setting file, and initializes MVN_SETTINGS When the selected file is an absolute path, the
     * file existence is checked on the build agent, if not found, it will be checked and copied from the master. The
     * file will be generated/copied to the workspace temp folder to make sure docker container can access it.
     *
     * @return the maven settings file path on the agent or {@code null} if none defined
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    @Nullable
    private String setupSettingFile() throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("settings.xml");

        // Settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getMavenSettingsConfig())) {
            console.format("[withMaven] use Maven settings provided by the Jenkins Managed Configuration File '%s' %n", step.getMavenSettingsConfig());
            settingsFromConfig(step.getMavenSettingsConfig(), settingsDest);
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        // Settings from the file path
        if (StringUtils.isNotEmpty(step.getMavenSettingsFilePath())) {
            String settingsPath = envOverride.expand(env.expand(step.getMavenSettingsFilePath()));
            FilePath settings;

            if ((settings = new FilePath(ws.getChannel(), settingsPath)).exists()) {
                // settings file residing on the agent
                console.format("[withMaven] use Maven settings provided on the build agent '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven settings file from build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else if ((settings = new FilePath(new File(settingsPath))).exists()) {
                // settings file residing on the master
                try {
                    XmlUtils.checkFileIsaMavenSettingsFile(new File(settingsPath));
                } catch (AbortException e) {
                    LOGGER.log(Level.WARNING,"Invalid Maven Setting File executing " + this.build.getFullDisplayName(), e);
                    throw e;
                }
                console.format("[withMaven] use Maven settings provided on the master '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven settings file from master to build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file '" + settingsPath + "' on the build agent nor the master");
            }
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        return null;
    }

    /**
     * Obtains the selected global setting file, and initializes GLOBAL_MVN_SETTINGS When the selected file is an absolute path, the
     * file existence is checked on the build agent, if not found, it will be checked and copied from the master. The
     * file will be generated/copied to the workspace temp folder to make sure docker container can access it.
     *
     * @return the mavne global settings file path on the agent or {@code null} if none defined
     * @throws InterruptedException when processing remote calls
     * @throws IOException          when reading files
     */
    @Nullable
    private String setupGlobalSettingFile() throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("globalSettings.xml");

        // Global settings from Config File Provider
        if (StringUtils.isNotEmpty(step.getGlobalMavenSettingsConfig())) {
            console.format("[withMaven] use Maven global settings provided by the Jenkins Managed Configuration File '%s' %n", step.getGlobalMavenSettingsConfig());
            globalSettingsFromConfig(step.getGlobalMavenSettingsConfig(), settingsDest);
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }

        // Global settings from the file path
        if (StringUtils.isNotEmpty(step.getGlobalMavenSettingsFilePath())) {
            String settingsPath = envOverride.expand(env.expand(step.getGlobalMavenSettingsFilePath()));
            FilePath settings;
            if ((settings = new FilePath(ws.getChannel(), settingsPath)).exists()) {
                // Global settings file residing on the agent
                console.format("[withMaven] use Maven global settings provided on the build agent '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven global settings file from build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else if ((settings = new FilePath(new File(settingsPath))).exists()) { // File from the master
                // Global settings file residing on the master
                try {
                    XmlUtils.checkFileIsaMavenSettingsFile(new File(settingsPath));
                } catch (AbortException e) {
                    LOGGER.log(Level.WARNING,"Invalid Maven Global Setting File executing " + this.build.getFullDisplayName(), e);
                    throw e;
                }
                console.format("[withMaven] use Maven global settings provided on the master '%s' %n", settingsPath);
                LOGGER.log(Level.FINE, "Copying maven global settings file from master to build agent {0} to {1}", new Object[]{settings, settingsDest});
                settings.copyTo(settingsDest);
            } else {
                throw new AbortException("Could not find file '" + settingsPath + "' on the build agent nor the master");
            }
            envOverride.put("GLOBAL_MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        }
        return null;
    }

    /**
     * Reads the config file from Config File Provider, expands the credentials and stores it in a file on the temp
     * folder to use it with the maven wrapper script
     *
     * @param mavenSettingsConfigId config file id from Config File Provider
     * @param mavenSettingsFile path to write te content to
     * @return the {@link FilePath} to the settings file
     * @throws AbortException in case of error
     */
    private void settingsFromConfig(String mavenSettingsConfigId, FilePath mavenSettingsFile) throws AbortException {

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

            final List<ServerCredentialMapping> serverCredentialMappings = mavenSettingsConfig.getServerCredentialMappings();
            final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build, serverCredentialMappings);

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
     * @param mavenGlobalSettingsFile path to write te content to
     * @return the {@link FilePath} to the settings file
     * @throws AbortException in case of error
     */
    private void globalSettingsFromConfig(String mavenGlobalSettingsConfigId, FilePath mavenGlobalSettingsFile) throws AbortException {

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
            final List<ServerCredentialMapping> serverCredentialMappings = mavenGlobalSettingsConfig.getServerCredentialMappings();
            final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build, serverCredentialMappings);

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
     * Filter to apply {@link MavenConsoleAnnotator} to a pipeline job: pretty print maven output.
     */
    private static class MavenConsoleFilter extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1;
        String charset;

        public MavenConsoleFilter(String charset) {
            this.charset = charset;
        }

        @SuppressWarnings("rawtypes") // inherited
        @Override
        public OutputStream decorateLogger(AbstractBuild _ignore, final OutputStream logger)
                throws IOException, InterruptedException {
            return new MavenConsoleAnnotator(logger, Charset.forName(charset));
        }

        ;
    }

    /**
     * Takes care of overriding the environment with our defined ovirrides
     */
    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String, String> overrides;

        private ExpanderImpl(EnvVars overrides) {
            LOGGER.log(Level.FINE, "Overrides: " + overrides.toString());
            this.overrides = new HashMap<String, String>();
            for (Entry<String, String> entry : overrides.entrySet()) {
                this.overrides.put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            env.overrideAll(overrides);
        }
    }

    /**
     * Callback to cleanup tmp script after finishing the job
     */
    private static class Callback extends BodyExecutionCallback.TailCall {
        private final FilePath tempBinDir;

        private final MavenSpyLogProcessor mavenSpyLogProcessor = new MavenSpyLogProcessor();

        public Callback(FilePath tempBinDir) {
            this.tempBinDir = tempBinDir;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            mavenSpyLogProcessor.processMavenSpyLogs(context, tempBinDir);

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
    private @Nonnull
    Computer getComputer() throws AbortException {
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
        // TODO replace with WorkspaceList.tempDir(ws) after 1.652
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
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
}
