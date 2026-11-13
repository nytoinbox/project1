package com.querydesigner.model;

/**
 * Join condition {@code left.leftColumn = right.rightColumn}.
 * For LEFT the rows of {@code left} are preserved, for RIGHT the rows of {@code right}.
 */
public class JoinDef {
    private final TableRef left;
    private final String leftColumn;
    private final TableRef right;
    private final String rightColumn;
    private JoinType type;

    public JoinDef(TableRef left, String leftColumn, TableRef right, String rightColumn, JoinType type) {
        this.left = left;
        this.leftColumn = leftColumn;
        this.right = right;
        this.rightColumn = rightColumn;
        this.type = type;
    }

    public TableRef getLeft() { return left; }
    public String getLeftColumn() { return leftColumn; }
    public TableRef getRight() { return right; }
    public String getRightColumn() { return rightColumn; }
    public JoinType getType() { return type; }
    void setType(JoinType type) { this.type = type; }

    public boolean involves(TableRef t) { return left == t || right == t; }

    public boolean connects(TableRef a, TableRef b) {
        return (left == a && right == b) || (left == b && right == a);
    }

    public TableRef other(TableRef t) { return left == t ? right : left; }

    boolean sameCondition(TableRef l, String lc, TableRef r, String rc) {
        return (left == l && right == r && leftColumn.equals(lc) && rightColumn.equals(rc))
                || (left == r && right == l && leftColumn.equals(rc) && rightColumn.equals(lc));
    }
}
