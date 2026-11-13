package com.querydesigner.db;

/** One column pair of a foreign key: {@code fkTable.fkColumn -> pkTable.pkColumn}. */
public record ForeignKey(String fkSchema, String fkTable, String fkColumn,
                         String pkSchema, String pkTable, String pkColumn) {
}
