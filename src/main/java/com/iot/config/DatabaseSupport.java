package com.iot.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class DatabaseSupport {
    private DatabaseSupport() { }

    static boolean exists(Connection connection, String table, String idColumn, String id) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE " + idColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    static int count(Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    static String nextId(Connection connection, String table, String idColumn, String prefix) throws SQLException {
        String sql = "SELECT NVL(MAX(TO_NUMBER(SUBSTR(" + idColumn + ", 2))), 0) + 1 "
            + "FROM " + table + " WHERE REGEXP_LIKE(" + idColumn + ", '^" + prefix + "[0-9]+$')";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            result.next();
            return String.format("%s%03d", prefix, result.getInt(1));
        }
    }
}
