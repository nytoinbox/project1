package com.querydesigner.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.querydesigner.model.Aggregate;
import com.querydesigner.model.ColumnSpec;
import com.querydesigner.model.JoinType;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.SortType;
import com.querydesigner.model.TableRef;
import com.querydesigner.sql.OracleSqlGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs generated SQL against the H2 demo database (Oracle mode). */
class DemoDatabaseTest {

    private static Connection conn;
    private static MetadataLoader meta;

    @BeforeAll
    static void open() throws SQLException {
        conn = DemoDatabase.open();
        meta = new MetadataLoader(conn);
    }

    @AfterAll
    static void close() throws SQLException {
        conn.close();
    }

    private static TableRef add(QueryModel m, String table) throws SQLException {
        return m.addTable(DemoDatabase.SCHEMA, table, meta.columns(DemoDatabase.SCHEMA, table), 0, 0);
    }

    @Test
    void metadataIsReadable() throws SQLException {
        List<String> tables = meta.tables(DemoDatabase.SCHEMA).stream().map(TableInfo::name).toList();
        assertEquals(List.of("CUSTOMERS", "ORDER_ITEMS", "ORDERS", "PRODUCTS"), tables);
        assertTrue(meta.columns("DEMO", "ORDER_ITEMS").stream().filter(c -> c.primaryKey()).count() == 2);
        List<ForeignKey> fks = meta.foreignKeys("DEMO", "ORDERS");
        assertTrue(fks.contains(new ForeignKey("DEMO", "ORDERS", "CUSTOMER_ID", "DEMO", "CUSTOMERS", "CUSTOMER_ID")));
        assertTrue(fks.contains(new ForeignKey("DEMO", "ORDER_ITEMS", "ORDER_ID", "DEMO", "ORDERS", "ORDER_ID")));
    }

    @Test
    void salesByRegionAndCategoryRuns() throws SQLException {
        QueryModel m = new QueryModel();
        TableRef c = add(m, "CUSTOMERS");
        TableRef o = add(m, "ORDERS");
        TableRef i = add(m, "ORDER_ITEMS");
        TableRef p = add(m, "PRODUCTS");
        m.addJoin(o, "CUSTOMER_ID", c, "CUSTOMER_ID", JoinType.INNER);
        m.addJoin(i, "ORDER_ID", o, "ORDER_ID", JoinType.INNER);
        m.addJoin(i, "PRODUCT_ID", p, "PRODUCT_ID", JoinType.INNER);
        m.setGroupBy(true);
        m.addColumn(c, "REGION");
        m.addColumn(p, "CATEGORY");
        ColumnSpec qty = m.addColumn(i, "QUANTITY");
        qty.setAggregate(Aggregate.SUM);
        qty.setFilter("> 10");
        qty.setSortType(SortType.DESC);
        qty.setSortOrder(1);
        m.addColumnSpec(ColumnSpec.countAll(), 3);
        ColumnSpec status = m.addColumn(o, "STATUS");
        status.setOutput(false);
        status.setAggregate(Aggregate.NONE);
        status.setFilter("<> 'CANCELLED'");

        String sql = new OracleSqlGenerator().generate(m, 100);
        QueryResult r = QueryRunner.execute(conn, sql, 100);
        assertEquals(List.of("REGION", "CATEGORY", "SUM_QUANTITY", "COUNT_ALL"), r.columns());
        assertFalse(r.rows().isEmpty());
        long previous = Long.MAX_VALUE;
        for (Object[] row : r.rows()) {
            long sum = ((Number) row[2]).longValue();
            assertTrue(sum > 10 && sum <= previous);
            previous = sum;
        }
    }

    @Test
    void outerJoinKeepsCustomersWithoutOrders() throws SQLException {
        QueryModel m = new QueryModel();
        TableRef c = add(m, "CUSTOMERS");
        TableRef o = add(m, "ORDERS");
        m.addJoin(c, "CUSTOMER_ID", o, "CUSTOMER_ID", JoinType.LEFT);
        m.setGroupBy(true);
        m.addColumn(c, "CUSTOMER_NAME");
        m.addColumn(o, "ORDER_ID").setAggregate(Aggregate.COUNT);
        QueryResult r = QueryRunner.execute(conn, new OracleSqlGenerator().generate(m), 0);
        assertEquals(10, r.rows().size());
    }

    @Test
    void onlySelectIsAllowed() {
        assertThrows(SQLException.class, () -> QueryRunner.execute(conn, "DELETE FROM ORDERS", 10));
    }

    @Test
    void truncatesAtMaxRows() throws SQLException {
        QueryResult r = QueryRunner.execute(conn, "SELECT * FROM ORDER_ITEMS;", 5);
        assertEquals(5, r.rows().size());
        assertTrue(r.truncated());
    }
}
