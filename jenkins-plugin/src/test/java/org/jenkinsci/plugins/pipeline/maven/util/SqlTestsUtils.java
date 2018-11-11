package org.jenkinsci.plugins.pipeline.maven.util;

import com.google.common.base.Objects;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

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
                out.print(Objects.firstNonNull(object, "#null#") + "\t");
            }
            out.println();
            rowCount++;
        }
        out.println();
        out.println("Count: " + rowCount);
        out.println();
    }

    public static int countRows(@Nonnull String sql, @Nonnull DataSource ds, Object... params) throws SQLException {
        try (Connection cnn = ds.getConnection()) {
            return countRows(sql, cnn, params);
        }
    }

    public static int countRows(@Nonnull String sql, @Nonnull Connection cnn, Object... params) throws SQLException {
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
                    int deleted = stmt.executeUpdate("delete from " + table);
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1) {
                    // ignore "H2 table not found"
                } else if (e.getErrorCode() == 1146) {
                    // ignore "MySQL table not found"
                } else {
                    throw new RuntimeSqlException(e);
                }
            }
        }
    }
}
