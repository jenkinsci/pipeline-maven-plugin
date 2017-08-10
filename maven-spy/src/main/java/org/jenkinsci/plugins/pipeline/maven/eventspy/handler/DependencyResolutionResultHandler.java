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

package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import org.apache.maven.project.DependencyResolutionResult;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyResolutionResultHandler extends AbstractMavenEventHandler<DependencyResolutionResult> {

    /**
     * Scope of the MAven dependencies that we dump<p/>
     * <p>
     * Standard Maven scopes: compile, provided, runtime, test, system and import
     *
     * @See <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">Maven - Dependency Scopes</a>
     * @see Dependency#getScope()
     */
    private Set<String> includedScopes = new HashSet<String>(Arrays.asList("compile", "provided", "test"));

    private boolean includeSnapshots = true;

    private boolean includeReleases = true;

    public DependencyResolutionResultHandler(MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected boolean _handle(DependencyResolutionResult result) {

        Xpp3Dom root = new Xpp3Dom("DependencyResolutionResult");
        root.setAttribute("class", result.getClass().getName());

        Xpp3Dom dependenciesElt = new Xpp3Dom("resolvedDependencies");
        root.addChild(dependenciesElt);

        for (Dependency dependency : result.getResolvedDependencies()) {
            Artifact artifact = dependency.getArtifact();

            if ( !includedScopes.contains(dependency.getScope())) {
                continue;
            }
            if (!includeSnapshots && artifact.isSnapshot()) {
                continue;
            }
            if(!includeReleases && !artifact.isSnapshot()) {
                continue;
            }

            Xpp3Dom dependencyElt = new Xpp3Dom("dependency");

            dependencyElt.addChild(newElement("file", artifact.getFile().getAbsolutePath()));

            dependencyElt.setAttribute("name", artifact.getFile().getName());

            dependencyElt.setAttribute("groupId", artifact.getGroupId());
            dependencyElt.setAttribute("artifactId", artifact.getArtifactId());
            dependencyElt.setAttribute("version", artifact.getVersion());
            dependencyElt.setAttribute("baseVersion", artifact.getBaseVersion());
            if (artifact.getClassifier() != null) {
                dependencyElt.setAttribute("classifier", artifact.getClassifier());
            }
            dependencyElt.setAttribute("type", artifact.getExtension());
            dependencyElt.setAttribute("id", artifact.getArtifactId());
            dependencyElt.setAttribute("extension", artifact.getExtension());
            dependencyElt.setAttribute("scope", dependency.getScope());
            dependencyElt.setAttribute("optional", Boolean.toString(dependency.isOptional()));
            dependencyElt.setAttribute("snapshot", Boolean.toString(artifact.isSnapshot()));

            dependenciesElt.addChild(dependencyElt);
        }

        reporter.print(root);
        return true;
    }
}
