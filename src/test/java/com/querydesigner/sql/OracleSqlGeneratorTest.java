package com.querydesigner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydesigner.model.Aggregate;
import com.querydesigner.model.ColumnMeta;
import com.querydesigner.model.ColumnSpec;
import com.querydesigner.model.JoinType;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.SortType;
import com.querydesigner.model.TableRef;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleSqlGeneratorTest {

    private final OracleSqlGenerator gen = new OracleSqlGenerator();
    private QueryModel m;

    @BeforeEach
    void setUp() {
        m = new QueryModel();
    }

    private TableRef table(String name, String... cols) {
        return m.addTable("SALES", name, java.util.Arrays.stream(cols)
                .map(c -> new ColumnMeta(c, "NUMBER", false)).toList(), 0, 0);
    }

    @Test
    void emptyModelGivesEmptySql() {
        assertEquals("", gen.generate(m));
    }

    @Test
    void selectsAllWhenNoColumnChosen() {
        table("ORDERS", "ORDER_ID");
        assertEquals("SELECT *\nFROM SALES.ORDERS", gen.generate(m));
    }

    @Test
    void innerJoinWithSelectedColumns() {
        TableRef o = table("ORDERS", "ORDER_ID", "CUSTOMER_ID");
        TableRef c = table("CUSTOMERS", "CUSTOMER_ID", "CITY");
        m.addJoin(o, "CUSTOMER_ID", c, "CUSTOMER_ID", JoinType.INNER);
        m.addColumn(o, "ORDER_ID");
        m.addColumn(c, "CITY");
        assertEquals("""
                SELECT ORDERS.ORDER_ID,
                       CUSTOMERS.CITY
                FROM SALES.ORDERS
                INNER JOIN SALES.CUSTOMERS ON ORDERS.CUSTOMER_ID = CUSTOMERS.CUSTOMER_ID""", gen.generate(m));
    }

    @Test
    void leftJoinIsFlippedWhenPreservedTableIsAddedLater() {
        TableRef c = table("CUSTOMERS", "CUSTOMER_ID");
        TableRef o = table("ORDERS", "ORDER_ID", "CUSTOMER_ID");
        // keep all ORDERS rows; ORDERS is written after CUSTOMERS so it becomes a RIGHT join
        m.addJoin(o, "CUSTOMER_ID", c, "CUSTOMER_ID", JoinType.LEFT);
        assertEquals("""
                SELECT *
                FROM SALES.CUSTOMERS
                RIGHT OUTER JOIN SALES.ORDERS ON ORDERS.CUSTOMER_ID = CUSTOMERS.CUSTOMER_ID""", gen.generate(m));
    }

    @Test
    void groupBySumWithWhereHavingAndOrderBy() {
        TableRef c = table("CUSTOMERS", "CUSTOMER_ID", "REGION", "CITY");
        TableRef o = table("ORDERS", "ORDER_ID", "CUSTOMER_ID", "AMOUNT");
        m.addJoin(o, "CUSTOMER_ID", c, "CUSTOMER_ID", JoinType.INNER);
        m.setGroupBy(true);
        ColumnSpec region = m.addColumn(c, "REGION");
        ColumnSpec amount = m.addColumn(o, "AMOUNT");
        amount.setAggregate(Aggregate.SUM);
        amount.setAlias("total_amount");
        amount.setFilter("> 1000");
        amount.setSortType(SortType.DESC);
        amount.setSortOrder(1);
        ColumnSpec city = m.addColumn(c, "CITY");
        city.setOutput(false);
        city.setAggregate(Aggregate.NONE);
        city.setFilter("'Bangkok'");
        region.setSortType(SortType.ASC);
        region.setSortOrder(2);
        assertEquals("""
                SELECT CUSTOMERS.REGION,
                       SUM(ORDERS.AMOUNT) AS "total_amount"
                FROM SALES.CUSTOMERS
                INNER JOIN SALES.ORDERS ON ORDERS.CUSTOMER_ID = CUSTOMERS.CUSTOMER_ID
                WHERE CUSTOMERS.CITY = 'Bangkok'
                GROUP BY CUSTOMERS.REGION
                HAVING SUM(ORDERS.AMOUNT) > 1000
                ORDER BY SUM(ORDERS.AMOUNT) DESC, CUSTOMERS.REGION""", gen.generate(m));
    }

    @Test
    void aggregatesGetDefaultAliasesAndCountStar() {
        TableRef o = table("ORDERS", "STATUS", "AMOUNT");
        m.addColumn(o, "STATUS");
        ColumnSpec sum = m.addColumn(o, "AMOUNT");
        sum.setAggregate(Aggregate.SUM);
        ColumnSpec sum2 = sum.copy();
        m.addColumnSpec(sum2, 2);
        m.addColumnSpec(ColumnSpec.countAll(), 3);
        ColumnSpec distinct = m.addColumn(o, "STATUS");
        distinct.setAggregate(Aggregate.COUNT_DISTINCT);
        assertEquals("""
                SELECT ORDERS.STATUS,
                       SUM(ORDERS.AMOUNT) AS SUM_AMOUNT,
                       SUM(ORDERS.AMOUNT) AS SUM_AMOUNT_2,
                       COUNT(*) AS COUNT_ALL,
                       COUNT(DISTINCT ORDERS.STATUS) AS COUNT_DISTINCT_STATUS
                FROM SALES.ORDERS
                GROUP BY ORDERS.STATUS""", gen.generate(m));
    }

    @Test
    void selfJoinUsesAliasesWithoutAsKeyword() {
        TableRef e = table("EMPLOYEES", "EMP_ID", "MANAGER_ID", "NAME");
        TableRef mgr = table("EMPLOYEES", "EMP_ID", "MANAGER_ID", "NAME");
        assertEquals("EMPLOYEES_1", mgr.getAlias());
        m.renameAlias(mgr, "MGR");
        m.addJoin(e, "MANAGER_ID", mgr, "EMP_ID", JoinType.LEFT);
        m.addColumn(e, "NAME");
        ColumnSpec boss = m.addColumn(mgr, "NAME");
        boss.setAlias("MANAGER_NAME");
        assertEquals("""
                SELECT EMPLOYEES.NAME,
                       MGR.NAME AS MANAGER_NAME
                FROM SALES.EMPLOYEES
                LEFT OUTER JOIN SALES.EMPLOYEES MGR ON EMPLOYEES.MANAGER_ID = MGR.EMP_ID""", gen.generate(m));
    }

    @Test
    void multipleConditionsBetweenSameTablesAreCombined() {
        TableRef a = table("A", "K1", "K2");
        TableRef b = table("B", "K1", "K2");
        m.addJoin(a, "K1", b, "K1", JoinType.INNER);
        m.addJoin(a, "K2", b, "K2", JoinType.INNER);
        assertEquals("SELECT *\nFROM SALES.A\nINNER JOIN SALES.B ON A.K1 = B.K1 AND A.K2 = B.K2", gen.generate(m));
    }

    @Test
    void unconnectedTablesUseCrossJoin() {
        table("A", "X");
        table("B", "Y");
        assertEquals("SELECT *\nFROM SALES.A\nCROSS JOIN SALES.B", gen.generate(m));
    }

    @Test
    void quotesLowerCaseAndReservedIdentifiersAndAddsFetchFirst() {
        TableRef t = m.addTable("app", "Order", List.of(new ColumnMeta("date", "DATE", false),
                new ColumnMeta("LEVEL", "NUMBER", false)), 0, 0);
        m.addColumn(t, "date");
        m.addColumn(t, "LEVEL").setFilter("IN (1, 2)");
        assertEquals("""
                SELECT "Order"."date",
                       "Order"."LEVEL"
                FROM "app"."Order"
                WHERE "Order"."LEVEL" IN (1, 2)
                FETCH FIRST 50 ROWS ONLY""", gen.generate(m, 50));
    }

    @Test
    void filterOperators() {
        assertEquals("X like 'A%'", OracleSqlGenerator.condition("X", "like 'A%'"));
        assertEquals("X IS NULL", OracleSqlGenerator.condition("X", "IS NULL"));
        assertEquals("X NOT IN (1)", OracleSqlGenerator.condition("X", "NOT IN (1)"));
        assertEquals("X BETWEEN 1 AND 5", OracleSqlGenerator.condition("X", "BETWEEN 1 AND 5"));
        assertEquals("X >= 3", OracleSqlGenerator.condition("X", ">= 3"));
        assertEquals("X = 3", OracleSqlGenerator.condition("X", "3"));
        assertEquals("(X = 1 OR X = 2)", OracleSqlGenerator.condition("X", "= 1 OR X = 2"));
    }
}
