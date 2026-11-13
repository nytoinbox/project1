package com.querydesigner.model;

import java.util.List;

/** A table instance placed on the diagram. The same table may appear several times with different aliases. */
public class TableRef {
    private final String schema;
    private final String name;
    private final List<ColumnMeta> columns;
    private String alias;
    private int x;
    private int y;

    public TableRef(String schema, String name, String alias, List<ColumnMeta> columns) {
        this.schema = schema;
        this.name = name;
        this.alias = alias;
        this.columns = List.copyOf(columns);
    }

    public String getSchema() { return schema; }
    public String getName() { return name; }
    public List<ColumnMeta> getColumns() { return columns; }
    public String getAlias() { return alias; }
    void setAlias(String alias) { this.alias = alias; }

    public boolean hasCustomAlias() { return !alias.equals(name); }

    public int getX() { return x; }
    public int getY() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    public boolean isSameTable(String otherSchema, String otherName) {
        return name.equalsIgnoreCase(otherName)
                && (schema == null || otherSchema == null || schema.equalsIgnoreCase(otherSchema));
    }

    @Override
    public String toString() { return alias; }
}
