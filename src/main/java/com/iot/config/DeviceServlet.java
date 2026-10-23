package com.iot.config;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/devices")
public class DeviceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String userId = OwnershipSupport.currentUserId(request);
        String search = WebSupport.text(request, "search", 50), status = WebSupport.text(request, "status", 20);
        String type = WebSupport.text(request, "type", 30), sort = WebSupport.text(request, "sort", 20);
        if (!"name".equals(sort) && !"status".equals(sort) && !"type".equals(sort)) sort = "id";
        List<String[]> devices = new ArrayList<String[]>();
        List<String> statuses = new ArrayList<String>(), types = new ArrayList<String>();
        try (Connection connection = DBConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT d.DEVICE_ID,d.DEVICE_NAME,d.USER_ID,d.DEVICE_TYPE,d.STATUS,u.USER_NAME FROM DEVICES d JOIN USERS u ON d.USER_ID=u.USER_ID WHERE d.USER_ID=?");
            List<String> values = new ArrayList<String>(); values.add(userId);
            if (WebSupport.hasText(search)) { sql.append(" AND UPPER(d.DEVICE_NAME) LIKE UPPER(?)"); values.add("%" + search + "%"); }
            if (WebSupport.hasText(status)) { sql.append(" AND d.STATUS=?"); values.add(status); }
            if (WebSupport.hasText(type)) { sql.append(" AND d.DEVICE_TYPE=?"); values.add(type); }
            sql.append(" ORDER BY ").append("name".equals(sort) ? "d.DEVICE_NAME" : "status".equals(sort) ? "d.STATUS" : "type".equals(sort) ? "d.DEVICE_TYPE" : "d.DEVICE_ID");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < values.size(); i++) statement.setString(i + 1, values.get(i));
                try (ResultSet result = statement.executeQuery()) { while (result.next()) devices.add(new String[] { result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5), result.getString(6) }); }
            }
            readChoices(connection, "SELECT DISTINCT STATUS FROM DEVICES WHERE USER_ID=? AND STATUS IS NOT NULL ORDER BY STATUS", userId, statuses);
            readChoices(connection, "SELECT DISTINCT DEVICE_TYPE FROM DEVICES WHERE USER_ID=? AND DEVICE_TYPE IS NOT NULL ORDER BY DEVICE_TYPE", userId, types);
        } catch (SQLException e) { getServletContext().log("Failed to load devices", e); request.setAttribute("dbError", "Could not load your devices. Check the server log."); }
        request.setAttribute("devices", devices); request.setAttribute("statuses", statuses); request.setAttribute("types", types);
        request.setAttribute("search", search); request.setAttribute("selectedStatus", status); request.setAttribute("selectedType", type); request.setAttribute("sort", sort);
        request.setAttribute("activePage", "devices"); request.setAttribute("csrfToken", WebSupport.csrf(request)); WebSupport.exposeFlash(request);
        request.getRequestDispatcher("/devices.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebSupport.validCsrf(request)) { response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token."); return; }
        try (Connection connection = DBConnection.getConnection()) {
            String action = WebSupport.text(request, "action", 10);
            if ("create".equals(action)) create(request, connection); else if ("update".equals(action)) update(request, connection); else if ("delete".equals(action)) delete(request, connection); else WebSupport.flash(request, "error", "Unknown device action.");
        } catch (SQLException e) { getServletContext().log("Device update failed", e); WebSupport.flash(request, "error", "The device could not be saved. No changes were applied."); }
        response.sendRedirect(request.getContextPath() + "/devices");
    }

    private void create(HttpServletRequest request, Connection connection) throws SQLException {
        String name = WebSupport.text(request, "deviceName", 50), type = WebSupport.text(request, "deviceType", 30), status = WebSupport.text(request, "status", 20);
        String userId = OwnershipSupport.currentUserId(request);
        if (!valid(name, status)) { WebSupport.flash(request, "error", "A device name and valid status are required."); return; }
        String id = DatabaseSupport.nextId(connection, "DEVICES", "DEVICE_ID", "D");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO DEVICES (DEVICE_ID,DEVICE_NAME,USER_ID,DEVICE_TYPE,STATUS) VALUES (?,?,?,?,?)")) {
            statement.setString(1, id); statement.setString(2, name); statement.setString(3, userId); statement.setString(4, nullable(type)); statement.setString(5, status); statement.executeUpdate();
        }
        WebSupport.flash(request, "success", "Device " + id + " was added to your inventory.");
    }

    private void update(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "deviceId", 10), name = WebSupport.text(request, "deviceName", 50), type = WebSupport.text(request, "deviceType", 30), status = WebSupport.text(request, "status", 20);
        String userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsDevice(connection, userId, id)) { WebSupport.flash(request, "error", "That device is unavailable."); return; }
        if (!valid(name, status)) { WebSupport.flash(request, "error", "A device name and valid status are required."); return; }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE DEVICES SET DEVICE_NAME=?,DEVICE_TYPE=?,STATUS=? WHERE DEVICE_ID=? AND USER_ID=?")) {
            statement.setString(1, name); statement.setString(2, nullable(type)); statement.setString(3, status); statement.setString(4, id); statement.setString(5, userId); statement.executeUpdate();
        }
        WebSupport.flash(request, "success", "Device " + id + " was updated.");
    }

    private void delete(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "deviceId", 10), userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsDevice(connection, userId, id)) { WebSupport.flash(request, "error", "That device is unavailable."); return; }
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            int sensors = countForDevice(connection,
                    "SELECT COUNT(*) FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE s.DEVICE_ID=? AND d.USER_ID=?", id, userId);
            int readings = countForDevice(connection,
                    "SELECT COUNT(*) FROM SENSOR_READINGS r JOIN SENSORS s ON r.SENSOR_ID=s.SENSOR_ID JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE s.DEVICE_ID=? AND d.USER_ID=?", id, userId);

            deleteForDevice(connection,
                    "DELETE FROM SENSOR_READINGS WHERE SENSOR_ID IN (SELECT s.SENSOR_ID FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE s.DEVICE_ID=? AND d.USER_ID=?)", id, userId);
            deleteForDevice(connection,
                    "DELETE FROM SENSORS WHERE SENSOR_ID IN (SELECT s.SENSOR_ID FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE s.DEVICE_ID=? AND d.USER_ID=?)", id, userId);
            if (deleteForDevice(connection, "DELETE FROM DEVICES WHERE DEVICE_ID=? AND USER_ID=?", id, userId) != 1) {
                throw new SQLException("Device was no longer available for deletion.");
            }
            connection.commit();
            WebSupport.flash(request, "success", "Device " + id + " and " + sensors + " sensor(s), and " + readings + " reading(s) were deleted.");
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
            throw e;
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) { }
        }
    }

    private boolean valid(String name, String status) { return WebSupport.hasText(name) && ("ACTIVE".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status)); }
    private String nullable(String value) { return WebSupport.hasText(value) ? value : null; }
    private int countForDevice(Connection connection, String sql, String deviceId, String userId) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.setString(1, deviceId); statement.setString(2, userId); try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; } } }
    private int deleteForDevice(Connection connection, String sql, String deviceId, String userId) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.setString(1, deviceId); statement.setString(2, userId); return statement.executeUpdate(); } }
    private void readChoices(Connection connection, String sql, String userId, List<String> out) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.setString(1, userId); try (ResultSet result = statement.executeQuery()) { while (result.next()) out.add(result.getString(1)); } } }
}
