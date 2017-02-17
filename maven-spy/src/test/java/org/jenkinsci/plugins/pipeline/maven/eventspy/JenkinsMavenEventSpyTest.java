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

package org.jenkinsci.plugins.pipeline.maven.eventspy;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.OutputStreamEventReporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JenkinsMavenEventSpyTest {

    JenkinsMavenEventSpy spy;
    MavenEventReporter reporter;
    StringWriter writer = new StringWriter();
    MavenProject project;

    @Before
    public void before() throws Exception {
        reporter = new OutputStreamEventReporter(writer);
        spy = new JenkinsMavenEventSpy(reporter) {
            @Override
            protected boolean isEventSpyDisabled() {
                return false;
            }
        };
        spy.init(new EventSpy.Context() {
            @Override
            public Map<String, Object> getData() {
                return new HashMap();
            }
        });

        MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/eventspy/pom.xml");

        Assert.assertThat(in, CoreMatchers.notNullValue());
        Model model = mavenXpp3Reader.read(in);
        project = new MavenProject(model);
        project.setGroupId(model.getGroupId());
        project.setArtifactId(model.getArtifactId());
        project.setVersion(model.getVersion());
        project.setName(model.getName());
    }

    @After
    public void after() throws Exception {
        spy.close();
    }

    @Test
    public void testMavenExecutionRequest() throws Exception {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setPom(new File("path/to/pom.xml"));
        request.setGoals(Arrays.asList("clean", "source:jar", "deploy"));

        spy.onEvent(request);

        String actual = writer.toString();
        System.out.println(actual);
        Assert.assertThat(actual, CoreMatchers.containsString("MavenExecutionRequest"));
    }

    @Test
    public void testExecutionProjectStarted() throws Exception {
        ExecutionEvent executionEvent = new  ExecutionEvent(){
            @Override
            public Type getType() {
                return Type.ProjectStarted;
            }

            @Override
            public MavenSession getSession() {
                return null;
            }

            @Override
            public MavenProject getProject() {
                return project;
            }

            @Override
            public MojoExecution getMojoExecution() {
                return null;
            }

            @Override
            public Exception getException() {
                return null;
            }
        };

        spy.onEvent(executionEvent);

        String actual = writer.toString();
        System.out.println(actual);
        Assert.assertThat(actual, CoreMatchers.containsString("ProjectStarted"));
        Assert.assertThat(actual, CoreMatchers.containsString("petclinic"));
    }
}
