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
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.Charset;
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

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.security.CredentialsHelper;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.inject.Inject;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

public class WithMavenStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;
    private static final String MAVEN_HOME = "MAVEN_HOME";
    private static final String M2_HOME = "M2_HOME";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";

    private static final Logger LOGGER = Logger.getLogger(WithMavenStepExecution.class.getName());

    @Inject(optional = true)
    private transient WithMavenStep step;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient FilePath ws;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient EnvVars env;
    private transient EnvVars envOverride;
    @StepContextParameter
    private transient Run<?, ?> build;

    private transient Computer computer;
    private transient FilePath tempBinDir;
    private transient BodyExecution body;

    /**
     * Inidicates if running on docker with <tt>docker.image()</tt>
     */
    private boolean withContainer;

    private transient PrintStream console;

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
        }

        getComputer();

        withContainer = detectWithContainer();

        setupJDK();
        setupMaven();

        MavenConsoleFilter consFilter = new MavenConsoleFilter(getComputer().getDefaultCharset().name());
        EnvironmentExpander envEx = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(envOverride));

        body = getContext().newBodyInvoker().withContexts(envEx, consFilter).withCallback(new Callback(tempBinDir)).start();

        return false;
    }

    /**
     * Detects if this step is running inside <tt>docker.image()</tt>
     * 
     * This has the following implications:
     * <li>Tool intallers do no work, as they install in the host, see:
     * https://issues.jenkins-ci.org/browse/JENKINS-36159
     * <li>Environment variables do not apply because they belong either to the master or the agent, but not to the
     * container running the <tt>sh</tt> command for maven This is due to the fact that <tt>docker.image()</tt> all it
     * does is decorate the launcher and excute the command with a <tt>docker run</tt> which means that the inherited
     * environment from the OS will be totally different eg: MAVEN_HOME, JAVA_HOME, PATH, etc.
     * 
     * @see <a href=
     * "https://github.com/jenkinsci/docker-workflow-plugin/blob/master/src/main/java/org/jenkinsci/plugins/docker/workflow/WithContainerStep.java#L213">
     * WithContainerStep</a>
     * @return true if running inside docker container with <tt>docker.image()</tt>
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
        JDK jdk;
        if (!StringUtils.isEmpty(step.getJdk())) {
            if (!withContainer) {
                jdk = Jenkins.getActiveInstance().getJDK(step.getJdk());
                if (jdk == null) {
                    throw new AbortException("Could not find the JDK installation: " + step.getJdk() + ". Make sure it is configured on the Global Tool Configuration page");
                }
                Node node = getComputer().getNode();
                if (node == null){
                    throw new AbortException("Could not obtain the Node for the computer: " + getComputer().getName());    
                }
                jdk = jdk.forNode(node, listener).forEnvironment(env);
                jdk.buildEnvVars(envOverride);
            } else { // see #detectWithContainer()
                LOGGER.log(Level.FINE, "Ignoring JDK installation parameter: {0}", step.getJdk());
                console.println(
                        "WARNING: Step running within docker.image() tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. You have specified a JDK installation, which will be ignored.");
            }
        }
    }

    private void setupMaven() throws AbortException, IOException, InterruptedException {
        String mvnExecPath;

        mvnExecPath = obtainMavenExec();

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
        String content = mavenWrapperContent(mvnExec, setupSettingFile(), setupMavenLocalRepo());

        createWrapperScript(tempBinDir, mvnExec.getName(), content);

        // Maven Ops
        if (!StringUtils.isEmpty(step.getMavenOpts())) {
            String mavenOpts = envOverride.expand(env.expand(step.getMavenOpts()));

            String mavenOpsOriginal = env.get(MAVEN_OPTS);
            if (mavenOpsOriginal != null) {
                mavenOpts = mavenOpts + " " + mavenOpsOriginal;
            }
            envOverride.put(MAVEN_OPTS, mavenOpts.replaceAll("[\t\r\n]+", " "));
        }

        // just as a precaution
        // see http://maven.apache.org/continuum/faqs.html#how-does-continuum-detect-a-successful-build
        envOverride.put("MAVEN_TERMINATE_CMD", "on");
    }

    private String obtainMavenExec() throws AbortException, IOException, InterruptedException {
        MavenInstallation mi = null;
        String mvnExecPath = null;

        LOGGER.fine("Setting up maven");

        String mavenName = step.getMaven();

        if (!StringUtils.isEmpty(mavenName)) {
            if (!withContainer) {
                LOGGER.log(Level.FINE, "Maven Installation parameter: {0}", mavenName);
                for (MavenInstallation i : getMavenInstallations()) {
                    if (mavenName != null && mavenName.equals(i.getName())) {
                        mi = i;
                        LOGGER.log(Level.FINE, "Found maven installation on {0}", mi.getHome());
                        break;
                    }
                }
                if (mi == null) {
                    throw new AbortException("Could not find '" + mavenName + "' maven installation.");
                }
            } else {
                console.println(
                        "WARNING: Step running within docker.image() tool installations are not available see https://issues.jenkins-ci.org/browse/JENKINS-36159. You have specified a Maven installation, which will be ignored.");
                LOGGER.log(Level.FINE, "Ignoring Maven Installation parameter: {0}", mavenName);
            }
        }

        if (mi != null) {
            console.println("Using Maven Installation " + mi.getName());
            
            Node node = getComputer().getNode();
            if (node == null){
                throw new AbortException("Could not obtain the Node for the computer: " + getComputer().getName());    
            }
            mi = mi.forNode(node, listener).forEnvironment(env);
            mi.buildEnvVars(envOverride);
            mvnExecPath = mi.getExecutable(launcher);
        } else {
            // in case there are no installations available we fallback to the OS maven installation
            // first we try MAVEN_HOME and M2_HOME
            LOGGER.fine("Searching for Maven on MAVEN_HOME and M2_HOME...");
            if (!withContainer) { // if not on docker we can use the computer environment
                LOGGER.fine("Using computer environment...");
                EnvVars agentEnv = getComputer().getEnvironment();
                LOGGER.log(Level.FINE, "Agent env: {0}", agentEnv);
                String mavenHome = agentEnv.get(MAVEN_HOME);
                if (mavenHome == null) {
                    mavenHome = agentEnv.get(M2_HOME);
                }
                if (mavenHome != null) {
                    LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                    // Resort to maven installation to get the executable and build environment
                    mi = new MavenInstallation("Mave Auto-discovered", mavenHome, null);
                    mi.buildEnvVars(envOverride);
                    mvnExecPath = mi.getExecutable(launcher);
                }
            } else { // in case of docker.image we need to execute a command through the decorated launcher and get the
                     // output.
                LOGGER.fine("Calling printenv on docker container...");
                String mavenHome = readFromProcess("printenv", MAVEN_HOME);
                if (mavenHome == null) {
                    mavenHome = readFromProcess("printenv", M2_HOME);
                }

                if (mavenHome != null) {
                    LOGGER.log(Level.FINE, "Found maven installation on {0}", mavenHome);
                    mvnExecPath = mavenHome + "/bin/mvn"; // we can safely assume *nix
                }
            }
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
        }

        if (mvnExecPath == null) {
            throw new AbortException("Could not find maven executable, please set up a Maven Installation or configure MAVEN_HOME environment variable");
        }

        LOGGER.log(Level.FINE, "Found exec for maven on: {0}", mvnExecPath);
        console.printf("Using maven exec: %s%n", mvnExecPath);
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
     * @param mvnExec maven executable location
     * @param settingsFile settings file
     * @param mavenLocalRepo maven local repo location
     * @return wrapper script content
     * @throws AbortException when problems creating content
     */
    private String mavenWrapperContent(FilePath mvnExec, String settingsFile, String mavenLocalRepo) throws AbortException {

        ArgumentListBuilder argList = new ArgumentListBuilder(mvnExec.getRemote());

        boolean isUnix = Boolean.TRUE.equals(getComputer().isUnix());

        String lineSep = isUnix ? "\n" : "\r\n";

        if (!StringUtils.isEmpty(settingsFile)) {
            argList.add("--settings", settingsFile);
        }

        if (!StringUtils.isEmpty(mavenLocalRepo)) {
            argList.addKeyValuePair(null, "maven.repo.local", mavenLocalRepo, false);
        }

        argList.add("--batch-mode");
        argList.add("--show-version");

        StringBuilder c = new StringBuilder();

        if (isUnix) {
            c.append("#!/bin/sh -e").append(lineSep);
        } else {
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
     * @param name the script file name
     * @param content contents of the file
     * @return
     * @throws InterruptedException when processing remote calls
     * @throws IOException when reading files
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
     * @return path on the build agent to the repo
     * @throws InterruptedException when processing remote calls
     * @throws IOException when reading files
     */
    private String setupMavenLocalRepo() throws IOException, InterruptedException {
        if (!StringUtils.isEmpty(step.getMavenLocalRepo())) {
            // resolve relative/absolute with workspace as base
            String expandedPath = envOverride.expand(env.expand(step.getMavenLocalRepo()));
            FilePath repoPath = new FilePath(ws, expandedPath);
            repoPath.mkdirs();
            return repoPath.getRemote();
        }
        return null;
    }

    /**
     * Obtains the selected setting file, and initializes MVN_SETTINGS When the selected file is an absolute path, the
     * file existence is checked on the build agent, if not found, it will be checked and copied from the master. The
     * file will be generated/copied to the workspace temp folder to make sure docker container can access it.
     * 
     * @return the settings file path on the agent
     * @throws InterruptedException when processing remote calls
     * @throws IOException when reading files
     */
    private String setupSettingFile() throws IOException, InterruptedException {
        final FilePath settingsDest = tempBinDir.child("settings.xml");

        // Settings from Config File Provider
        if (!StringUtils.isEmpty(step.getMavenSettingsConfig())) {
            settingsFromConfig(step.getMavenSettingsConfig(), settingsDest);
            envOverride.put("MVN_SETTINGS", settingsDest.getRemote());
            return settingsDest.getRemote();
        } else if (!StringUtils.isEmpty(step.getMavenSettingsFilePath())) {
            String settingsPath = envOverride.expand(env.expand(step.getMavenSettingsFilePath()));
            FilePath settings;
            console.println("Setting up settings file " + settingsPath);
            // file from agent
            if ((settings = new FilePath(ws.getChannel(), settingsPath)).exists()) {
                console.format("Using settings from: %s on build agent%n", settingsPath);
                LOGGER.log(Level.FINE, "Copying file from build agent {0} to {1}", new Object[] { settings, settingsDest });
                settings.copyTo(settingsDest);
            } else if ((settings = new FilePath(new File(settingsPath))).exists()) { // File from the master
                console.format("Using settings from: %s on master%n", settingsPath);
                LOGGER.log(Level.FINE, "Copying file from master to build agent {0} to {1}", new Object[] { settings, settingsDest });
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
     * Reads the config file from Config File Provider, expands the credentials and stores it in a file on the temp
     * folder to use it with the maven wrapper script
     * 
     * @param settingsConfigId config file id from Config File Provider
     * @param settingsFile path to write te content to
     * @return the {@link FilePath} to the settings file
     * @throws AbortException in case of error
     */
    private void settingsFromConfig(String settingsConfigId, FilePath settingsFile) throws AbortException {
        Config c = Config.getByIdOrNull(settingsConfigId);
        if (c != null) {
            MavenSettingsConfig config;
            if (c instanceof MavenSettingsConfig) {
                config = (MavenSettingsConfig) c;
            } else {
                config = new MavenSettingsConfig(c.id, c.name, c.comment, c.content, MavenSettingsConfig.isReplaceAllDefault, null);
            }

            final Boolean isReplaceAll = config.getIsReplaceAll();
            console.println("Using settings config with name " + config.name);
            console.println("Replacing all maven server entries not found in credentials list is " + isReplaceAll);
            if (StringUtils.isNotBlank(config.content)) {
                try {
                    String fileContent = config.content;

                    final List<ServerCredentialMapping> serverCredentialMappings = config.getServerCredentialMappings();
                    final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build, serverCredentialMappings);

                    if (!resolvedCredentials.isEmpty()) {
                        List<String> tempFiles = new ArrayList<String>();
                        fileContent = CredentialsHelper.fillAuthentication(fileContent, isReplaceAll, resolvedCredentials, tempBinDir, tempFiles);
                    }

                    settingsFile.write(fileContent, getComputer().getDefaultCharset().name());
                    LOGGER.log(Level.FINE, "Created config file {0}", new Object[] { settingsFile });
                } catch (Exception e) {
                    throw new IllegalStateException("the settings.xml could not be supplied for the current build: " + e.getMessage(), e);
                }
            } else {
                throw new AbortException("Could not create Maven settings.xml config file id:" + settingsConfigId + ". Content of the file is empty");
            }
        } else {
            throw new AbortException("Could not find the Maven settings.xml config file id:" + settingsConfigId + ". Make sure it exists on Managed Files");
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
        };
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
        FilePath tempBinDir;

        public Callback(FilePath tempBinDir) {
            this.tempBinDir = tempBinDir;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            try {
                tempBinDir.deleteRecursive();
            } catch (IOException | InterruptedException e) {
                try {
                    TaskListener listener = context.get(TaskListener.class);
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
    private @Nonnull Computer getComputer() throws AbortException {
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

}
