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
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ArtifactDeployedEventHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.CatchAllExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DefaultSettingsBuildingRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DependencyResolutionRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DependencyResolutionResultHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.DeployDeployExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.FailsafeTestExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.InvokerRunExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.InvokerStartExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.JarJarExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenEventHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenExecutionRequestHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.MavenExecutionResultHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ProjectFailedExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ProjectStartedExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.ProjectSucceededExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.SessionEndedHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.handler.SurefireTestExecutionHandler;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.DevNullMavenEventReporter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.FileMavenEventReporter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Maven {@link EventSpy} to capture build details consumed by the Jenkins Pipeline Maven Plugin
 * and the {@code withMaven(){...}} pipeline step.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Named
@Singleton
public class JenkinsMavenEventSpy extends AbstractEventSpy {

    public final static String DISABLE_MAVEN_EVENT_SPY_PROPERTY_NAME =  JenkinsMavenEventSpy.class.getName() + ".disabled";

    public final static String DISABLE_MAVEN_EVENT_SPY_ENVIRONMENT_VARIABLE_NAME =  "JENKINS_MAVEN_AGENT_DISABLED";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private MavenEventReporter reporter;

    /*
     * visible for testing
     */
    protected final boolean disabled;

    private Set<Class> blackList = new HashSet();
    private Set<String> ignoredList = new HashSet(Arrays.asList(
            /*"org.eclipse.aether.RepositoryEvent",*/
            "org.apache.maven.settings.building.DefaultSettingsBuildingResult"/*,
            "org.apache.maven.execution.DefaultMavenExecutionResult"*/));

    private List<MavenEventHandler> handlers = new ArrayList();

    public JenkinsMavenEventSpy() throws IOException {
        this.disabled = isEventSpyDisabled();
        if (disabled) {
            logger.info("[jenkins-event-spy] Jenkins Maven Event Spy is disabled");
        }
    }

    public JenkinsMavenEventSpy(MavenEventReporter reporter) throws IOException {
        this();
        this.reporter = reporter;
    }

    @Override
    public void init(EventSpy.Context context) throws Exception {
        if (disabled) {
            this.reporter = new DevNullMavenEventReporter();
            return;
        }

        if (reporter == null) {
            this.reporter = new FileMavenEventReporter();
        }
        // Initialize handlers
        handlers.add(new ProjectSucceededExecutionHandler(reporter));
        handlers.add(new ProjectFailedExecutionHandler(reporter));
        handlers.add(new ProjectStartedExecutionHandler(reporter));
        handlers.add(new FailsafeTestExecutionHandler(reporter));
        handlers.add(new SurefireTestExecutionHandler(reporter));
        handlers.add(new JarJarExecutionHandler(reporter));
        handlers.add(new InvokerRunExecutionHandler(reporter));
        handlers.add(new InvokerStartExecutionHandler(reporter));
        handlers.add(new DefaultSettingsBuildingRequestHandler(reporter));
        handlers.add(new MavenExecutionRequestHandler(reporter));
        handlers.add(new DependencyResolutionRequestHandler(reporter));
        handlers.add(new DependencyResolutionResultHandler(reporter));
        handlers.add(new MavenExecutionResultHandler(reporter));
        handlers.add(new SessionEndedHandler(reporter));
        handlers.add(new DeployDeployExecutionHandler(reporter));
        handlers.add(new ArtifactDeployedEventHandler(reporter));

        handlers.add(new CatchAllExecutionHandler(reporter));

        // Print context
        Xpp3Dom element = new Xpp3Dom("context");
        for (Map.Entry<String, Object> entry : context.getData().entrySet()) {
            Xpp3Dom entryElt = new Xpp3Dom(entry.getKey());
            element.addChild(entryElt);
            entryElt.setValue(entryElt.getValue());
        }

        reporter.print(element);
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
            logger.warn("[jenkins-event-spy] Exception processing " + event, t);
            reporter.print(getClass().getName() + ": Exception processing " + event);
        }
    }


    @Override
    public void close() {
        if (disabled) {
            return;
        }
        reporter.print("close: ignored:" + ignoredList + ", blackListed: " + blackList);
        reporter.close();
    }

    /**
     * Visible for testing
     */
    protected boolean isEventSpyDisabled(){
        return "true".equalsIgnoreCase(System.getProperty(DISABLE_MAVEN_EVENT_SPY_PROPERTY_NAME)) ||
                "true".equalsIgnoreCase(System.getenv(DISABLE_MAVEN_EVENT_SPY_ENVIRONMENT_VARIABLE_NAME));
    }

    public MavenEventReporter getReporter() {
        return reporter;
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
