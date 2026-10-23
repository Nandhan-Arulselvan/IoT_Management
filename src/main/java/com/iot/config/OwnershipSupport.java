package com.iot.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;

/** Verifies record ownership on the server for every account-scoped operation. */
final class OwnershipSupport {
    private OwnershipSupport() { }

    static String currentUserId(HttpServletRequest request) {
        return (String) request.getSession(false).getAttribute(AuthSupport.USER_ID);
    }

    static boolean ownsDevice(Connection connection, String userId, String deviceId) throws SQLException {
        return exists(connection, "SELECT 1 FROM DEVICES WHERE DEVICE_ID=? AND USER_ID=?", deviceId, userId);
    }

    static boolean ownsSensor(Connection connection, String userId, String sensorId) throws SQLException {
        return exists(connection, "SELECT 1 FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE s.SENSOR_ID=? AND d.USER_ID=?", sensorId, userId);
    }

    static boolean ownsReading(Connection connection, String userId, String readingId) throws SQLException {
        return exists(connection, "SELECT 1 FROM SENSOR_READINGS r JOIN SENSORS s ON r.SENSOR_ID=s.SENSOR_ID JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE r.READING_ID=? AND d.USER_ID=?", readingId, userId);
    }

    private static boolean exists(Connection connection, String sql, String recordId, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recordId);
            statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }
}
