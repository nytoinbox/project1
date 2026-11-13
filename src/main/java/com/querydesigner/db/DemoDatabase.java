package com.querydesigner.db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Random;

/** In-memory H2 database in Oracle compatibility mode with a small sales schema, for trying the tool. */
public final class DemoDatabase {

    public static final String URL = "jdbc:h2:mem:querydesigner_demo;MODE=Oracle;DB_CLOSE_DELAY=-1";
    public static final String SCHEMA = "DEMO";

    private DemoDatabase() {
    }

    public static Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(URL, "sa", "");
        try (Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS DEMO CASCADE");
            st.execute("CREATE SCHEMA DEMO");
            st.execute("SET SCHEMA DEMO");
            st.execute("""
                    CREATE TABLE CUSTOMERS (
                      CUSTOMER_ID   NUMBER(10) PRIMARY KEY,
                      CUSTOMER_NAME VARCHAR2(100) NOT NULL,
                      CITY          VARCHAR2(50),
                      REGION        VARCHAR2(30))""");
            st.execute("""
                    CREATE TABLE PRODUCTS (
                      PRODUCT_ID   NUMBER(10) PRIMARY KEY,
                      PRODUCT_NAME VARCHAR2(100) NOT NULL,
                      CATEGORY     VARCHAR2(50),
                      UNIT_PRICE   NUMBER(10,2))""");
            st.execute("""
                    CREATE TABLE ORDERS (
                      ORDER_ID    NUMBER(10) PRIMARY KEY,
                      CUSTOMER_ID NUMBER(10) NOT NULL,
                      ORDER_DATE  DATE NOT NULL,
                      STATUS      VARCHAR2(20),
                      CONSTRAINT FK_ORDERS_CUSTOMER FOREIGN KEY (CUSTOMER_ID) REFERENCES CUSTOMERS (CUSTOMER_ID))""");
            st.execute("""
                    CREATE TABLE ORDER_ITEMS (
                      ORDER_ID   NUMBER(10) NOT NULL,
                      LINE_NO    NUMBER(5)  NOT NULL,
                      PRODUCT_ID NUMBER(10) NOT NULL,
                      QUANTITY   NUMBER(10) NOT NULL,
                      UNIT_PRICE NUMBER(10,2) NOT NULL,
                      CONSTRAINT PK_ORDER_ITEMS PRIMARY KEY (ORDER_ID, LINE_NO),
                      CONSTRAINT FK_ITEMS_ORDER FOREIGN KEY (ORDER_ID) REFERENCES ORDERS (ORDER_ID),
                      CONSTRAINT FK_ITEMS_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES PRODUCTS (PRODUCT_ID))""");
        }
        insertData(c);
        return c;
    }

    private static void insertData(Connection c) throws SQLException {
        String[][] customers = {
                {"Siam Trading", "Bangkok", "Central"}, {"Lanna Foods", "Chiang Mai", "North"},
                {"Andaman Resort", "Phuket", "South"}, {"Isan Agro", "Khon Kaen", "Northeast"},
                {"Chao Phraya Retail", "Bangkok", "Central"}, {"Doi Coffee", "Chiang Rai", "North"},
                {"Gulf Marine", "Songkhla", "South"}, {"Mekong Supply", "Udon Thani", "Northeast"},
                {"Eastern Seaboard Co.", "Chonburi", "East"}, {"Korat Hardware", "Nakhon Ratchasima", "Northeast"}};
        Object[][] products = {
                {"Jasmine Rice 5kg", "Food", "185.00"}, {"Arabica Coffee 250g", "Food", "220.00"},
                {"Fish Sauce 700ml", "Food", "45.00"}, {"Notebook A5", "Stationery", "35.00"},
                {"Ballpoint Pen (12)", "Stationery", "60.00"}, {"LED Bulb 9W", "Electrical", "79.00"},
                {"Extension Cord 3m", "Electrical", "259.00"}, {"Rubber Gloves", "Household", "39.00"}};
        String[] statuses = {"NEW", "PAID", "SHIPPED", "SHIPPED", "SHIPPED", "CANCELLED"};

        Random rnd = new Random(42);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO CUSTOMERS VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < customers.length; i++) {
                ps.setInt(1, i + 1);
                ps.setString(2, customers[i][0]);
                ps.setString(3, customers[i][1]);
                ps.setString(4, customers[i][2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO PRODUCTS VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < products.length; i++) {
                ps.setInt(1, i + 1);
                ps.setString(2, (String) products[i][0]);
                ps.setString(3, (String) products[i][1]);
                ps.setBigDecimal(4, new BigDecimal((String) products[i][2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        LocalDate start = LocalDate.of(2026, 1, 1);
        try (PreparedStatement order = c.prepareStatement("INSERT INTO ORDERS VALUES (?, ?, ?, ?)");
             PreparedStatement item = c.prepareStatement("INSERT INTO ORDER_ITEMS VALUES (?, ?, ?, ?, ?)")) {
            for (int o = 1; o <= 80; o++) {
                order.setInt(1, o);
                order.setInt(2, 1 + rnd.nextInt(customers.length));
                order.setDate(3, Date.valueOf(start.plusDays(rnd.nextInt(300))));
                order.setString(4, statuses[rnd.nextInt(statuses.length)]);
                order.addBatch();
                int lines = 1 + rnd.nextInt(4);
                for (int l = 1; l <= lines; l++) {
                    int p = rnd.nextInt(products.length);
                    BigDecimal price = new BigDecimal((String) products[p][2])
                            .multiply(BigDecimal.valueOf(90 + rnd.nextInt(11), 2))
                            .setScale(2, RoundingMode.HALF_UP);
                    item.setInt(1, o);
                    item.setInt(2, l);
                    item.setInt(3, p + 1);
                    item.setInt(4, 1 + rnd.nextInt(20));
                    item.setBigDecimal(5, price);
                    item.addBatch();
                }
            }
            order.executeBatch();
            item.executeBatch();
        }
    }
}
