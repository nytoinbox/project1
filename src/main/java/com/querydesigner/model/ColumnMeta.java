package com.querydesigner.model;

/** Metadata of a single column of a database table. */
public record ColumnMeta(String name, String typeName, boolean primaryKey) {
}
