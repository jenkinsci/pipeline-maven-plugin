package org.jenkinsci.plugins.pipeline.maven;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class NonProductionGradeDatabaseWarningAdministrativeMonitor extends AdministrativeMonitor {

    @Override
    public boolean isActivated() {
        String jdbcUrl = GlobalPipelineMavenConfig.get().getJdbcUrl();
        if (!isBlank(jdbcUrl)) {
            return jdbcUrl.startsWith("jdbc:h2:");
        }

        if (GlobalPipelineMavenConfig.get().isDaoInitialized()) {
            return !GlobalPipelineMavenConfig.get().getDao().isEnoughProductionGradeForTheWorkload();
        }

        return false;
    }

    @Override
    public String getDisplayName() {
        return "Pipeline Maven Integration - Non Production Database";
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public void doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            rsp.sendRedirect(req.getContextPath() + "/manage");
        } else {
            rsp.sendRedirect(req.getContextPath() + "/configureTools");
        }
    }
}