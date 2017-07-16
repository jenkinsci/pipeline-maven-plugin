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

import static org.jenkinsci.plugins.pipeline.maven.eventspy.JenkinsMavenEventSpy.DISABLE_MAVEN_EVENT_SPY_ENVIRONMENT_VARIABLE_NAME;

import org.apache.maven.execution.ExecutionEvent;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class InvokerStartExecutionHandler extends AbstractExecutionHandler {

    public InvokerStartExecutionHandler(final MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    @Nullable
    protected ExecutionEvent.Type getSupportedType() {
        return ExecutionEvent.Type.MojoStarted;
    }

    @Nullable
    @Override
    protected String getSupportedPluginGoal() {
        return "org.apache.maven.plugins:maven-invoker-plugin:run";
    }

    @Nonnull
    @Override
    protected List<String> getConfigurationParametersToReport(final ExecutionEvent executionEvent) {
        return new ArrayList<String>();
    }

    @Override
    public boolean _handle(final ExecutionEvent executionEvent) {
        final boolean result = super._handle(executionEvent);

        System.out.println("[jenkins-maven-event-spy] INFO start of goal " + getSupportedPluginGoal() + ", disabling spy in IT tests.");

        Xpp3Dom env = executionEvent.getMojoExecution().getConfiguration().getChild("environmentVariables");
        if (env == null) {
            env = new Xpp3Dom("environmentVariables");
            executionEvent.getMojoExecution().getConfiguration().addChild(env);
        }
        Xpp3Dom disableSpy = new Xpp3Dom(DISABLE_MAVEN_EVENT_SPY_ENVIRONMENT_VARIABLE_NAME);
        disableSpy.setValue("true");
        env.addChild(disableSpy);

        return result;
    }
}
