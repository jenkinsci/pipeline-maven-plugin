package org.jenkinsci.plugins.pipeline.maven.db.migration.h2;

import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;
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
        String select = "select jenkins_job.full_name, jenkins_job.jenkins_master_id, jenkins_build.number, jenkins_build.id " +
                " from jenkins_build inner join jenkins_job on jenkins_build.job_id = jenkins_job.id order by jenkins_job.full_name, jenkins_build.number";

        String insert = " insert into JENKINS_BUILD_UPSTREAM_CAUSE (upstream_build_id, downstream_build_id) " +
                " select upstream_build.id, ? " +
                " from jenkins_build as upstream_build, jenkins_job as upstream_job " +
                " where " +
                "   upstream_build.job_id = upstream_job.id and" +
                "   upstream_job.full_name = ? and" +
                "   upstream_job.jenkins_master_id = ? and" +
                "   upstream_build.number = ? ";
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
