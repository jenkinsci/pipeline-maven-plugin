package org.jenkinsci.plugins.pipeline.maven.db.migration;

import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.SQLException;

public interface MigrationStep {
    void execute(@Nonnull Connection cnn, @Nonnull JenkinsDetails jenkinsDetails) throws SQLException;

    /**
     * for unit tests outside of Jenkins
     */
    public static class JenkinsDetails {
        public String getMasterLegacyInstanceId() {
            return Jenkins.getInstance().getLegacyInstanceId();
        }

        public String getMasterRootUrl(){
            return Jenkins.getInstance().getRootUrl();
        }
    }
}
