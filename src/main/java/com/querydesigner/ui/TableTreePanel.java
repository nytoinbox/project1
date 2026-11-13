package com.querydesigner.ui;

import com.querydesigner.db.MetadataLoader;
import com.querydesigner.db.TableInfo;
import java.awt.BorderLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Left pane: schema selector and the list of tables/views to drag onto the diagram. */
class TableTreePanel extends JPanel {

    private final JComboBox<String> schemaBox = new JComboBox<>();
    private final JTextField filter = new JTextField();
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Tables");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final BiConsumer<String, String> onDoubleClick;
    private final java.util.function.Consumer<Exception> onError;
    private MetadataLoader loader;
    private List<TableInfo> allTables = List.of();
    private boolean loadingSchemas;

    TableTreePanel(BiConsumer<String, String> onDoubleClick, java.util.function.Consumer<Exception> onError) {
        super(new BorderLayout(0, 4));
        this.onDoubleClick = onDoubleClick;
        this.onError = onError;
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        JPanel schemaRow = new JPanel(new BorderLayout(4, 0));
        schemaRow.add(new JLabel("Schema"), BorderLayout.WEST);
        schemaRow.add(schemaBox, BorderLayout.CENTER);
        top.add(schemaRow, BorderLayout.NORTH);
        filter.putClientProperty("JTextField.placeholderText", "Filter tables");
        filter.setToolTipText("Filter tables by name");
        top.add(filter, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        tree.setDragEnabled(true);
        tree.setToolTipText("Drag a table onto the diagram (or double-click)");
        tree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<String> lines = new ArrayList<>();
                TreePath[] paths = tree.getSelectionPaths();
                if (paths != null) {
                    for (TreePath p : paths) {
                        Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
                        if (o instanceof TableInfo t) {
                            lines.add((t.schema() == null ? "" : t.schema()) + "\t" + t.name());
                        }
                    }
                }
                return lines.isEmpty() ? null : new StringSelection(String.join("\n", lines));
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath p = tree.getPathForLocation(e.getX(), e.getY());
                    if (p != null && ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject()
                            instanceof TableInfo t) {
                        onDoubleClick.accept(t.schema(), t.name());
                    }
                }
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);

        schemaBox.addActionListener(e -> {
            if (!loadingSchemas && schemaBox.getSelectedItem() != null) {
                loadTables((String) schemaBox.getSelectedItem());
            }
        });
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }

            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }

            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
    }

    void load(MetadataLoader loader, String schema) {
        this.loader = loader;
        loadingSchemas = true;
        try {
            schemaBox.removeAllItems();
            List<String> schemas = loader.schemas();
            for (String s : schemas) {
                schemaBox.addItem(s);
            }
            String selected = schemas.stream().filter(s -> s.equalsIgnoreCase(schema)).findFirst().orElse(schema);
            if (selected != null && !schemas.contains(selected)) {
                schemaBox.addItem(selected);
            }
            schemaBox.setSelectedItem(selected);
        } catch (SQLException ex) {
            onError.accept(ex);
        } finally {
            loadingSchemas = false;
        }
        loadTables(schema);
    }

    void clear() {
        loader = null;
        schemaBox.removeAllItems();
        allTables = List.of();
        applyFilter();
    }

    private void loadTables(String schema) {
        if (loader == null) {
            return;
        }
        try {
            allTables = loader.tables(schema);
        } catch (SQLException ex) {
            allTables = List.of();
            onError.accept(ex);
        }
        applyFilter();
    }

    private void applyFilter() {
        String f = filter.getText().trim().toUpperCase(Locale.ROOT);
        root.removeAllChildren();
        for (TableInfo t : allTables) {
            if (f.isEmpty() || t.name().toUpperCase(Locale.ROOT).contains(f)) {
                root.add(new DefaultMutableTreeNode(t, false));
            }
        }
        treeModel.reload();
    }
}
