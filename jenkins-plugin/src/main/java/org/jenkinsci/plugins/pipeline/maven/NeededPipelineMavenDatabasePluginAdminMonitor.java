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
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
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