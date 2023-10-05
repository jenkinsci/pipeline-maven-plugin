package org.jenkinsci.plugins.pipeline.maven.db.util;

import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SqlUtils {

    private SqlUtils() {}

    public static void dumpResultsetMetadata(ResultSet rst, PrintStream out) {
        try {
            ResultSetMetaData metaData = rst.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                out.print(metaData.getColumnName(i) + "\t");
            }
            out.println();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        out.println();
    }
}
