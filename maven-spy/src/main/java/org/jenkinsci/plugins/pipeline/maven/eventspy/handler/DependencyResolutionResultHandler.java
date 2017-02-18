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
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyResolutionResultHandler extends AbstractMavenEventHandler<DependencyResolutionResult> {
    public DependencyResolutionResultHandler(MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected boolean _handle(DependencyResolutionResult result) {

        Xpp3Dom root = new Xpp3Dom("DependencyResolutionResult");
        root.setAttribute("class", result.getClass().getName());

        /*
        Xpp3Dom dependenciesElt = new Xpp3Dom("resolvedDependencies");
        root.addChild(dependenciesElt);
        // don't cast to Dependency to not have a classloading issue at runtime
        for (Object dependency: result.getResolvedDependencies()) {
            dependenciesElt.addChild(newElement("dependency", dependency.toString()));
        }*/

        reporter.print(root);
        return true;
    }
}
