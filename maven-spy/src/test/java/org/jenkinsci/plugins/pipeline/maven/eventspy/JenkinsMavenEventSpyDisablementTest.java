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
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.DevNullMavenEventReporter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Test how to disable the Maven Event Spy
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class JenkinsMavenEventSpyDisablementTest {

    @Test
    public void when_disabled_maven_event_spy_must_use_the_dev_null_reporter() throws Exception {
        JenkinsMavenEventSpy spy = new JenkinsMavenEventSpy() {
            @Override
            protected boolean isEventSpyDisabled() {
                return true;
            }
        };
        Assert.assertThat(spy.getReporter(), CoreMatchers.nullValue());
        spy.init(new EventSpy.Context() {
            @Override
            public Map<String, Object> getData() {
                return new HashMap<String, Object>();
            }
        });

        Assert.assertThat(spy.getReporter(), CoreMatchers.instanceOf(DevNullMavenEventReporter.class));
        Assert.assertThat(spy.disabled, CoreMatchers.is(true));
    }
    @Test
    public void when_disabled_maven_event_spy_must_not_call_reporter() throws Exception {
        MavenEventReporter reporterMustNeverBeInvoked = new MavenEventReporter() {
            @Override
            public void print(Object message) {
                throw new IllegalStateException();
            }

            @Override
            public void print(Xpp3Dom element) {
                throw new IllegalStateException();
            }

            @Override
            public void close() {
                throw new IllegalStateException();
            }
        };
        JenkinsMavenEventSpy spy = new JenkinsMavenEventSpy(reporterMustNeverBeInvoked) {
            @Override
            protected boolean isEventSpyDisabled() {
                return true;
            }
        };

        spy.init(new EventSpy.Context() {
            @Override
            public Map<String, Object> getData() {
                return new HashMap<String, Object>();
            }
        });

        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setPom(new File("path/to/pom.xml"));
        request.setGoals(Arrays.asList("clean", "source:jar", "deploy"));

        spy.onEvent(request);
        spy.close();
    }

}
