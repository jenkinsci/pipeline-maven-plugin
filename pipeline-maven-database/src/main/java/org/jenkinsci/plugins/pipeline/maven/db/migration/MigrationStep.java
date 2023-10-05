package org.jenkinsci.plugins.pipeline.maven.db.migration;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import jenkins.model.Jenkins;

public interface MigrationStep {
    void execute(@NonNull Connection cnn, @NonNull JenkinsDetails jenkinsDetails) throws SQLException;

    /**
     * for unit tests outside of Jenkins
     */
    class JenkinsDetails {
        @NonNull
        public String getMasterLegacyInstanceId() {
            return Jenkins.get().getLegacyInstanceId();
        }

        @NonNull
        public String getMasterRootUrl() {
            return Objects.toString(Jenkins.get().getRootUrl(), "");
        }
    }
}
