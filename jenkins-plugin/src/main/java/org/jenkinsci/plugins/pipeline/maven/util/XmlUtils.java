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
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
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

    public static MavenSpyLogProcessor.MavenArtifact newMavenArtifact(Element artifactElt) {
        MavenSpyLogProcessor.MavenArtifact mavenArtifact = new MavenSpyLogProcessor.MavenArtifact();
        loadMavenArtifact(artifactElt, mavenArtifact);

        return mavenArtifact;
    }

    public static MavenSpyLogProcessor.MavenDependency newMavenDependency(Element dependencyElt) {
        MavenSpyLogProcessor.MavenDependency dependency = new MavenSpyLogProcessor.MavenDependency();
        loadMavenArtifact(dependencyElt, dependency);
        dependency.setScope(dependencyElt.getAttribute("scope"));
        dependency.optional = Boolean.valueOf(dependencyElt.getAttribute("optional"));

        return dependency;
    }

    private static void loadMavenArtifact(Element artifactElt, MavenSpyLogProcessor.MavenArtifact mavenArtifact) {
        mavenArtifact.groupId = artifactElt.getAttribute("groupId");
        mavenArtifact.artifactId = artifactElt.getAttribute("artifactId");
        mavenArtifact.version = artifactElt.getAttribute("version");
        mavenArtifact.baseVersion = artifactElt.getAttribute("baseVersion");
        if (mavenArtifact.baseVersion == null || mavenArtifact.baseVersion.isEmpty()) {
            mavenArtifact.baseVersion = mavenArtifact.version;
        }
        mavenArtifact.snapshot = Boolean.valueOf(artifactElt.getAttribute("snapshot"));
        mavenArtifact.type = artifactElt.getAttribute("type");
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
    <project baseDir="/Users/cyrilleleclerc/git/cyrille-leclerc/my-jar" file="/Users/cyrilleleclerc/git/cyrille-leclerc/my-jar/pom.xml" groupId="com.example" name="my-jar" artifactId="my-jar" version="0.3-SNAPSHOT">
      <build sourceDirectory="/Users/cyrilleleclerc/git/cyrille-leclerc/my-jar/src/main/java" directory="/Users/cyrilleleclerc/git/cyrille-leclerc/my-jar/target"/>
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
        boolean windows = isWindows(workspace);

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

    public static boolean isWindows(@Nonnull FilePath path) {
        String remote = path.getRemote();
        if (remote.length() > 3 && remote.charAt(1) == ':' && remote.charAt(2) == '\\') {
            // windows path such as "C:\path\to\..."
            return true;
        } else if (remote.length() > 3 && remote.charAt(1) == ':' && remote.charAt(2) == '/') {
            // nasty windows path such as "C:/path/to/...". See JENKINS-44088
            return true;
        }
        int indexOfSlash = path.getRemote().indexOf('/');
        int indexOfBackSlash = path.getRemote().indexOf('\\');
        if (indexOfSlash == -1) {
            return true;
        } else if (indexOfBackSlash == -1) {
            return false;
        } else if (indexOfSlash < indexOfBackSlash) {
            return false;
        } else {
            return true;
        }
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
}
