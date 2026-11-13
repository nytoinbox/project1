package com.querydesigner.ui;

import com.querydesigner.model.ColumnMeta;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.TableRef;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/** A table on the diagram: title bar (drag to move) and one checkbox row per column. */
class TableBox extends JPanel {

    static final Color HEADER_BG = new Color(0x2F4F7F);
    private static final int MAX_BODY_HEIGHT = 230;

    final TableRef table;
    private final QueryModel model;
    private final DiagramCanvas canvas;
    private final JLabel title = new JLabel();
    private final Map<String, ColumnRow> rows = new LinkedHashMap<>();
    private final JScrollPane scroll;

    TableBox(TableRef table, QueryModel model, DiagramCanvas canvas) {
        super(new BorderLayout());
        this.table = table;
        this.model = model;
        this.canvas = canvas;
        setBorder(BorderFactory.createLineBorder(HEADER_BG));
        setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 2));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setToolTipText((table.getSchema() == null ? "" : table.getSchema() + ".") + table.getName());
        header.add(title, BorderLayout.CENTER);
        JButton close = new JButton("×");
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setFocusable(false);
        close.setToolTipText("Remove table from the query");
        close.addActionListener(e -> model.removeTable(table));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        header.add(buttons, BorderLayout.EAST);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        HeaderMouse hm = new HeaderMouse();
        header.addMouseListener(hm);
        header.addMouseMotionListener(hm);
        title.addMouseListener(hm);
        title.addMouseMotionListener(hm);
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        for (ColumnMeta col : table.getColumns()) {
            ColumnRow row = new ColumnRow(col);
            rows.put(col.name(), row);
            body.add(row);
        }
        scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> canvas.repaint());
        add(scroll, BorderLayout.CENTER);

        refresh();
        Dimension bodySize = body.getPreferredSize();
        int width = Math.max(160, Math.min(280, Math.max(header.getPreferredSize().width, bodySize.width) + 24));
        int height = header.getPreferredSize().height + Math.min(bodySize.height, MAX_BODY_HEIGHT) + 4;
        setBounds(table.getX(), table.getY(), width, height);
    }

    void refresh() {
        title.setText(table.hasCustomAlias() ? table.getAlias() + "  (" + table.getName() + ")" : table.getName());
        for (ColumnRow row : rows.values()) {
            row.check.setSelected(model.isColumnSelected(table, row.column.name()));
        }
    }

    /** Anchor point (canvas coordinates) of a column row on the left or right edge of this box. */
    Point anchor(String column, boolean rightEdge) {
        int x = rightEdge ? getX() + getWidth() : getX();
        ColumnRow row = rows.get(column);
        if (row == null) {
            return new Point(x, getY() + 12);
        }
        Point p = SwingUtilities.convertPoint(row, 0, row.getHeight() / 2, canvas);
        Rectangle view = SwingUtilities.convertRectangle(scroll, scroll.getViewport().getBounds(), canvas);
        int y = Math.max(view.y + 2, Math.min(p.y, view.y + view.height - 2));
        return new Point(x, y);
    }

    private void toggleColumn(String column, boolean selected) {
        if (selected) {
            model.addColumn(table, column);
        } else {
            model.removeColumns(table, column);
        }
    }

    private void showHeaderMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename alias...");
        rename.addActionListener(a -> {
            String alias = JOptionPane.showInputDialog(this, "Alias for " + table.getName() + ":", table.getAlias());
            if (alias != null && !model.renameAlias(table, alias)) {
                JOptionPane.showMessageDialog(this, "The alias is empty or already used.", "Rename alias",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        JMenuItem all = new JMenuItem("Select all columns");
        all.addActionListener(a -> model.batch(() -> {
            for (ColumnMeta c : table.getColumns()) {
                if (!model.isColumnSelected(table, c.name())) {
                    model.addColumn(table, c.name());
                }
            }
        }));
        JMenuItem none = new JMenuItem("Clear column selection");
        none.addActionListener(a -> model.batch(() -> table.getColumns()
                .forEach(c -> model.removeColumns(table, c.name()))));
        JMenuItem remove = new JMenuItem("Remove table");
        remove.addActionListener(a -> model.removeTable(table));
        menu.add(rename);
        menu.addSeparator();
        menu.add(all);
        menu.add(none);
        menu.addSeparator();
        menu.add(remove);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /** Row of the box: checkbox selects the column; dragging the name onto another column creates a join. */
    final class ColumnRow extends JPanel {
        final ColumnMeta column;
        final JCheckBox check = new JCheckBox();
        final TableBox box = TableBox.this;

        ColumnRow(ColumnMeta column) {
            super(new BorderLayout());
            this.column = column;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            check.setOpaque(false);
            check.setMargin(new Insets(0, 2, 0, 2));
            check.addActionListener(e -> toggleColumn(column.name(), check.isSelected()));
            JLabel label = new JLabel(column.name());
            label.setToolTipText(column.typeName() + (column.primaryKey() ? " (primary key)" : "")
                    + " — drag onto another table's column to create a join");
            if (column.primaryKey()) {
                label.setText("<html><b>" + column.name() + "</b> <font color='#B07A00'>PK</font></html>");
            }
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter drag = new MouseAdapter() {
                private boolean dragging;

                @Override
                public void mousePressed(MouseEvent e) {
                    dragging = SwingUtilities.isLeftMouseButton(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging) {
                        return;
                    }
                    Point p = SwingUtilities.convertPoint(label, e.getPoint(), canvas);
                    canvas.updateJoinDrag(ColumnRow.this, p);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging) {
                        dragging = false;
                        canvas.endJoinDrag(ColumnRow.this, SwingUtilities.convertPoint(label, e.getPoint(), canvas));
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        check.setSelected(!check.isSelected());
                        toggleColumn(column.name(), check.isSelected());
                    }
                }
            };
            label.addMouseListener(drag);
            label.addMouseMotionListener(drag);
            add(check, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }
    }

    private final class HeaderMouse extends MouseAdapter {
        private Point pressOnScreen;
        private Point startLocation;

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showHeaderMenu(e);
                return;
            }
            pressOnScreen = e.getLocationOnScreen();
            startLocation = getLocation();
            canvas.setComponentZOrder(TableBox.this, 0);
            canvas.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showHeaderMenu(e);
            }
            pressOnScreen = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (pressOnScreen == null) {
                return;
            }
            Point now = e.getLocationOnScreen();
            int x = Math.max(0, startLocation.x + now.x - pressOnScreen.x);
            int y = Math.max(0, startLocation.y + now.y - pressOnScreen.y);
            setLocation(x, y);
            table.setPosition(x, y);
            canvas.boxMoved();
        }
    }
}
