package com.querydesigner.ui;

import com.querydesigner.db.DemoDatabase;
import com.querydesigner.db.ForeignKey;
import com.querydesigner.db.MetadataLoader;
import com.querydesigner.db.QueryResult;
import com.querydesigner.db.QueryRunner;
import com.querydesigner.model.ColumnMeta;
import com.querydesigner.model.JoinType;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.TableRef;
import com.querydesigner.sql.OracleSqlGenerator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

/** Main window: table list | diagram / criteria / SQL / results, like the SSMS query designer. */
public class MainFrame extends JFrame {

    private final QueryModel model = new QueryModel();
    private final OracleSqlGenerator generator = new OracleSqlGenerator();
    private final TableTreePanel tablePanel;
    private final DiagramCanvas canvas;
    private final CriteriaGrid grid;
    private final SqlPanel sqlPanel;
    private final ResultPanel resultPanel = new ResultPanel();
    private final JToggleButton groupByButton = new JToggleButton("Group By");
    private final JToggleButton distinctButton = new JToggleButton("Distinct");
    private final JSpinner maxRows = new JSpinner(new SpinnerNumberModel(1000, 1, 1_000_000, 100));
    private final JLabel connectionLabel = new JLabel("Not connected");
    private Connection connection;
    private MetadataLoader metadata;
    private int nextDropOffset;

    public MainFrame() {
        super("Visual Query Designer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        tablePanel = new TableTreePanel((schema, table) -> {
            int o = 20 + (nextDropOffset++ % 8) * 30;
            addTable(schema, table, new Point(o, o));
        }, this::showError);
        canvas = new DiagramCanvas(model, this::addTable);
        grid = new CriteriaGrid(model);
        sqlPanel = new SqlPanel(this::runQuery);

        JScrollPane diagramScroll = new JScrollPane(canvas);
        diagramScroll.getVerticalScrollBar().setUnitIncrement(16);
        diagramScroll.getHorizontalScrollBar().setUnitIncrement(16);
        JSplitPane lower = split(JSplitPane.VERTICAL_SPLIT, sqlPanel, resultPanel, 140);
        JSplitPane middle = split(JSplitPane.VERTICAL_SPLIT, grid, lower, 170);
        JSplitPane right = split(JSplitPane.VERTICAL_SPLIT, diagramScroll, middle, 280);
        JSplitPane main = split(JSplitPane.HORIZONTAL_SPLIT, tablePanel, right, 230);

        add(buildToolBar(), BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        connectionLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        connectionLabel.setForeground(Color.DARK_GRAY);
        add(connectionLabel, BorderLayout.SOUTH);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run");
        getRootPane().getActionMap().put("run", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runQuery();
            }
        });

        model.addListener(this::onModelChanged);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closeConnection();
            }
        });
        setSize(new Dimension(1280, 860));
        setLocationRelativeTo(null);
        onModelChanged();
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(toolButton("Connect to Oracle...", this::connect));
        bar.add(toolButton("Open demo DB (H2)", this::openDemo));
        bar.addSeparator();
        groupByButton.setFocusable(false);
        groupByButton.setToolTipText("Add GROUP BY for every selected column, then choose Sum/Count/... in the grid");
        groupByButton.addActionListener(e -> model.setGroupBy(groupByButton.isSelected()));
        distinctButton.setFocusable(false);
        distinctButton.setToolTipText("SELECT DISTINCT");
        distinctButton.addActionListener(e -> model.setDistinct(distinctButton.isSelected()));
        bar.add(groupByButton);
        bar.add(distinctButton);
        bar.add(toolButton("Clear", () -> {
            model.clear();
            resultPanel.clear();
        }));
        bar.addSeparator();
        bar.add(new JLabel("Max rows "));
        maxRows.setMaximumSize(new Dimension(90, 28));
        maxRows.setToolTipText("Number of rows fetched when running the query");
        bar.add(maxRows);
        bar.add(toolButton("Run (F5)", this::runQuery));
        return bar;
    }

    private void onModelChanged() {
        canvas.syncWithModel();
        grid.refresh();
        sqlPanel.setGeneratedSql(generator.generate(model));
        groupByButton.setSelected(model.isGroupBy());
        distinctButton.setSelected(model.isDistinct());
    }

    // ---------------------------------------------------------- connection

    public void connect() {
        ConnectionDialog.Result r = new ConnectionDialog(this).showDialog();
        if (r != null) {
            setConnection(r.connection(), "Connected: " + r.description(), r.schema());
        }
    }

    public void openDemo() {
        try {
            setConnection(DemoDatabase.open(), "Connected: demo database (H2 in Oracle mode)", DemoDatabase.SCHEMA);
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void setConnection(Connection c, String description, String schema) {
        closeConnection();
        connection = c;
        metadata = new MetadataLoader(c);
        model.clear();
        resultPanel.clear();
        connectionLabel.setText(description);
        String s = schema;
        if (s == null || s.isBlank()) {
            try {
                s = metadata.currentSchema();
            } catch (SQLException ex) {
                s = null;
            }
        }
        tablePanel.load(metadata, s);
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing anyway
            }
            connection = null;
            metadata = null;
        }
    }

    // ------------------------------------------------------------- tables

    /** Adds a table to the diagram and creates joins for foreign keys to tables already on it. */
    public void addTable(String schema, String table, Point location) {
        if (metadata == null) {
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            List<ColumnMeta> columns = metadata.columns(schema, table);
            List<ForeignKey> keys = metadata.foreignKeys(schema, table);
            model.batch(() -> {
                TableRef added = model.addTable(schema, table, columns, Math.max(0, location.x), Math.max(0, location.y));
                for (ForeignKey fk : keys) {
                    if (added.isSameTable(fk.fkSchema(), fk.fkTable())) {
                        TableRef target = findOther(added, fk.pkSchema(), fk.pkTable());
                        if (target != null) {
                            model.addJoin(added, fk.fkColumn(), target, fk.pkColumn(), JoinType.INNER);
                        }
                    }
                    if (added.isSameTable(fk.pkSchema(), fk.pkTable())) {
                        TableRef source = findOther(added, fk.fkSchema(), fk.fkTable());
                        if (source != null) {
                            model.addJoin(source, fk.fkColumn(), added, fk.pkColumn(), JoinType.INNER);
                        }
                    }
                }
            });
        } catch (SQLException ex) {
            showError(ex);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private TableRef findOther(TableRef except, String schema, String name) {
        return model.getTables().stream()
                .filter(t -> t != except && t.isSameTable(schema, name))
                .findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- run

    public void runQuery() {
        if (connection == null) {
            resultPanel.showError("Not connected. Use \"Connect to Oracle...\" or \"Open demo DB\".");
            return;
        }
        int limit = (Integer) maxRows.getValue();
        String sql = sqlPanel.isManuallyEdited() ? sqlPanel.getSql() : generator.generate(model, limit);
        if (sql.isBlank()) {
            resultPanel.showError("Nothing to run — add a table first.");
            return;
        }
        Connection c = connection;
        resultPanel.showRunning();
        new SwingWorker<QueryResult, Void>() {
            @Override
            protected QueryResult doInBackground() throws Exception {
                return QueryRunner.execute(c, sql, limit);
            }

            @Override
            protected void done() {
                try {
                    resultPanel.showResult(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    resultPanel.showError(cause.getMessage());
                }
            }
        }.execute();
    }

    // -------------------------------------------------------------- utils

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static JButton toolButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JSplitPane split(int orientation, java.awt.Component a, java.awt.Component b, int divider) {
        JSplitPane s = new JSplitPane(orientation, a, b);
        s.setDividerLocation(divider);
        s.setResizeWeight(orientation == JSplitPane.HORIZONTAL_SPLIT ? 0 : 0.5);
        s.setContinuousLayout(true);
        s.setBorder(null);
        return s;
    }

    /** For scripted demos/tests: the query model behind the window. */
    public QueryModel getModel() {
        return model;
    }
}
