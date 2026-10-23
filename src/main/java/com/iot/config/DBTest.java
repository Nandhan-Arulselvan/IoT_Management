
package com.iot.config;

import java.sql.Connection;
import java.sql.SQLException;

public class DBTest {

    public static void main(String[] args) {

        try (Connection con = DBConnection.getConnection()) {
            System.out.println("Oracle JDBC connection successful!");
            System.out.println("Database: " +
                con.getMetaData().getDatabaseProductName());
            System.out.println("Version: " +
                con.getMetaData().getDatabaseProductVersion());

        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
