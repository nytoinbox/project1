package com.querydesigner.model;

/** One row of the criteria grid. {@code table == null} means {@code *} (used with COUNT). */
public class ColumnSpec {
    public static final String STAR = "*";

    private final TableRef table;
    private final String column;
    private String alias = "";
    private boolean output = true;
    private Aggregate aggregate = Aggregate.NONE;
    private SortType sortType = SortType.NONE;
    private int sortOrder;
    private String filter = "";

    public ColumnSpec(TableRef table, String column) {
        this.table = table;
        this.column = column;
    }

    public static ColumnSpec countAll() {
        ColumnSpec c = new ColumnSpec(null, STAR);
        c.aggregate = Aggregate.COUNT;
        return c;
    }

    public ColumnSpec copy() {
        ColumnSpec c = new ColumnSpec(table, column);
        c.alias = "";
        c.output = output;
        c.aggregate = aggregate;
        c.filter = "";
        return c;
    }

    public boolean isStar() { return table == null; }

    public TableRef getTable() { return table; }
    public String getColumn() { return column; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias == null ? "" : alias.trim(); }
    public boolean isOutput() { return output; }
    public void setOutput(boolean output) { this.output = output; }
    public Aggregate getAggregate() { return aggregate; }
    public void setAggregate(Aggregate aggregate) { this.aggregate = aggregate == null ? Aggregate.NONE : aggregate; }
    public SortType getSortType() { return sortType; }
    public void setSortType(SortType sortType) { this.sortType = sortType == null ? SortType.NONE : sortType; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = Math.max(0, sortOrder); }
    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter == null ? "" : filter.trim(); }
}
