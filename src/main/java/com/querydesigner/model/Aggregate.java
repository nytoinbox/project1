package com.querydesigner.model;

/** The "Group By" column of the criteria grid. */
public enum Aggregate {
    NONE("", null),
    GROUP_BY("Group By", null),
    SUM("Sum", "SUM"),
    COUNT("Count", "COUNT"),
    COUNT_DISTINCT("Count Distinct", "COUNT"),
    AVG("Avg", "AVG"),
    MIN("Min", "MIN"),
    MAX("Max", "MAX");

    private final String label;
    private final String function;

    Aggregate(String label, String function) {
        this.label = label;
        this.function = function;
    }

    /** SQL aggregate function name, or null for NONE / GROUP_BY. */
    public String function() { return function; }

    public boolean isFunction() { return function != null; }

    @Override
    public String toString() { return label; }
}
