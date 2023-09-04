package org.jenkinsci.plugins.pipeline.maven.util;

import org.h2.api.ErrorCode;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.sql.DataSource;

import static java.util.Optional.ofNullable;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SqlTestsUtils {


    public static void dump(String sql, DataSource ds, PrintStream out) throws RuntimeSqlException {
        try (Connection connection = ds.getConnection()) {
            out.println("# DUMP " + sql);
            out.println();
            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rst = stmt.executeQuery(sql)) {
                    dump(rst, out);
                }
            }
            out.println("----");
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    public static void dump(ResultSet rst, PrintStream out) throws SQLException {
        ResultSetMetaData metaData = rst.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            out.print(metaData.getColumnName(i) + "\t");
        }
        out.println();

        int rowCount = 0;
        while (rst.next()) {
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                Object object = rst.getObject(i);
                out.print(ofNullable(object).orElse("#null#") + "\t");
            }
            out.println();
            rowCount++;
        }
        out.println();
        out.println("Count: " + rowCount);
        out.println();
    }

    public static int countRows(@NonNull String sql, @NonNull DataSource ds, Object... params) throws SQLException {
        try (Connection cnn = ds.getConnection()) {
            return countRows(sql, cnn, params);
        }
    }

    public static int countRows(@NonNull String sql, @NonNull Connection cnn, Object... params) throws SQLException {
        String sqlQuery ;
        if (sql.startsWith("select * from")){
            sqlQuery = "select count(*) from " + sql.substring("select * from".length());
        } else {
            sqlQuery = "select count(*) from (" + sql + ")";        }

        try (PreparedStatement stmt = cnn.prepareStatement(sqlQuery)) {
            int idx = 1;
            for (Object param : params) {
                stmt.setObject(idx, param);
                idx++;
            }
            try (ResultSet rst = stmt.executeQuery()) {
                rst.next();
                return rst.getInt(1);
            }
        }
    }

    public static void silentlyDeleteTableRows(DataSource ds, String... tables) {
        for (String table : tables) {
            try (Connection cnn = ds.getConnection()) {
                try (Statement stmt = cnn.createStatement()) {
                    stmt.executeUpdate("delete from " + table);
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1) {
                    // ignore "H2 table not found"
                } else if (e.getErrorCode() == 1146) {
                    // ignore "MySQL table not found"
                } else if ("42P01".equals(e.getSQLState())) {
                    // ignore PostgreSQL "ERROR: relation "..." does not exist
                } else {
                    throw new RuntimeSqlException(e);
                }
            }
        }
    }
}
