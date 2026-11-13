package com.querydesigner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Whole query being designed: tables, joins and criteria-grid columns. Notifies listeners on change. */
public class QueryModel {

    public interface Listener {
        void modelChanged();
    }

    private final List<TableRef> tables = new ArrayList<>();
    private final List<JoinDef> joins = new ArrayList<>();
    private final List<ColumnSpec> columns = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean distinct;
    private boolean groupBy;
    private int batchDepth;
    private boolean pendingChange;

    public void addListener(Listener l) { listeners.add(l); }

    public List<TableRef> getTables() { return Collections.unmodifiableList(tables); }
    public List<JoinDef> getJoins() { return Collections.unmodifiableList(joins); }
    public List<ColumnSpec> getColumns() { return Collections.unmodifiableList(columns); }

    public boolean isDistinct() { return distinct; }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
        fireChanged();
    }

    public boolean isGroupBy() { return groupBy; }

    /** Like the SSMS "Group By" button: turning on groups every plain column, turning off removes all grouping. */
    public void setGroupBy(boolean groupBy) {
        this.groupBy = groupBy;
        for (ColumnSpec c : columns) {
            if (groupBy && c.getAggregate() == Aggregate.NONE && !c.isStar()) {
                c.setAggregate(Aggregate.GROUP_BY);
            } else if (!groupBy && !c.isStar()) {
                c.setAggregate(Aggregate.NONE);
            }
        }
        if (!groupBy) {
            columns.removeIf(ColumnSpec::isStar);
        }
        fireChanged();
    }

    // ---------------------------------------------------------------- tables

    public TableRef addTable(String schema, String name, List<ColumnMeta> cols, int x, int y) {
        TableRef t = new TableRef(schema, name, uniqueAlias(name), cols);
        t.setPosition(x, y);
        tables.add(t);
        fireChanged();
        return t;
    }

    public void removeTable(TableRef t) {
        tables.remove(t);
        joins.removeIf(j -> j.involves(t));
        columns.removeIf(c -> c.getTable() == t);
        fireChanged();
    }

    public String uniqueAlias(String base) {
        if (!isAliasInUse(base, null)) {
            return base;
        }
        int n = 1;
        while (isAliasInUse(base + "_" + n, null)) {
            n++;
        }
        return base + "_" + n;
    }

    public boolean isAliasInUse(String alias, TableRef except) {
        String a = alias.toUpperCase(Locale.ROOT);
        return tables.stream().anyMatch(t -> t != except && t.getAlias().toUpperCase(Locale.ROOT).equals(a));
    }

    /** @return false if the alias is blank or already used by another table */
    public boolean renameAlias(TableRef t, String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        alias = alias.trim();
        if (isAliasInUse(alias, t)) {
            return false;
        }
        t.setAlias(alias);
        fireChanged();
        return true;
    }

    // ----------------------------------------------------------------- joins

    public JoinDef addJoin(TableRef left, String leftCol, TableRef right, String rightCol, JoinType type) {
        if (left == right) {
            return null;
        }
        for (JoinDef j : joins) {
            if (j.sameCondition(left, leftCol, right, rightCol)) {
                return j;
            }
        }
        JoinDef j = new JoinDef(left, leftCol, right, rightCol, type);
        joins.add(j);
        fireChanged();
        return j;
    }

    public void setJoinType(JoinDef j, JoinType type) {
        j.setType(type);
        fireChanged();
    }

    public void removeJoin(JoinDef j) {
        joins.remove(j);
        fireChanged();
    }

    // --------------------------------------------------------------- columns

    public ColumnSpec addColumn(TableRef t, String column) {
        ColumnSpec c = new ColumnSpec(t, column);
        if (groupBy) {
            c.setAggregate(Aggregate.GROUP_BY);
        }
        columns.add(c);
        fireChanged();
        return c;
    }

    public void addColumnSpec(ColumnSpec c, int index) {
        columns.add(Math.max(0, Math.min(index, columns.size())), c);
        fireChanged();
    }

    public void removeColumn(ColumnSpec c) {
        columns.remove(c);
        fireChanged();
    }

    public void removeColumns(TableRef t, String column) {
        columns.removeIf(c -> c.getTable() == t && c.getColumn().equals(column));
        fireChanged();
    }

    public boolean isColumnSelected(TableRef t, String column) {
        return columns.stream().anyMatch(c -> c.getTable() == t && c.getColumn().equals(column) && c.isOutput());
    }

    public void moveColumn(int from, int to) {
        if (from < 0 || from >= columns.size() || to < 0 || to >= columns.size() || from == to) {
            return;
        }
        columns.add(to, columns.remove(from));
        fireChanged();
    }

    public int nextSortOrder() {
        return columns.stream().mapToInt(ColumnSpec::getSortOrder).max().orElse(0) + 1;
    }

    public void clear() {
        tables.clear();
        joins.clear();
        columns.clear();
        distinct = false;
        groupBy = false;
        fireChanged();
    }

    // ---------------------------------------------------------------- events

    /** Runs several changes and fires a single change event at the end. */
    public void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
        }
        if (batchDepth == 0 && pendingChange) {
            fireChanged();
        }
    }

    public void fireChanged() {
        if (batchDepth > 0) {
            pendingChange = true;
            return;
        }
        pendingChange = false;
        for (Listener l : List.copyOf(listeners)) {
            l.modelChanged();
        }
    }
}
