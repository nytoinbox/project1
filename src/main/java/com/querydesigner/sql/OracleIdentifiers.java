package com.querydesigner.sql;

import java.util.Set;
import java.util.regex.Pattern;

/** Quotes Oracle identifiers only when needed (lower case, special characters or reserved words). */
public final class OracleIdentifiers {

    private static final Pattern SIMPLE = Pattern.compile("[A-Z][A-Z0-9_$#]*");

    private static final Set<String> RESERVED = Set.of(
            "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CASE",
            "CHAR", "CHECK", "CLUSTER", "COLUMN", "COMMENT", "COMPRESS", "CONNECT", "CONSTRAINT", "CREATE",
            "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIMESTAMP", "DATE", "DAY", "DECIMAL", "DEFAULT",
            "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END", "EXCEPT", "EXCLUSIVE", "EXISTS", "FALSE",
            "FETCH", "FILE", "FLOAT", "FOR", "FOREIGN", "FROM", "FULL", "GRANT", "GROUP", "GROUPS", "HAVING",
            "HOUR", "IDENTIFIED", "IF", "IMMEDIATE", "IN", "INCREMENT", "INDEX", "INITIAL", "INNER", "INSERT",
            "INTEGER", "INTERSECT", "INTERVAL", "INTO", "IS", "JOIN", "KEY", "LEFT", "LEVEL", "LIKE", "LIMIT",
            "LOCK", "LONG", "MAXEXTENTS", "MINUS", "MINUTE", "MLSLABEL", "MODE", "MODIFY", "MONTH", "NATURAL",
            "NOAUDIT", "NOCOMPRESS", "NOT", "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "OFFSET", "ON",
            "ONLINE", "OPTION", "OR", "ORDER", "OUTER", "PCTFREE", "PRIMARY", "PRIOR", "PUBLIC", "RAW",
            "RENAME", "RESOURCE", "REVOKE", "RIGHT", "ROW", "ROWID", "ROWNUM", "ROWS", "SECOND", "SELECT",
            "SESSION", "SET", "SHARE", "SIZE", "SMALLINT", "START", "SUCCESSFUL", "SYNONYM", "SYSDATE",
            "TABLE", "THEN", "TO", "TRIGGER", "TRUE", "UID", "UNION", "UNIQUE", "UPDATE", "USER", "USING",
            "VALIDATE", "VALUE", "VALUES", "VARCHAR", "VARCHAR2", "VIEW", "WHEN", "WHENEVER", "WHERE",
            "WITH", "YEAR");

    private OracleIdentifiers() {
    }

    public static String quote(String identifier) {
        if (SIMPLE.matcher(identifier).matches() && !RESERVED.contains(identifier)) {
            return identifier;
        }
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
