package com.querydesigner.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens JDBC connections to Oracle. */
public final class ConnectionManager {

    private ConnectionManager() {
    }

    /** Builds an Oracle thin URL in the EZConnect form {@code jdbc:oracle:thin:@//host:port/service}. */
    public static String oracleUrl(String host, int port, String serviceName) {
        return "jdbc:oracle:thin:@//" + host.trim() + ":" + port + "/" + serviceName.trim();
    }

    public static Connection open(String url, String user, String password) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        DriverManager.setLoginTimeout(15);
        Connection c = DriverManager.getConnection(url, props);
        c.setReadOnly(true);
        return c;
    }
}
