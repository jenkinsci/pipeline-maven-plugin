package org.jenkinsci.plugins.pipeline.maven.db.migration;

import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public interface MigrationStep {
    void execute(@Nonnull Connection cnn, @Nonnull JenkinsDetails jenkinsDetails) throws SQLException;

    /**
     * for unit tests outside of Jenkins
     */
    class JenkinsDetails {
        @Nonnull
        public String getMasterLegacyInstanceId() {
            return Jenkins.get().getLegacyInstanceId();
        }

        @Nonnull
        public String getMasterRootUrl(){
            return Objects.toString(Jenkins.get().getRootUrl(), "");
        }
    }
}
