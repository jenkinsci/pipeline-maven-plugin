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
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class AbstractExecutionHandler extends AbstractMavenEventHandler<ExecutionEvent> {
    protected AbstractExecutionHandler(@Nonnull MavenEventReporter reporter) {
        super(reporter);
    }

    public boolean handle(@Nonnull Object event) {
        if (!(event instanceof ExecutionEvent)) {
            return false;
        }
        ExecutionEvent executionEvent = (ExecutionEvent) event;
        ExecutionEvent.Type supportedType = getSupportedType();

        if (supportedType != null && !(supportedType.equals(executionEvent.getType()))) {
            return false;
        }

        String supportedGoal = getSupportedPluginGoal();
        if (supportedGoal == null) {
            return _handle(executionEvent);
        } else {
            String[] gag = supportedGoal.split(":");
            if (gag.length == 3) {
                MojoExecution execution = executionEvent.getMojoExecution();
                if (execution.getGroupId().equals(gag[0]) && execution.getArtifactId().equals(gag[1]) && execution.getGoal().equals(gag[2])) {
                    _handle(executionEvent);
                    return true;
                } else {
                    return false;
                }
            } else {
                reporter.print(toString() + " - unsupported supportedPluginGoal:" + supportedGoal);
                return false;
            }
        }

    }

    @Override
    public boolean _handle(@Nonnull ExecutionEvent executionEvent) {
        List<String> configurationParameters = getConfigurationParametersToReport(executionEvent);

        Xpp3Dom root = new Xpp3Dom("ExecutionEvent");
        root.setAttribute("class", executionEvent.getClass().getName());
        root.setAttribute("type", executionEvent.getType().name());

        root.addChild(newElement("project", executionEvent.getProject()));

        MojoExecution execution = executionEvent.getMojoExecution();

        if (execution == null) {
            root.addChild(new Xpp3Dom("no-execution-found"));
        } else {
            Xpp3Dom plugin = new Xpp3Dom("plugin");
            root.addChild(plugin);

            plugin.setAttribute("groupId", execution.getGroupId());
            plugin.setAttribute("artifactId", execution.getArtifactId());
            plugin.setAttribute("goal", execution.getGoal());
            plugin.setAttribute("version", execution.getVersion());
            if (execution.getExecutionId() != null) {
                // See JENKINS-47508, caused by plugin being declared and invoked by the <reports> section
                plugin.setAttribute("executionId", execution.getExecutionId());
            }
            if (execution.getLifecyclePhase() != null) {
                // protect against null lifecyclePhase. cause is NOT clear
                plugin.setAttribute("lifecyclePhase", execution.getLifecyclePhase());
            }

            for (String configurationParameter : configurationParameters) {
                Xpp3Dom element = fullClone(configurationParameter, execution.getConfiguration().getChild(configurationParameter));
                if (element != null) {
                    plugin.addChild(element);
                }
            }
        }

        addDetails(executionEvent, root);

        reporter.print(root);

        return true;
    }

    protected void addDetails(@Nonnull ExecutionEvent executionEvent, @Nonnull Xpp3Dom root) {

    }

    @Nonnull
    protected abstract List<String> getConfigurationParametersToReport(ExecutionEvent executionEvent);

    @Nullable
    protected abstract ExecutionEvent.Type getSupportedType();


    @Nullable
    protected String getSupportedPluginGoal() {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + getSupportedType() + "," + getSupportedPluginGoal() + "]";
    }

    @Nullable
    protected String getMojoConfigurationValue(@Nonnull MojoExecution execution, @Nonnull String elementName) {
        Xpp3Dom element = execution.getConfiguration().getChild(elementName);
        return element == null ? null : element.getValue() == null ? element.getAttribute("default-value") : element.getValue();
    }

    @Nullable
    protected Xpp3Dom fullClone(@Nonnull String elementName, @Nullable Xpp3Dom element) {
        if (element == null) {
            return null;
        }

        Xpp3Dom result = new Xpp3Dom(elementName);

        Xpp3Dom[] childs = element.getChildren();
        if (childs != null && childs.length > 0) {
            for (Xpp3Dom child : childs) {
                result.addChild(fullClone(child.getName(), child));
            }
        } else {
            result.setValue(element.getValue() == null ? element.getAttribute("default-value") : element.getValue());
        }

        return result;
    }
}
