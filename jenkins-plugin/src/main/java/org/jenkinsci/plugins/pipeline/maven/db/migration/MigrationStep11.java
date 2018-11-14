package org.jenkinsci.plugins.pipeline.maven.db.migration;

import hudson.model.Cause;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MigrationStep11 implements MigrationStep {

    private final static Logger LOGGER = Logger.getLogger(MigrationStep11.class.getName());

    @Override
    public void execute(@Nonnull Connection cnn, @Nonnull JenkinsDetails jenkinsDetails) throws SQLException {
        int jobCount = 0;
        int buildCauseCount = 0;

        LOGGER.info("Upgrade table JENKINS_BUILD_UPSTREAM_CAUSE...");
        String select = "SELECT JENKINS_JOB.FULL_NAME, JENKINS_JOB.JENKINS_MASTER_ID, JENKINS_BUILD.NUMBER, JENKINS_BUILD.ID " +
                " FROM JENKINS_BUILD INNER JOIN JENKINS_JOB ON JENKINS_BUILD.JOB_ID = JENKINS_JOB.ID ORDER BY JENKINS_JOB.FULL_NAME, JENKINS_BUILD.NUMBER";

        String insert = " INSERT INTO JENKINS_BUILD_UPSTREAM_CAUSE (UPSTREAM_BUILD_ID, DOWNSTREAM_BUILD_ID) " +
                " SELECT UPSTREAM_BUILD.ID, ? " +
                " FROM JENKINS_BUILD AS UPSTREAM_BUILD, JENKINS_JOB AS UPSTREAM_JOB " +
                " WHERE " +
                "   UPSTREAM_BUILD.JOB_ID = UPSTREAM_JOB.ID AND" +
                "   UPSTREAM_JOB.FULL_NAME = ? AND" +
                "   UPSTREAM_JOB.JENKINS_MASTER_ID = ? AND" +
                "   UPSTREAM_BUILD.NUMBER = ? ";
        try (PreparedStatement insertStmt = cnn.prepareStatement(insert)) {
            try (PreparedStatement selectStmt = cnn.prepareStatement(select)) {
                try (ResultSet rst = selectStmt.executeQuery()) {
                    while (rst.next()) {
                        jobCount++;
                        if ((jobCount < 100 && (jobCount % 10) == 0) ||
                                (jobCount < 500 && (jobCount % 20) == 0) ||
                                ((jobCount % 50) == 0)) {
                            LOGGER.log(Level.INFO, "#" + jobCount + " - " + rst.getString("FULL_NAME") + "...");
                        }

                        String jobFullName = rst.getString("full_name");
                        int buildNumber = rst.getInt("number");
                        long buildId = rst.getLong("id");
                        long jenkinsMasterId = rst.getLong("jenkins_master_id");

                        try {
                            WorkflowJob pipeline = Jenkins.getInstance().getItemByFullName(jobFullName, WorkflowJob.class);
                            if (pipeline == null) {
                                continue;
                            }
                            WorkflowRun build = pipeline.getBuildByNumber(buildNumber);
                            if (build == null) {
                                continue;
                            }

                            for (Cause cause : build.getCauses()) {
                                if (cause instanceof Cause.UpstreamCause) {
                                    Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
                                    String upstreamJobFullName = upstreamCause.getUpstreamProject();
                                    int upstreamJobNumber = upstreamCause.getUpstreamBuild();

                                    insertStmt.setLong(1, buildId);
                                    insertStmt.setString(2, upstreamJobFullName);
                                    insertStmt.setLong(3, jenkinsMasterId);
                                    insertStmt.setInt(4, upstreamJobNumber);
                                    insertStmt.addBatch();
                                    buildCauseCount++;
                                }
                            }
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.WARNING, "Silently ignore exception migrating build " + jobFullName + "#" + buildNumber, e);
                        }

                    }
                    insertStmt.executeBatch();
                }
            }
        }
        LOGGER.info("Successfully upgraded table JENKINS_BUILD_UPSTREAM_CAUSE, " + jobCount + " jobs scanned, " + buildCauseCount + " job causes inserted");

    }
}
