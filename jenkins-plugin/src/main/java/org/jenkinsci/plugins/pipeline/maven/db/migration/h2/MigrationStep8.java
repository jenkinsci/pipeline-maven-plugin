package org.jenkinsci.plugins.pipeline.maven.db.migration.h2;

import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MigrationStep8 implements MigrationStep {

    @Override
    public void execute(Connection cnn, JenkinsDetails jenkinsDetails) throws SQLException {
        Integer masterId = null;
        try (PreparedStatement stmt = cnn.prepareStatement("SELECT * from JENKINS_MASTER where legacy_instance_id=?")) {
            stmt.setString(1, jenkinsDetails.getMasterLegacyInstanceId());
            try (ResultSet rst = stmt.executeQuery()) {
                if (rst.next()) {
                    masterId = rst.getInt("ID");
                }
            }
        }
        if (masterId == null) {
            try (PreparedStatement stmt = cnn.prepareStatement("INSERT INTO JENKINS_MASTER(LEGACY_INSTANCE_ID, URL) values (?, ?)")) {
                stmt.setString(1, jenkinsDetails.getMasterLegacyInstanceId());
                stmt.setString(2, jenkinsDetails.getMasterRootUrl());
                stmt.execute();
                try (ResultSet rst = stmt.getGeneratedKeys()) {
                    if (rst.next()) {
                        masterId = rst.getInt(1);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
        try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB set JENKINS_MASTER_ID=? where JENKINS_MASTER_ID IS NULL")) {
            stmt.setInt(1, masterId);
            stmt.execute();
        }
    }
}
