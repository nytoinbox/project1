package com.querydesigner.ui;

import com.querydesigner.db.ConnectionManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

/** Asks for Oracle connection details and opens the connection in the background. */
class ConnectionDialog extends JDialog {

    record Result(Connection connection, String description, String schema) {
    }

    private final JTextField host = new JTextField("localhost", 20);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(1521, 1, 65535, 1));
    private final JTextField service = new JTextField("ORCLPDB1", 20);
    private final JTextField user = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JTextField schema = new JTextField(20);
    private final JCheckBox useUrl = new JCheckBox("Use a full JDBC URL instead");
    private final JTextField url = new JTextField("jdbc:oracle:thin:@//localhost:1521/ORCLPDB1", 30);
    private final JLabel message = new JLabel(" ");
    private final JButton connect = new JButton("Connect");
    private Result result;

    ConnectionDialog(JFrame owner) {
        super(owner, "Connect to Oracle", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        int row = 0;
        row = addRow(form, row, "Host", host);
        row = addRow(form, row, "Port", port);
        row = addRow(form, row, "Service name", service);
        GridBagConstraints c = constraints(0, row++);
        c.gridwidth = 2;
        form.add(useUrl, c);
        row = addRow(form, row, "JDBC URL", url);
        row = addRow(form, row, "Username", user);
        row = addRow(form, row, "Password", password);
        row = addRow(form, row, "Schema (optional)", schema);
        schema.setToolTipText("Schema to browse; defaults to the user's own schema");
        c = constraints(0, row);
        c.gridwidth = 2;
        message.setForeground(new Color(0xB00020));
        form.add(message, c);

        useUrl.addActionListener(e -> updateEnabled());
        updateEnabled();

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        connect.addActionListener(e -> doConnect());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(connect);
        buttons.add(cancel);
        getRootPane().setDefaultButton(connect);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns null if cancelled. */
    Result showDialog() {
        setVisible(true);
        return result;
    }

    private void updateEnabled() {
        boolean u = useUrl.isSelected();
        url.setEnabled(u);
        host.setEnabled(!u);
        port.setEnabled(!u);
        service.setEnabled(!u);
    }

    private void doConnect() {
        String jdbcUrl = useUrl.isSelected() ? url.getText().trim()
                : ConnectionManager.oracleUrl(host.getText(), (Integer) port.getValue(), service.getText());
        String u = user.getText().trim();
        String p = new String(password.getPassword());
        if (u.isEmpty()) {
            message.setText("Please enter a username.");
            return;
        }
        connect.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        message.setForeground(Color.DARK_GRAY);
        message.setText("Connecting...");
        new SwingWorker<Connection, Void>() {
            @Override
            protected Connection doInBackground() throws Exception {
                return ConnectionManager.open(jdbcUrl, u, p);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                connect.setEnabled(true);
                try {
                    Connection conn = get();
                    String s = schema.getText().trim();
                    result = new Result(conn, u + " @ " + jdbcUrl, s.isEmpty() ? u.toUpperCase() : s.toUpperCase());
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    message.setForeground(new Color(0xB00020));
                    message.setText("<html><body style='width:320px'>" + cause.getMessage() + "</body></html>");
                    pack();
                }
            }
        }.execute();
    }

    private static int addRow(JPanel form, int row, String label, java.awt.Component field) {
        form.add(new JLabel(label), constraints(0, row));
        GridBagConstraints c = constraints(1, row);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
        return row + 1;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 3, 3, 3);
        return c;
    }
}
