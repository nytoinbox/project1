package com.querydesigner.db;

import com.querydesigner.model.ColumnMeta;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads schemas, tables, columns and foreign keys through JDBC {@link DatabaseMetaData}. */
public class MetadataLoader {

    private final Connection connection;

    public MetadataLoader(Connection connection) {
        this.connection = connection;
    }

    public String currentSchema() throws SQLException {
        String s = connection.getSchema();
        return s != null ? s : connection.getMetaData().getUserName();
    }

    public List<String> schemas() throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                result.add(rs.getString("TABLE_SCHEM"));
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public List<TableInfo> tables(String schema) throws SQLException {
        List<TableInfo> result = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(null, schema, "%", null)) {
            while (rs.next()) {
                String type = String.valueOf(rs.getString("TABLE_TYPE")).toUpperCase(Locale.ROOT);
                boolean view = type.contains("VIEW");
                boolean table = type.equals("TABLE") || type.equals("BASE TABLE");
                if (table || (view && !type.contains("SYSTEM"))) {
                    result.add(new TableInfo(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"), view));
                }
            }
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public List<ColumnMeta> columns(String schema, String table) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        Set<String> pk = new HashSet<>();
        try (ResultSet rs = md.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                pk.add(rs.getString("COLUMN_NAME"));
            }
        }
        List<ColumnMeta> result = new ArrayList<>();
        try (ResultSet rs = md.getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                result.add(new ColumnMeta(name, rs.getString("TYPE_NAME"), pk.contains(name)));
            }
        }
        return result;
    }

    /** Foreign keys where the table is either the referencing or the referenced side. */
    public List<ForeignKey> foreignKeys(String schema, String table) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        Set<ForeignKey> result = new LinkedHashSet<>();
        try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
            readKeys(rs, result);
        }
        try (ResultSet rs = md.getExportedKeys(null, schema, table)) {
            readKeys(rs, result);
        }
        return new ArrayList<>(result);
    }

    private static void readKeys(ResultSet rs, Set<ForeignKey> out) throws SQLException {
        while (rs.next()) {
            out.add(new ForeignKey(
                    rs.getString("FKTABLE_SCHEM"), rs.getString("FKTABLE_NAME"), rs.getString("FKCOLUMN_NAME"),
                    rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME")));
        }
    }
}
