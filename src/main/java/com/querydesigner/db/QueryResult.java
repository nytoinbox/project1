package com.querydesigner.db;

import java.util.List;

public record QueryResult(List<String> columns, List<Object[]> rows, long elapsedMillis, boolean truncated) {
}
