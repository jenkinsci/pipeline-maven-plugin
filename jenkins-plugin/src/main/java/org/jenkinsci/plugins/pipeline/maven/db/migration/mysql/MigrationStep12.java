package org.jenkinsci.plugins.pipeline.maven.db.migration.mysql;

import org.jenkinsci.plugins.pipeline.maven.db.migration.MigrationStep;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

public class MigrationStep12 implements MigrationStep {

    private final static Logger LOGGER = Logger.getLogger(MigrationStep12.class.getName());

    @Override
    public void execute(@Nonnull Connection cnn, @Nonnull JenkinsDetails jenkinsDetails) throws SQLException {

        try (Statement stmt = cnn.createStatement()) {
            stmt.execute("ALTER TABLE MAVEN_ARTIFACT MODIFY COLUMN VERSION varchar(100)");
            LOGGER.log(Level.INFO, "Successfully resized column MAVEN_ARTIFACT.VERSION to varchar(100)" );
        } catch (SQLException e) {
            // some old mysql version may not accept the resize due to constraints on the index size
            LOGGER.log(Level.WARNING, "Silently ignore failure to resize column MAVEN_ARTIFACT.VERSION to varchar(100). " +
                    "It is probably caused by the old version of the MySQL engine, it will not restrict the capabilities, " +
                    "it will just continue to restrict the max size of the maven_artifact.version column to 56 chars" );
        }
    }
}
