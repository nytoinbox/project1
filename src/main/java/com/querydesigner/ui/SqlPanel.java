package com.querydesigner.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** SQL pane: shows the generated statement; can be edited by hand before running. */
class SqlPanel extends JPanel {

    private final JTextArea area = new JTextArea();
    private final JLabel status = new JLabel();
    private boolean updating;
    private boolean manual;

    SqlPanel(Runnable onRun) {
        super(new BorderLayout());
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setTabSize(4);
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { edited(); }

            @Override
            public void removeUpdate(DocumentEvent e) { edited(); }

            @Override
            public void changedUpdate(DocumentEvent e) { }
        });

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JLabel title = new JLabel("SQL (Oracle)  ");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        bar.add(title);
        JButton copy = new JButton("Copy SQL");
        copy.setFocusable(false);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));
        JButton run = new JButton("Run (F5)");
        run.setFocusable(false);
        run.addActionListener(e -> onRun.run());
        bar.add(copy);
        bar.add(run);
        bar.addSeparator();
        status.setForeground(new Color(0xB05A00));
        bar.add(status);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    void setGeneratedSql(String sql) {
        updating = true;
        try {
            area.setText(sql);
            area.setCaretPosition(0);
        } finally {
            updating = false;
        }
        manual = false;
        status.setText("");
    }

    String getSql() { return area.getText(); }

    boolean isManuallyEdited() { return manual; }

    private void edited() {
        if (!updating && !manual) {
            manual = true;
            status.setText("Edited by hand — changes in the designer will replace this text");
        }
    }
}
