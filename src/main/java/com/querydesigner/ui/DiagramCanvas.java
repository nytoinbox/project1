package com.querydesigner.ui;

import com.querydesigner.model.JoinDef;
import com.querydesigner.model.JoinType;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.TableRef;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/** Diagram pane: tables dropped from the tree, join lines between columns. */
class DiagramCanvas extends JPanel {

    interface DropHandler {
        void tableDropped(String schema, String table, Point location);
    }

    private static final Color LINE_COLOR = new Color(0x5A6B80);
    private static final Color LINE_HOVER = new Color(0xD9822B);
    private static final String HINT = "Drag tables here from the list on the left";

    private final QueryModel model;
    private final Map<TableRef, TableBox> boxes = new HashMap<>();
    private final List<JoinHit> hits = new ArrayList<>();
    private TableBox.ColumnRow dragSource;
    private Point dragPoint;
    private JoinDef hover;

    DiagramCanvas(QueryModel model, DropHandler dropHandler) {
        super(null);
        this.model = model;
        setBackground(new Color(0xF4F6F9));
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    Point p = support.isDrop() ? support.getDropLocation().getDropPoint() : new Point(20, 20);
                    for (String line : data.split("\n")) {
                        String[] parts = line.split("\t", -1);
                        if (parts.length == 2) {
                            dropHandler.tableDropped(parts[0].isEmpty() ? null : parts[0], parts[1], new Point(p));
                            p.translate(30, 30);
                        }
                    }
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowJoinMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowJoinMenu(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                JoinDef j = joinAt(e.getPoint());
                if (j != hover) {
                    hover = j;
                    setToolTipText(j == null ? null : "Right-click to change join type or delete");
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Adds/removes boxes so they match the model, then refreshes checkboxes and lines. */
    void syncWithModel() {
        boxes.entrySet().removeIf(e -> {
            if (!model.getTables().contains(e.getKey())) {
                remove(e.getValue());
                return true;
            }
            return false;
        });
        for (TableRef t : model.getTables()) {
            TableBox box = boxes.get(t);
            if (box == null) {
                box = new TableBox(t, model, this);
                boxes.put(t, box);
                add(box);
                setComponentZOrder(box, 0);
            }
            box.refresh();
        }
        if (hover != null && !model.getJoins().contains(hover)) {
            hover = null;
        }
        boxMoved();
    }

    void boxMoved() {
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 200;
        for (Component c : getComponents()) {
            w = Math.max(w, c.getX() + c.getWidth() + 40);
            h = Math.max(h, c.getY() + c.getHeight() + 40);
        }
        return new Dimension(w, h);
    }

    // -------------------------------------------------------- join dragging

    void updateJoinDrag(TableBox.ColumnRow source, Point p) {
        dragSource = source;
        dragPoint = p;
        repaint();
    }

    void endJoinDrag(TableBox.ColumnRow source, Point p) {
        dragSource = null;
        dragPoint = null;
        repaint();
        Component c = SwingUtilities.getDeepestComponentAt(this, p.x, p.y);
        while (c != null && !(c instanceof TableBox.ColumnRow)) {
            c = c.getParent();
        }
        if (c instanceof TableBox.ColumnRow target && target.box != source.box) {
            model.addJoin(source.box.table, source.column.name(), target.box.table, target.column.name(),
                    JoinType.INNER);
        }
    }

    // --------------------------------------------------------------- paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (boxes.isEmpty()) {
            g2.setColor(new Color(0x8A94A3));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(HINT, (getWidth() - fm.stringWidth(HINT)) / 2, getHeight() / 2);
        }

        hits.clear();
        for (JoinDef j : model.getJoins()) {
            TableBox a = boxes.get(j.getLeft());
            TableBox b = boxes.get(j.getRight());
            if (a == null || b == null) {
                continue;
            }
            boolean aIsLeft = a.getX() + a.getWidth() / 2 <= b.getX() + b.getWidth() / 2;
            Point pa = a.anchor(j.getLeftColumn(), aIsLeft);
            Point pb = b.anchor(j.getRightColumn(), !aIsLeft);
            int stub = aIsLeft ? 14 : -14;
            Path2D path = new Path2D.Double();
            path.moveTo(pa.x, pa.y);
            path.lineTo(pa.x + stub, pa.y);
            path.lineTo(pb.x - stub, pb.y);
            path.lineTo(pb.x, pb.y);

            boolean hot = j == hover;
            g2.setColor(hot ? LINE_HOVER : LINE_COLOR);
            g2.setStroke(new BasicStroke(hot ? 2.5f : 1.6f));
            g2.draw(path);
            // A dot marks the side whose rows are all kept by an outer join.
            drawEnd(g2, pa, j.getType() == JoinType.LEFT || j.getType() == JoinType.FULL);
            drawEnd(g2, pb, j.getType() == JoinType.RIGHT || j.getType() == JoinType.FULL);

            String label = j.getType().label();
            FontMetrics fm = g2.getFontMetrics();
            int mx = (pa.x + stub + pb.x - stub) / 2;
            int my = (pa.y + pb.y) / 2;
            Rectangle r = new Rectangle(mx - fm.stringWidth(label) / 2 - 5, my - fm.getHeight() / 2 - 1,
                    fm.stringWidth(label) + 10, fm.getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.setColor(hot ? LINE_HOVER : LINE_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.drawString(label, r.x + 5, r.y + 1 + fm.getAscent());
            hits.add(new JoinHit(j, new BasicStroke(8f).createStrokedShape(path), r));
        }

        if (dragSource != null && dragPoint != null) {
            Point from = dragSource.box.anchor(dragSource.column.name(), dragPoint.x > dragSource.box.getX());
            g2.setColor(LINE_HOVER);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[] {6f, 4f}, 0f));
            g2.drawLine(from.x, from.y, dragPoint.x, dragPoint.y);
        }
        g2.dispose();
    }

    private static void drawEnd(Graphics2D g2, Point p, boolean preserved) {
        if (preserved) {
            g2.fillOval(p.x - 4, p.y - 4, 8, 8);
        } else {
            g2.fillRect(p.x - 2, p.y - 2, 4, 4);
        }
    }

    // ---------------------------------------------------------- join menu

    private JoinDef joinAt(Point p) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            JoinHit h = hits.get(i);
            if (h.label.contains(p) || h.line.contains(p)) {
                return h.join;
            }
        }
        return null;
    }

    private void maybeShowJoinMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        JoinDef j = joinAt(e.getPoint());
        if (j == null) {
            return;
        }
        String l = j.getLeft().getAlias();
        String r = j.getRight().getAlias();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem header = new JMenuItem(l + "." + j.getLeftColumn() + " = " + r + "." + j.getRightColumn());
        header.setEnabled(false);
        menu.add(header);
        menu.addSeparator();
        ButtonGroup group = new ButtonGroup();
        addTypeItem(menu, group, j, JoinType.INNER, "Inner join (only matching rows)");
        addTypeItem(menu, group, j, JoinType.LEFT, "Select all rows from " + l);
        addTypeItem(menu, group, j, JoinType.RIGHT, "Select all rows from " + r);
        addTypeItem(menu, group, j, JoinType.FULL, "Full outer join (all rows from both)");
        menu.addSeparator();
        JMenuItem delete = new JMenuItem("Remove join");
        delete.addActionListener(a -> model.removeJoin(j));
        menu.add(delete);
        menu.show(this, e.getX(), e.getY());
    }

    private void addTypeItem(JPopupMenu menu, ButtonGroup group, JoinDef j, JoinType type, String text) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text, j.getType() == type);
        item.addActionListener(a -> model.setJoinType(j, type));
        group.add(item);
        menu.add(item);
    }

    private record JoinHit(JoinDef join, java.awt.Shape line, Rectangle label) {
    }
}
