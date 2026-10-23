
package com.iot.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
        "jdbc:oracle:thin:@//localhost:1521/xe";

    public static Connection getConnection() throws SQLException {
        String user = System.getenv("IOT_DB_USER");
        String password = System.getenv("IOT_DB_PASSWORD");
        if (user == null || password == null || user.trim().isEmpty() || password.isEmpty()) {
            throw new SQLException(
                "Set IOT_DB_USER and IOT_DB_PASSWORD first."
            );
        }

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                "Oracle JDBC driver could not be loaded.", e
            );
        }

        return DriverManager.getConnection(URL, user, password);
    }
}
