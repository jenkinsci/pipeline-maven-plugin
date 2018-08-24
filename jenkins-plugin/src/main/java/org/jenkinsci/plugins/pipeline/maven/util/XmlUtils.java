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

package org.jenkinsci.plugins.pipeline.maven.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class XmlUtils {
    private static final Logger LOGGER = Logger.getLogger(XmlUtils.class.getName());

    public static MavenArtifact newMavenArtifact(Element artifactElt) {
        MavenArtifact mavenArtifact = new MavenArtifact();
        loadMavenArtifact(artifactElt, mavenArtifact);

        return mavenArtifact;
    }

    public static MavenDependency newMavenDependency(Element dependencyElt) {
        MavenDependency dependency = new MavenDependency();
        loadMavenArtifact(dependencyElt, dependency);
        dependency.setScope(dependencyElt.getAttribute("scope"));
        dependency.optional = Boolean.valueOf(dependencyElt.getAttribute("optional"));

        return dependency;
    }

    private static void loadMavenArtifact(Element artifactElt, MavenArtifact mavenArtifact) {
        mavenArtifact.groupId = artifactElt.getAttribute("groupId");
        mavenArtifact.artifactId = artifactElt.getAttribute("artifactId");
        mavenArtifact.version = artifactElt.getAttribute("version");
        mavenArtifact.baseVersion = artifactElt.getAttribute("baseVersion");
        if (mavenArtifact.baseVersion == null || mavenArtifact.baseVersion.isEmpty()) {
            mavenArtifact.baseVersion = mavenArtifact.version;
        }
        mavenArtifact.snapshot = Boolean.valueOf(artifactElt.getAttribute("snapshot"));
        mavenArtifact.type = artifactElt.getAttribute("type");
        if (mavenArtifact.type == null || mavenArtifact.type.isEmpty()) {
            // workaround: sometimes we use "XmlUtils.newMavenArtifact()" on "project" elements, in this case, "packaging" is defined but "type" is not defined
            // we should  probably not use "MavenArtifact"
            mavenArtifact.type = artifactElt.getAttribute("packaging");
        }
        mavenArtifact.classifier = artifactElt.hasAttribute("classifier") ? artifactElt.getAttribute("classifier") : null;
        mavenArtifact.extension = artifactElt.getAttribute("extension");
    }


    /*
  <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.19.1">
 */
    public static MavenSpyLogProcessor.PluginInvocation newPluginInvocation(Element pluginInvocationElt) {
        MavenSpyLogProcessor.PluginInvocation pluginInvocation = new MavenSpyLogProcessor.PluginInvocation();
        pluginInvocation.groupId = pluginInvocationElt.getAttribute("groupId");
        pluginInvocation.artifactId = pluginInvocationElt.getAttribute("artifactId");
        pluginInvocation.version = pluginInvocationElt.getAttribute("version");
        pluginInvocation.goal = pluginInvocationElt.getAttribute("goal");
        pluginInvocation.executionId = pluginInvocationElt.getAttribute("executionId");
        return pluginInvocation;
    }

    @Nonnull
    public static Element getUniqueChildElement(@Nonnull Element element, @Nonnull String childElementName) {
        Element child = getUniqueChildElementOrNull(element, childElementName);
        if (child == null) {
            throw new IllegalStateException("No <" + childElementName + "> element found");
        }
        return child;
    }

    @Nullable
    public static Element getUniqueChildElementOrNull(@Nonnull Element element, String... childElementName) {
        Element result = element;
        for (String childEltName : childElementName) {
            List<Element> childElts = getChildrenElements(result, childEltName);
            if (childElts.size() == 0) {
                return null;
            } else if (childElts.size() > 1) {
                throw new IllegalStateException("More than 1 (" + childElts.size() + ") elements <" + childEltName + "> found in " + toString(element));
            }

            result = childElts.get(0);
        }
        return result;
    }

    @Nonnull
    public static List<Element> getChildrenElements(@Nonnull Element element, @Nonnull String childElementName) {
        NodeList childElts = element.getChildNodes();
        List<Element> result = new ArrayList<>();

        for (int i = 0; i < childElts.getLength(); i++) {
            Node node = childElts.item(i);
            if (node instanceof Element && node.getNodeName().equals(childElementName)) {
                result.add((Element) node);
            }
        }

        return result;
    }

    @Nonnull
    public static String toString(@Nullable Node node) {
        try {
            StringWriter out = new StringWriter();
            Transformer identityTransformer = TransformerFactory.newInstance().newTransformer();
            identityTransformer.transform(new DOMSource(node), new StreamResult(out));
            return out.toString();
        } catch (TransformerException e) {
            LOGGER.log(Level.WARNING, "Exception dumping node " + node, e);
            return e.toString();
        }
    }

    @Nonnull
    public static List<Element> getExecutionEvents(@Nonnull Element mavenSpyLogs, String... expectedType) {

        Set<String> expectedTypes = new HashSet<>(Arrays.asList(expectedType));
        List<Element> result = new ArrayList<>();
        for (Element element : getChildrenElements(mavenSpyLogs, "ExecutionEvent")) {
            if (expectedTypes.contains(element.getAttribute("type"))) {
                result.add(element);
            }
        }
        return result;
    }

    /*
    <RepositoryEvent type="ARTIFACT_DEPLOYED" class="org.eclipse.aether.RepositoryEvent" _time="2018-02-11 16:18:26.505">
        <artifact extension="jar" file="/path/to/my-project-workspace/target/my-jar-0.5-SNAPSHOT.jar" baseVersion="0.5-SNAPSHOT" groupId="com.example" classifier="" artifactId="my-jar" id="com.example:my-jar:jar:0.5-20180211.151825-18" version="0.5-20180211.151825-18" snapshot="true"/>
        <repository layout="default" id="nexus.beescloud.com" url="https://nexus.beescloud.com/content/repositories/snapshots/"/>
    </RepositoryEvent>
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-02-11 16:18:30.971">
        <project baseDir="/path/to/my-project-workspace" file="/path/to/my-project-workspace/pom.xml" groupId="com.example" name="my-jar" artifactId="my-jar" version="0.5-SNAPSHOT">
          <build sourceDirectory="/path/to/my-project-workspace/src/main/java" directory="/path/to/my-project-workspace/target"/>
        </project>
        <no-execution-found/>
        <artifact extension="jar" baseVersion="0.5-SNAPSHOT" groupId="com.example" artifactId="my-jar" id="com.example:my-jar:jar:0.5-SNAPSHOT" type="jar" version="0.5-20180211.151825-18" snapshot="true">
          <file>/path/to/my-project-workspace/target/my-jar-0.5-SNAPSHOT.jar</file>
        </artifact>
        <attachedArtifacts/>
    </ExecutionEvent>
     */
    @Nonnull
    public static List<Element> getArtifactDeployedEvents(@Nonnull Element mavenSpyLogs) {
        List<Element> elements = new ArrayList<>();

        NodeList nodes = mavenSpyLogs.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (StringUtils.equals(element.getNodeName(), "RepositoryEvent")) {
                    Attr type = element.getAttributeNode("type");
                    if (null != type && StringUtils.equals(type.getValue(), "ARTIFACT_DEPLOYED")) {
                        elements.add(element);
                    }
                }
            }
        }
        return elements;
    }

    /*
    <RepositoryEvent type="ARTIFACT_DEPLOYED" class="org.eclipse.aether.RepositoryEvent" _time="2018-02-11 16:18:26.505">
        <artifact extension="jar" file="/path/to/my-project-workspace/target/my-jar-0.5-SNAPSHOT.jar" baseVersion="0.5-SNAPSHOT" groupId="com.example" classifier="" artifactId="my-jar" id="com.example:my-jar:jar:0.5-20180211.151825-18" version="0.5-20180211.151825-18" snapshot="true"/>
        <repository layout="default" id="nexus.beescloud.com" url="https://nexus.beescloud.com/content/repositories/snapshots/"/>
    </RepositoryEvent>
    <ExecutionEvent type="ProjectSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-02-11 16:18:30.971">
        <project baseDir="/path/to/my-project-workspace" file="/path/to/my-project-workspace/pom.xml" groupId="com.example" name="my-jar" artifactId="my-jar" version="0.5-SNAPSHOT">
          <build sourceDirectory="/path/to/my-project-workspace/src/main/java" directory="/path/to/my-project-workspace/target"/>
        </project>
        <no-execution-found/>
        <artifact extension="jar" baseVersion="0.5-SNAPSHOT" groupId="com.example" artifactId="my-jar" id="com.example:my-jar:jar:0.5-SNAPSHOT" type="jar" version="0.5-20180211.151825-18" snapshot="true">
          <file>/path/to/my-project-workspace/target/my-jar-0.5-SNAPSHOT.jar</file>
        </artifact>
        <attachedArtifacts/>
    </ExecutionEvent>
     */

    /**
     *
     * @param artifactDeployedEvents list of "RepositoryEvent" of type "ARTIFACT_DEPLOYED"
     * @param filePath file path of the artifact we search for
     * @return The "RepositoryEvent" of type "ARTIFACT_DEPLOYED" or {@code null} if non found
     */
    @Nullable
    public static Element getArtifactDeployedEvent(@Nonnull List<Element> artifactDeployedEvents, @Nonnull String filePath) {
        for (Element artifactDeployedEvent: artifactDeployedEvents) {
            if (!"RepositoryEvent".equals(artifactDeployedEvent.getNodeName()) || !"ARTIFACT_DEPLOYED".equals(artifactDeployedEvent.getAttribute("type"))) {
                // skip unexpected element
                continue;
            }
            String deployedArtifactFilePath = getUniqueChildElement(artifactDeployedEvent, "artifact").getAttribute("file");
            if (Objects.equals(filePath, deployedArtifactFilePath)) {
                return artifactDeployedEvent;
            }
        }
        return null;
    }

    /*
   <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-02 23:03:17.06">
      <project artifactIdId="supplychain-portal" groupId="com.acmewidgets.supplychain" name="supplychain-portal" version="0.0.7" />
      <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.18.1">
         <reportsDirectory>${project.build.directory}/surefire-reports</reportsDirectory>
      </plugin>
   </ExecutionEvent>
     */
    @Nonnull
    public static List<Element> getExecutionEvents(@Nonnull Element mavenSpyLogs, String pluginGroupId, String pluginArtifactId, String pluginGoal) {
        List<Element> result = new ArrayList<>();
        for (Element executionEventElt : getChildrenElements(mavenSpyLogs, "ExecutionEvent")) {
            Element pluginElt = XmlUtils.getUniqueChildElementOrNull(executionEventElt, "plugin");
            if (pluginElt == null) {

            } else {
                if (pluginElt.getAttribute("groupId").equals(pluginGroupId) &&
                        pluginElt.getAttribute("artifactId").equals(pluginArtifactId) &&
                        pluginElt.getAttribute("goal").equals(pluginGoal)) {
                    result.add(executionEventElt);
                } else {

                }
            }

        }
        return result;
    }

    /*
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-02-02 23:03:17.06">
      <project artifactIdId="supplychain-portal" groupId="com.acmewidgets.supplychain" name="supplychain-portal" version="0.0.7" />
      <plugin executionId="default-test" goal="test" groupId="org.apache.maven.plugins" artifactId="maven-surefire-plugin" version="2.18.1">
         <reportsDirectory>${project.build.directory}/surefire-reports</reportsDirectory>
      </plugin>
    </ExecutionEvent>
     */

    /**
     *
     * @param mavenSpyLogs
     * @param eventType e.g. "MojoSucceeded"
     * @param pluginGroupId e.g. "org.apache.maven.plugins" artifactId=
     * @param pluginArtifactId e.g. "maven-surefire-plugin"
     * @param pluginGoal e.g. "test"
     * @return
     */
    @Nonnull
    public static List<Element> getExecutionEvents(@Nonnull Element mavenSpyLogs, String eventType, String pluginGroupId, String pluginArtifactId, String pluginGoal) {
        List<Element> result = new ArrayList<>();
        for (Element executionEventElt : getChildrenElements(mavenSpyLogs, "ExecutionEvent")) {

            if (executionEventElt.getAttribute("type").equals(eventType)) {
                Element pluginElt = XmlUtils.getUniqueChildElementOrNull(executionEventElt, "plugin");
                if (pluginElt == null) {
                    // ignore unexpected
                } else {
                    if (pluginElt.getAttribute("groupId").equals(pluginGroupId) &&
                            pluginElt.getAttribute("artifactId").equals(pluginArtifactId) &&
                            pluginElt.getAttribute("goal").equals(pluginGoal)) {
                        result.add(executionEventElt);
                    } else {
                        // ignore non matching plugin
                    }
                }
            } else {
                // ignore not supported event type
            }

        }
        return result;
    }

    /*
    <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2017-09-26 23:55:44.188">
    <project baseDir="/path/to/my-project-workspace" file="/path/to/my-project-workspace/pom.xml" groupId="com.example" name="my-jar" artifactId="my-jar" version="0.3-SNAPSHOT">
      <build sourceDirectory="/path/to/my-project-workspace/src/main/java" directory="/path/to/my-project-workspace/target"/>
    </project>
    <plugin executionId="default-jar" goal="jar" lifecyclePhase="package" groupId="org.apache.maven.plugins" artifactId="maven-jar-plugin" version="2.4">
      <finalName>${jar.finalName}</finalName>
      <outputDirectory>${project.build.directory}</outputDirectory>
    </plugin>
  </ExecutionEvent>
     */
    @Nonnull
    public static List<String> getExecutedLifecyclePhases(@Nonnull Element mavenSpyLogs) {
        List<String> lifecyclePhases = new ArrayList<>();
        for (Element mojoSucceededEvent :getExecutionEvents(mavenSpyLogs, "MojoSucceeded")) {
            Element pluginElement = getUniqueChildElement(mojoSucceededEvent, "plugin");
            String lifecyclePhase = pluginElement.getAttribute("lifecyclePhase");
            if (!lifecyclePhases.contains(lifecyclePhase)) {
                lifecyclePhases.add(lifecyclePhase);
            }
        }

        return lifecyclePhases;
    }

    /**
     * Relativize path
     * <p>
     * TODO replace all the workarounds (JENKINS-44088, JENKINS-46084, mac special folders...) by a unique call to
     * {@link File#getCanonicalPath()} on the workspace for the whole "MavenSpyLogProcessor#processMavenSpyLogs" code block.
     * We donb't want to pay an RPC call to {@link File#getCanonicalPath()} each time.
     *
     * @return relativized path
     * @throws IllegalArgumentException if {@code other} is not a {@code Path} that can be relativized
     *                                  against this path
     * @see java.nio.file.Path#relativize(Path)
     */
    @Nonnull
    public static String getPathInWorkspace(@Nonnull final String absoluteFilePath, @Nonnull FilePath workspace) {
        boolean windows = FileUtils.isWindows(workspace);

        final String workspaceRemote = workspace.getRemote();

        String sanitizedAbsoluteFilePath;
        String sanitizedWorkspaceRemote;
        if (windows) {
            // sanitize to workaround JENKINS-44088
            sanitizedWorkspaceRemote = workspaceRemote.replace('/', '\\');
            sanitizedAbsoluteFilePath = absoluteFilePath.replace('/', '\\');
        } else if (workspaceRemote.startsWith("/var/") && absoluteFilePath.startsWith("/private/var/")) {
            // workaround MacOSX special folders path
            // eg String workspace = "/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven";
            // eg String absolutePath = "/private/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven/pom.xml";
            sanitizedWorkspaceRemote = workspaceRemote;
            sanitizedAbsoluteFilePath = absoluteFilePath.substring("/private".length());
        } else {
            sanitizedAbsoluteFilePath = absoluteFilePath;
            sanitizedWorkspaceRemote = workspaceRemote;
        }

        if (StringUtils.startsWithIgnoreCase(sanitizedAbsoluteFilePath, sanitizedWorkspaceRemote)) {
            // OK
        } else if (sanitizedWorkspaceRemote.contains("/workspace/") && sanitizedAbsoluteFilePath.contains("/workspace/")) {
            // workaround JENKINS-46084
            // sanitizedAbsoluteFilePath = '/app/Jenkins/home/workspace/testjob/pom.xml'
            // sanitizedWorkspaceRemote = '/var/lib/jenkins/workspace/testjob'
            sanitizedAbsoluteFilePath = "/workspace/" + StringUtils.substringAfter(sanitizedAbsoluteFilePath, "/workspace/");
            sanitizedWorkspaceRemote = "/workspace/" + StringUtils.substringAfter(sanitizedWorkspaceRemote, "/workspace/");
        } else if (sanitizedWorkspaceRemote.endsWith("/workspace") && sanitizedAbsoluteFilePath.contains("/workspace/")) {
            // workspace = "/var/lib/jenkins/jobs/Test-Pipeline/workspace";
            // absolutePath = "/storage/jenkins/jobs/Test-Pipeline/workspace/pom.xml";
            sanitizedAbsoluteFilePath = "workspace/" + StringUtils.substringAfter(sanitizedAbsoluteFilePath, "/workspace/");
            sanitizedWorkspaceRemote = "workspace/";
        } else {
            throw new IllegalArgumentException("Cannot relativize '" + absoluteFilePath + "' relatively to '" + workspace.getRemote() + "'");
        }

        String relativePath = StringUtils.removeStartIgnoreCase(sanitizedAbsoluteFilePath, sanitizedWorkspaceRemote);
        String fileSeparator = windows ? "\\" : "/";

        if (relativePath.startsWith(fileSeparator)) {
            relativePath = relativePath.substring(fileSeparator.length());
        }
        LOGGER.log(Level.FINEST, "getPathInWorkspace({0}, {1}: {2}", new Object[]{absoluteFilePath, workspaceRemote, relativePath});
        return relativePath;
    }

    /**
     * @deprecated  use {@link FileUtils#isWindows(FilePath)}
     */
    @Deprecated
    public static boolean isWindows(@Nonnull FilePath path) {
        return FileUtils.isWindows(path);
    }

    /**
     * Return the File separator "/" or "\" that is effective on the remote agent.
     *
     * @param filePath
     * @return "/" or "\"
     */
    @Nonnull
    public static String getFileSeparatorOnRemote(@Nonnull FilePath filePath) {
        int indexOfSlash = filePath.getRemote().indexOf('/');
        int indexOfBackSlash = filePath.getRemote().indexOf('\\');
        if (indexOfSlash == -1) {
            return "\\";
        } else if (indexOfBackSlash == -1) {
            return "/";
        } else if (indexOfSlash < indexOfBackSlash) {
            return "/";
        } else {
            return "\\";
        }
    }

    /**
     * @param projectElt
     * @return {@code project/build/@directory"}
     */
    @Nullable
    public static String getProjectBuildDirectory(@Nonnull Element projectElt) {
        Element build = XmlUtils.getUniqueChildElementOrNull(projectElt, "build");
        if (build == null) {
            return null;
        }
        return build.getAttribute("directory");
    }

    /**
     * Concatenate the given {@code elements} using the given {@code delimiter} to concatenate.
     */
    @NonNull
    public static String join(@NonNull Iterable<String> elements, @NonNull String delimiter) {
        StringBuilder result = new StringBuilder();
        Iterator<String> it = elements.iterator();
        while (it.hasNext()) {
            String element = it.next();
            result.append(element);
            if (it.hasNext()) {
                result.append(delimiter);
            }
        }
        return result.toString();
    }


    @Nonnull
    public static List<MavenArtifact> listGeneratedArtifacts(Element mavenSpyLogs, boolean includeAttachedArtifacts) {

        List<Element> artifactDeployedEvents = XmlUtils.getArtifactDeployedEvents(mavenSpyLogs);

        List<MavenArtifact> result = new ArrayList<>();

        for (Element projectSucceededElt : XmlUtils.getExecutionEvents(mavenSpyLogs, "ProjectSucceeded")) {

            Element projectElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "project");
            MavenArtifact projectArtifact = XmlUtils.newMavenArtifact(projectElt);
            MavenArtifact pomArtifact = new MavenArtifact();
            pomArtifact.groupId = projectArtifact.groupId;
            pomArtifact.artifactId = projectArtifact.artifactId;
            pomArtifact.baseVersion = projectArtifact.baseVersion;
            pomArtifact.version = projectArtifact.version;
            pomArtifact.snapshot = projectArtifact.snapshot;
            pomArtifact.type = "pom";
            pomArtifact.extension = "pom";
            pomArtifact.file = projectElt.getAttribute("file");

            result.add(pomArtifact);

            Element artifactElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "artifact");
            MavenArtifact mavenArtifact = XmlUtils.newMavenArtifact(artifactElt);
            if ("pom".equals(mavenArtifact.type)) {
                // No file is generated in a "pom" type project, don't add the pom file itself
                // TODO: evaluate if we really want to skip this file - cyrille le clerc 2018-04-12
            } else {
                Element fileElt = XmlUtils.getUniqueChildElementOrNull(artifactElt, "file");
                if (fileElt == null || fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINE, "listGeneratedArtifacts: Project " + projectArtifact + ":  no associated file found for " +
                                mavenArtifact + " in " + XmlUtils.toString(artifactElt));
                    }
                } else {
                    mavenArtifact.file = StringUtils.trim(fileElt.getTextContent());

                    Element artifactDeployedEvent = XmlUtils.getArtifactDeployedEvent(artifactDeployedEvents, mavenArtifact.file);
                    if(artifactDeployedEvent == null) {
                        // artifact has not been deployed ("mvn deploy")
                    } else {
                        mavenArtifact.repositoryUrl = XmlUtils.getUniqueChildElement(artifactDeployedEvent, "repository").getAttribute("url");
                    }
                }
                result.add(mavenArtifact);
            }

            if (includeAttachedArtifacts) {
                Element attachedArtifactsParentElt = XmlUtils.getUniqueChildElement(projectSucceededElt, "attachedArtifacts");
                List<Element> attachedArtifactsElts = XmlUtils.getChildrenElements(attachedArtifactsParentElt, "artifact");
                for (Element attachedArtifactElt : attachedArtifactsElts) {
                    MavenArtifact attachedMavenArtifact = XmlUtils.newMavenArtifact(attachedArtifactElt);

                    Element fileElt = XmlUtils.getUniqueChildElementOrNull(attachedArtifactElt, "file");
                    if (fileElt == null || fileElt.getTextContent() == null || fileElt.getTextContent().isEmpty()) {
                        if (LOGGER.isLoggable(Level.FINER)) {
                            LOGGER.log(Level.FINER, "Project " + projectArtifact + ", no associated file found for attached artifact " +
                                    attachedMavenArtifact + " in " + XmlUtils.toString(attachedArtifactElt));
                        }
                    } else {
                        attachedMavenArtifact.file = StringUtils.trim(fileElt.getTextContent());

                        Element attachedArtifactDeployedEvent = XmlUtils.getArtifactDeployedEvent(artifactDeployedEvents, attachedMavenArtifact.file);
                        if(attachedArtifactDeployedEvent == null) {
                            // artifact has not been deployed ("mvn deploy")
                        } else {
                            attachedMavenArtifact.repositoryUrl = XmlUtils.getUniqueChildElement(attachedArtifactDeployedEvent, "repository").getAttribute("url");
                        }

                    }
                    result.add(attachedMavenArtifact);
                }
            }
        }

        return result;
    }
}
