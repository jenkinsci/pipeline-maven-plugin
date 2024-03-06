package org.jenkinsci.plugins.pipeline.maven.db;

import com.mysql.cj.exceptions.MysqlErrorNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.db.util.RuntimeSqlException;

@Extension
public class PipelineMavenPluginMariaDbDao extends AbstractPipelineMavenPluginDao {

    public PipelineMavenPluginMariaDbDao() {
        super();
    }

    @Override
    public String getDescription() {
        return Messages.dao_mariadb_description();
    }

    /**
     * Extract the MariaDB server version from {@link DatabaseMetaData#getDatabaseProductVersion()}
     *
     * @param jdbcDatabaseProductVersion such as  "5.5.5-10.2.20-MariaDB", "5.5.5-10.3.11-MariaDB-1:10.3.11+maria~bionic"
     * @return {@code null} if this is not a MariaDB version, the MariaDB server version (e.g. "10.2.20", "10.3.11") if parsed, the entire {@link DatabaseMetaData#getDatabaseProductVersion()} if the parsing oof the MariaDB server version failed
     */
    @Nullable
    public static String extractMariaDbVersion(@Nullable String jdbcDatabaseProductVersion) {
        if (jdbcDatabaseProductVersion == null) {
            return null;
        }

        if (!jdbcDatabaseProductVersion.contains("MariaDB")) {
            return null;
        }

        String mariaDbVersion = StringUtils.substringBetween(jdbcDatabaseProductVersion, "-", "-MariaDB");

        if (mariaDbVersion == null) { // MariaDB version format has changed.
            return jdbcDatabaseProductVersion;
        } else {
            return mariaDbVersion;
        }
    }

    public PipelineMavenPluginMariaDbDao(@NonNull DataSource ds) {
        super(ds);
    }

    @Override
    public String getJdbcScheme() {
        return "mariadb";
    }

    @Override
    protected void handleDatabaseInitialisationException(SQLException e) {
        if (MysqlErrorNumbers.SQL_STATE_ILLEGAL_ARGUMENT.equals(e.getSQLState())) {
            LOGGER.log(Level.FINE, "Ignore sql exception " + e.getErrorCode() + " - " + e.getSQLState(), e);
        } else if (MysqlErrorNumbers.ER_EMPTY_QUERY == e.getErrorCode()) {
            LOGGER.log(Level.FINE, "Ignore sql exception " + e.getErrorCode() + " - " + e.getSQLState(), e);
        } else if (MysqlErrorNumbers.ER_TOO_LONG_KEY == e.getErrorCode()) {
            // see JENKINS-54784
            throw new RuntimeSqlException(e);
        } else {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    protected void registerJdbcDriver() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "MariaDB driver 'org.mariadb.jdbc.Driver' not found. Please install the 'MariaDB Database Plugin' to install the MariaDB driver");
        }
    }

    @Override
    protected String getDatabaseDescription() {
        try (Connection cnn = getDataSource().getConnection()) {
            DatabaseMetaData metaData = cnn.getMetaData();
            String version = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            try (Statement stmt = cnn.createStatement()) {
                try (ResultSet rst = stmt.executeQuery("select AURORA_VERSION()")) {
                    rst.next();
                    version += " / Amazon Aurora " + rst.getString(1);
                } catch (SQLException e) {
                    if (e.getErrorCode() == MysqlErrorNumbers.ER_SP_DOES_NOT_EXIST) {
                        // not amazon aurora, the function aurora_version() does not exist
                    } else {
                        LOGGER.log(Level.WARNING, "Exception checking Amazon Aurora version", e);
                    }
                }
            }
            return version;
        } catch (SQLException e) {
            return "#" + e.toString() + "#";
        }
    }

    @Override
    public boolean isEnoughProductionGradeForTheWorkload() {
        return true;
    }
}
