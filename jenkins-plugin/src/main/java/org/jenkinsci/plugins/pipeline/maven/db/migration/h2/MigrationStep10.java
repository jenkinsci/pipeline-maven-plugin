package org.jenkinsci.plugins.pipeline.maven.db.migration.h2;

import hudson.model.Run;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MigrationStep10 implements MigrationStep {

    private final static Logger LOGGER = Logger.getLogger(MigrationStep10.class.getName());

    @Override
    public void execute(@Nonnull Connection cnn, @Nonnull JenkinsDetails jenkinsDetails) throws SQLException {
        int count = 0;

        LOGGER.info("Upgrade table JENKINS_JOB...");
        try (PreparedStatement stmt = cnn.prepareStatement("SELECT * from JENKINS_JOB ORDER BY FULL_NAME")) {
            try (ResultSet rst = stmt.executeQuery()) {
                while (rst.next()) {
                    count++;
                    if ((count < 100 && (count % 10) == 0) ||
                            (count < 500 && (count % 20) == 0) ||
                            ((count % 50) == 0)) {
                        LOGGER.log(Level.INFO, "#" + count + " - " + rst.getString("FULL_NAME") + "...");
                    }

                    long jobPrimaryKey = rst.getLong("ID");
                    Integer lastBuildNumber = findLastBuildNumber(cnn, jobPrimaryKey);
                    if (lastBuildNumber == null) {
                        // no build found, skip
                    } else {
                        updateJenkinsJobRecord(cnn, jobPrimaryKey, lastBuildNumber);
                    }
                }
            }
        }
        LOGGER.info("Successfully upgraded table JENKINS_JOB, " + count + " records upgraded");

    }

    protected void updateJenkinsJobRecord(@Nonnull Connection cnn, long jenkinsJobPrimaryKey, int lastBuildNumber) throws SQLException {
        try (PreparedStatement stmt = cnn.prepareStatement("UPDATE JENKINS_JOB set LAST_BUILD_NUMBER = ?, LAST_SUCCESSFUL_BUILD_NUMBER = ? where ID = ?")) {
            stmt.setInt(1, lastBuildNumber);
            // TRICK we assume that the last build is successful
            stmt.setInt(2, lastBuildNumber);
            stmt.setLong(3, jenkinsJobPrimaryKey);
            stmt.execute();
        }
    }

    /**
     * @return the last {@link Run#getNumber()} or {@code null} if no build found
     */
    @Nullable
    protected Integer findLastBuildNumber(@Nonnull Connection cnn, long jobPrimaryKey) throws SQLException {
        try (PreparedStatement stmt2 = cnn.prepareStatement("SELECT * FROM JENKINS_BUILD WHERE JOB_ID = ? ORDER BY JENKINS_BUILD.NUMBER DESC LIMIT 1")) {
            stmt2.setLong(1, jobPrimaryKey);
            try (ResultSet rst2 = stmt2.executeQuery()) {
                if (rst2.next()) {
                    return rst2.getInt("JENKINS_BUILD.NUMBER");
                } else {
                    return null;
                }
            }
        }
    }
}
