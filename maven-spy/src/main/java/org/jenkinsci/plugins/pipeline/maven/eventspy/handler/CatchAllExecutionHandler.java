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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class CatchAllExecutionHandler extends AbstractExecutionHandler {

    public CatchAllExecutionHandler(@Nonnull MavenEventReporter reporter) {
        super(reporter);
    }

    @Nonnull
    @Override
    protected List<String> getConfigurationParametersToReport(ExecutionEvent executionEvent) {

        MojoExecution mojoExecution = executionEvent.getMojoExecution();
        if (mojoExecution == null) {
            return Collections.emptyList();
        }

        Xpp3Dom configuration = mojoExecution.getConfiguration();
        List<String> parameters = new ArrayList<String>();
        for (Xpp3Dom configurationParameter : configuration.getChildren()) {
            parameters.add(configurationParameter.getName());
        }
        return parameters;
    }

    @Nullable
    @Override
    protected ExecutionEvent.Type getSupportedType() {
        return null;
    }
}
