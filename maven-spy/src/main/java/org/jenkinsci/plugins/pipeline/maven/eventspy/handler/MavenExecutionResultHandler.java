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

import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenExecutionResultHandler extends AbstractMavenEventHandler<MavenExecutionResult> {
    public MavenExecutionResultHandler(MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected boolean _handle(MavenExecutionResult result) {
        Xpp3Dom root = new Xpp3Dom("MavenExecutionResult");
        root.setAttribute("class", result.getClass().getName());

        for (MavenProject project : result.getTopologicallySortedProjects()) {
            BuildSummary summary = result.getBuildSummary(project);
            if (summary == null) {
                Xpp3Dom comment = new Xpp3Dom("comment");
                comment.setValue("No build summary found for maven project: " + project);
                root.addChild(comment);
            } else {
                Xpp3Dom buildSummary = newElement("buildSummary", project);
                root.addChild(buildSummary);
                buildSummary.setAttribute("class", summary.getClass().getName());
                buildSummary.setAttribute("time", Long.toString(summary.getTime()));
            }
        }
        for(Throwable throwable: result.getExceptions()) {
            root.addChild(newElement("exception", throwable));
        }
        reporter.print(root);
        return true;
    }
}
