package com.querydesigner.db;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Executes read-only queries and collects at most {@code maxRows} rows. */
public final class QueryRunner {

    private QueryRunner() {
    }

    public static QueryResult execute(Connection connection, String sql, int maxRows) throws SQLException {
        String text = normalize(sql);
        String head = text.replaceFirst("^(\\s|\\(|--[^\\n]*\\n|/\\*.*?\\*/)+", "").toUpperCase(Locale.ROOT);
        if (!head.startsWith("SELECT") && !head.startsWith("WITH")) {
            throw new SQLException("Only SELECT / WITH queries can be run from the designer.");
        }
        long start = System.nanoTime();
        try (Statement st = connection.createStatement()) {
            if (maxRows > 0) {
                st.setMaxRows(maxRows + 1);
            }
            try (ResultSet rs = st.executeQuery(text)) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> columns = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    columns.add(md.getColumnLabel(i));
                }
                List<Object[]> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (maxRows > 0 && rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof Clob clob) {
                            v = clob.getSubString(1, (int) Math.min(clob.length(), 4000));
                        }
                        row[i - 1] = v;
                    }
                    rows.add(row);
                }
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                return new QueryResult(columns, rows, elapsed, truncated);
            }
        }
    }

    /** Removes trailing semicolons / slashes that JDBC does not accept. */
    static String normalize(String sql) {
        String s = sql.strip();
        while (s.endsWith(";") || s.endsWith("/")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        return s;
    }
}
