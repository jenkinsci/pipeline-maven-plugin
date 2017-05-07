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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
        mavenArtifact.groupId = artifactElt.getAttribute("groupId");
        mavenArtifact.artifactId = artifactElt.getAttribute("artifactId");
        mavenArtifact.version = artifactElt.getAttribute("version");
        mavenArtifact.type = artifactElt.getAttribute("type");
        mavenArtifact.classifier = artifactElt.hasAttribute("classifier") ? artifactElt.getAttribute("classifier") : null;
        mavenArtifact.extension = artifactElt.getAttribute("extension");

        return mavenArtifact;
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
            if (expectedTypes.contains(element.getAttribute("type"))){
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

    /**
     * @return same path if not matching workspace
     */
    @Nonnull
    public static String getPathInWorkspace(@Nonnull String absoluteFilePath, @Nonnull FilePath workspace) {
        String workspaceRemote = workspace.getRemote();

        String fileSeparator;
        if (absoluteFilePath.contains("\\")) {
            // '\' character found in the absoluteFilePath, this is windows
            fileSeparator = "\\";
        } else {
            fileSeparator = "/";
        }

        if (!workspaceRemote.endsWith(fileSeparator)) {
            workspaceRemote = workspaceRemote + fileSeparator;
        }
        if (absoluteFilePath.startsWith(workspaceRemote)) {
            return StringUtils.substringAfter(absoluteFilePath, workspaceRemote);
        } else {
            return absoluteFilePath;
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
