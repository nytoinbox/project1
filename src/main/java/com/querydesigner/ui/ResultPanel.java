package com.querydesigner.ui;

import com.querydesigner.db.QueryResult;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Results pane: rows returned by the last run. */
class ResultPanel extends JPanel {

    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel status = new JLabel("Press Run (F5) to see the data");

    ResultPanel() {
        super(new BorderLayout());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(true);
        status.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    void showRunning() {
        status.setForeground(Color.DARK_GRAY);
        status.setText("Running...");
    }

    void showResult(QueryResult r) {
        tableModel.setDataVector(r.rows().toArray(Object[][]::new), r.columns().toArray());
        for (int i = 0; i < table.getColumnCount(); i++) {
            int w = Math.max(80, Math.min(260, r.columns().get(i).length() * 9 + 24));
            table.getColumnModel().getColumn(i).setPreferredWidth(w);
        }
        status.setForeground(Color.DARK_GRAY);
        status.setText(r.rows().size() + " row(s) in " + r.elapsedMillis() + " ms"
                + (r.truncated() ? "  — limited to the first " + r.rows().size() + " rows" : ""));
    }

    void showError(String message) {
        tableModel.setDataVector(new Object[0][0], new Object[0]);
        status.setForeground(new Color(0xB00020));
        status.setText("Error: " + message);
    }

    void clear() {
        tableModel.setDataVector(new Object[0][0], new Object[0]);
        status.setForeground(Color.DARK_GRAY);
        status.setText("Press Run (F5) to see the data");
    }
}
