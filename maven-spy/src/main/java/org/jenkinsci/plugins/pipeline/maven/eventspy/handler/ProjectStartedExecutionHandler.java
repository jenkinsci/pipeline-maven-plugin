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

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ProjectStartedExecutionHandler extends AbstractExecutionHandler {

    public ProjectStartedExecutionHandler(MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected ExecutionEvent.Type getSupportedType() {
        return ExecutionEvent.Type.ProjectStarted;
    }

    @Override
    protected List<String> getConfigurationParametersToReport(ExecutionEvent executionEvent) {
        return Collections.emptyList();
    }

    @Override
    public boolean _handle(@Nonnull ExecutionEvent executionEvent) {
        return super._handle(executionEvent);
    }

    @Override
    protected void addDetails(@Nonnull ExecutionEvent executionEvent, @Nonnull Xpp3Dom root) {
        super.addDetails(executionEvent, root);
        MavenProject parentProject = executionEvent.getProject().getParent();
        if (parentProject == null) {
            // nothing to do
        } else {
            Xpp3Dom parentProjectElt = new Xpp3Dom("parentProject");
            root.addChild(parentProjectElt);
            parentProjectElt.setAttribute("name", parentProject.getName());
            parentProjectElt.setAttribute("groupId", parentProject.getGroupId());

            parentProjectElt.setAttribute("artifactId", parentProject.getArtifactId());
            parentProjectElt.setAttribute("version", parentProject.getVersion());
        }
    }
}
