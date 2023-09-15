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

package org.jenkinsci.plugins.pipeline.maven;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Objects;

/**
 *
 */
@Extension
public class NeededPipelineMavenDatabasePluginAdminMonitor extends AdministrativeMonitor {

    @Override
    public boolean isActivated() {
        String jdbcUrl = Objects.requireNonNull(GlobalPipelineMavenConfig.get()).getJdbcUrl();
        return (StringUtils.startsWith(jdbcUrl, "jdbc:h2")
                || StringUtils.startsWith(jdbcUrl, "jdbc:mysql")
                || StringUtils.startsWith(jdbcUrl, "jdbc:postgresql"))
                && Jenkins.get().getPlugin("pipeline-maven-database") == null;
    }

    @Override
    public String getDisplayName() {
        return "Pipeline Maven Integration - Need Pipeline Maven Database";
    }

}