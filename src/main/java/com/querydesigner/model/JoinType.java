package com.querydesigner.model;

public enum JoinType {
    INNER("INNER JOIN", "INNER"),
    LEFT("LEFT OUTER JOIN", "LEFT"),
    RIGHT("RIGHT OUTER JOIN", "RIGHT"),
    FULL("FULL OUTER JOIN", "FULL");

    private final String sql;
    private final String label;

    JoinType(String sql, String label) {
        this.sql = sql;
        this.label = label;
    }

    public String sql() { return sql; }
    public String label() { return label; }

    /** The equivalent join type when the two sides are swapped. */
    public JoinType flipped() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            default -> this;
        };
    }
}
