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

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.CatchAllExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DefaultSettingsBuildingRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DependencyResolutionRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DependencyResolutionResultHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.JarJarExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenEventHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenExecutionRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenExecutionResultHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ProjectStartedExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ProjectSucceededExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.SessionEndedHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.SurefireTestExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.FileMavenEventReporter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Named
@Singleton
public class JenkinsMavenEventSpy extends AbstractEventSpy {

    private MavenEventReporter reporter;

    boolean disabled = false;

    Set<Class> blackList = new HashSet();
    Set<String> ignoredList = new HashSet(Arrays.asList(
            "org.eclipse.aether.RepositoryEvent",
            "org.apache.maven.settings.building.DefaultSettingsBuildingResult"/*,
            "org.apache.maven.execution.DefaultMavenExecutionResult"*/));

    List<MavenEventHandler> handlers = new ArrayList();

    public JenkinsMavenEventSpy() throws IOException {
        this(new FileMavenEventReporter());
    }

    public JenkinsMavenEventSpy(MavenEventReporter reporter) throws IOException {
        handlers.add(new ProjectSucceededExecutionHandler(reporter));
        handlers.add(new ProjectStartedExecutionHandler(reporter));
        handlers.add(new SurefireTestExecutionHandler(reporter));
        handlers.add(new JarJarExecutionHandler(reporter));
        handlers.add(new DefaultSettingsBuildingRequestHandler(reporter));
        handlers.add(new MavenExecutionRequestHandler(reporter));
        handlers.add(new DependencyResolutionRequestHandler(reporter));
        handlers.add(new DependencyResolutionResultHandler(reporter));
        handlers.add(new MavenExecutionResultHandler(reporter));
        handlers.add(new SessionEndedHandler(reporter));

        handlers.add(new CatchAllExecutionHandler(reporter));
        this.reporter = reporter;
    }


    @Override
    public void init(EventSpy.Context context) throws Exception {
        Xpp3Dom element = new Xpp3Dom("context");
        for (Map.Entry<String, Object> entry : context.getData().entrySet()) {
            Xpp3Dom entryElt = new Xpp3Dom(entry.getKey());
            element.addChild(entryElt);
            entryElt.setValue(entryElt.getValue());
        }

        reporter.print(element);
        reporter.print("new File(.): " + new File(".").getAbsolutePath());
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (disabled)
            return;

        try {
            if (blackList.contains(event.getClass())) {
                return;
            } else if (ignoredList.contains(event.getClass().getName())) {
                return;
            }

            for (MavenEventHandler handler : handlers) {
                boolean handled = handler.handle(event);
                if (handled) {
                    break;
                }
            }

        } catch (Throwable t) {
            blackList.add(event.getClass());
            System.err.println("Exception processing " + event);
            reporter.print(getClass().getName() + ": Exception processing " + event);
            t.printStackTrace();
        }
    }


    @Override
    public void close() {
        reporter.print("close: ignored:" + ignoredList + ", blackListed: " + blackList);
        reporter.close();
    }

    public MavenEventReporter getReporter() {
        return reporter;
    }

    public void setReporter(MavenEventReporter reporter) {
        this.reporter = reporter;
    }

    public List<MavenEventHandler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<MavenEventHandler> handlers) {
        this.handlers = handlers;
    }

    public void setHandlers(MavenEventHandler... handlers) {
        this.handlers = Arrays.asList(handlers);
    }
}
