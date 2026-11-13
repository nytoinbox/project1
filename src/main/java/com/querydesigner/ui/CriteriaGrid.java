package com.querydesigner.ui;

import com.querydesigner.model.Aggregate;
import com.querydesigner.model.ColumnSpec;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.SortType;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** Criteria pane: one row per selected column with alias, output, aggregate, sort and filter. */
class CriteriaGrid extends JPanel {

    private static final String[] HEADERS = {
            "Column", "Table", "Alias", "Output", "Group By", "Sort Type", "Sort Order", "Filter"};
    private static final int COL_ALIAS = 2;
    private static final int COL_OUTPUT = 3;
    private static final int COL_AGG = 4;
    private static final int COL_SORT = 5;
    private static final int COL_ORDER = 6;
    private static final int COL_FILTER = 7;

    private final QueryModel model;
    private final GridModel gridModel = new GridModel();
    private final JTable table = new JTable(gridModel);

    CriteriaGrid(QueryModel model) {
        super(new BorderLayout());
        this.model = model;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getRowHeight() + 4);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSurrendersFocusOnKeystroke(true);
        table.getColumnModel().getColumn(COL_AGG).setCellEditor(new DefaultCellEditor(new JComboBox<>(Aggregate.values())));
        table.getColumnModel().getColumn(COL_SORT).setCellEditor(new DefaultCellEditor(new JComboBox<>(SortType.values())));
        int[] widths = {150, 100, 110, 50, 110, 90, 70, 200};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(COL_FILTER).setHeaderValue("Filter  (e.g. > 100, LIKE 'A%', IN (1,2))");

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("Add COUNT(*)", "Add a COUNT(*) column", () -> model.addColumnSpec(ColumnSpec.countAll(),
                model.getColumns().size())));
        bar.add(button("Duplicate", "Duplicate the selected row (e.g. SUM and AVG of the same column)", () -> {
            ColumnSpec c = selected();
            if (c != null) {
                int row = table.getSelectedRow();
                model.addColumnSpec(c.copy(), row + 1);
                select(row + 1);
            }
        }));
        bar.add(button("Remove", "Remove the selected row", () -> {
            ColumnSpec c = selected();
            if (c != null) {
                model.removeColumn(c);
            }
        }));
        bar.addSeparator();
        bar.add(button("Move up", "Move the selected row up", () -> move(-1)));
        bar.add(button("Move down", "Move the selected row down", () -> move(1)));
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    void refresh() {
        if (table.isEditing()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        int row = table.getSelectedRow();
        gridModel.fireTableDataChanged();
        select(row);
    }

    private void select(int row) {
        if (row >= 0 && row < table.getRowCount()) {
            table.setRowSelectionInterval(row, row);
        }
    }

    private ColumnSpec selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getColumns().get(row);
    }

    private void move(int delta) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        model.moveColumn(row, row + delta);
        select(row + delta);
    }

    private static JButton button(String text, String tooltip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private final class GridModel extends AbstractTableModel {
        @Override
        public int getRowCount() { return model.getColumns().size(); }

        @Override
        public int getColumnCount() { return HEADERS.length; }

        @Override
        public String getColumnName(int column) { return HEADERS[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case COL_OUTPUT -> Boolean.class;
                case COL_AGG -> Aggregate.class;
                case COL_SORT -> SortType.class;
                case COL_ORDER -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column >= COL_ALIAS;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ColumnSpec c = model.getColumns().get(row);
            return switch (column) {
                case 0 -> c.getColumn();
                case 1 -> c.isStar() ? "" : c.getTable().getAlias();
                case COL_ALIAS -> c.getAlias();
                case COL_OUTPUT -> c.isOutput();
                case COL_AGG -> c.getAggregate();
                case COL_SORT -> c.getSortType();
                case COL_ORDER -> c.getSortOrder() == 0 ? null : c.getSortOrder();
                case COL_FILTER -> c.getFilter();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            ColumnSpec c = model.getColumns().get(row);
            switch (column) {
                case COL_ALIAS -> c.setAlias((String) value);
                case COL_OUTPUT -> c.setOutput(Boolean.TRUE.equals(value));
                case COL_AGG -> {
                    Aggregate a = (Aggregate) value;
                    if (c.isStar() && a != Aggregate.COUNT) {
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                    c.setAggregate(a);
                }
                case COL_SORT -> {
                    c.setSortType((SortType) value);
                    if (c.getSortType() == SortType.NONE) {
                        c.setSortOrder(0);
                    } else if (c.getSortOrder() == 0) {
                        c.setSortOrder(model.nextSortOrder());
                    }
                }
                case COL_ORDER -> c.setSortOrder(value == null ? 0 : (Integer) value);
                case COL_FILTER -> c.setFilter((String) value);
                default -> {
                    return;
                }
            }
            model.fireChanged();
        }
    }
}
