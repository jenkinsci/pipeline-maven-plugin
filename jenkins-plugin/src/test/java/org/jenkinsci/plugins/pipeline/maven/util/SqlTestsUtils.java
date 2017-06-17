package org.jenkinsci.plugins.pipeline.maven.util;

import org.h2.jdbcx.JdbcConnectionPool;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SqlTestsUtils {


    public static void dump(String sql, JdbcConnectionPool jdbcConnectionPool, PrintStream out) throws RuntimeSqlException {
        try (Connection connection = jdbcConnectionPool.getConnection()) {
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
                out.print(rst.getObject(i) + "\t");
            }
            out.println();
            rowCount++;
        }
        out.println();
        out.println("Count: " + rowCount);
        out.println();
    }

    public static int countRows(@Nonnull String sql, @Nonnull JdbcConnectionPool jdbcConnectionPool, Object... params) throws SQLException {
        try (Connection cnn = jdbcConnectionPool.getConnection()) {
            return countRows(sql, cnn, params);
        }
    }

    public static int countRows(@Nonnull String sql, @Nonnull Connection cnn, Object... params) throws SQLException {
        try (PreparedStatement stmt = cnn.prepareStatement("select count(*) from (" + sql + ")")) {
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

    public static void silentlyDeleteTableRows(JdbcConnectionPool jdbcConnectionPool, String... tables) {
        for (String table : tables) {
            try (Connection cnn = jdbcConnectionPool.getConnection()) {
                try (Statement stmt = cnn.createStatement()) {
                    int deleted = stmt.executeUpdate("delete from " + table);
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == 42102) {
                    // ignore "table not found"
                } else {
                    throw new RuntimeSqlException(e);
                }
            }
        }
    }
}
