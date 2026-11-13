package com.querydesigner.model;

public enum SortType {
    NONE(""), ASC("Ascending"), DESC("Descending");

    private final String label;

    SortType(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
