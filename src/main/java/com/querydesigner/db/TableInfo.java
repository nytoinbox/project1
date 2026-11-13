package com.querydesigner.db;

public record TableInfo(String schema, String name, boolean view) {
    @Override
    public String toString() {
        return view ? name + "  (view)" : name;
    }
}
