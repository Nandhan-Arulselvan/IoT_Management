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

@WebServlet("/sensors")
public class SensorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String userId = OwnershipSupport.currentUserId(request), search = WebSupport.text(request, "search", 50), device = WebSupport.text(request, "device", 10);
        List<String[]> sensors = new ArrayList<String[]>(), devices = new ArrayList<String[]>();
        try (Connection connection = DBConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT s.SENSOR_ID,s.SENSOR_NAME,s.DEVICE_ID,s.SENSOR_TYPE,s.UNIT,d.DEVICE_NAME FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE d.USER_ID=?");
            List<String> values = new ArrayList<String>(); values.add(userId);
            if (WebSupport.hasText(search)) { sql.append(" AND (UPPER(s.SENSOR_NAME) LIKE UPPER(?) OR UPPER(s.SENSOR_TYPE) LIKE UPPER(?))"); values.add("%" + search + "%"); values.add("%" + search + "%"); }
            if (WebSupport.hasText(device)) { sql.append(" AND s.DEVICE_ID=?"); values.add(device); }
            sql.append(" ORDER BY s.SENSOR_ID");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) { for (int i = 0; i < values.size(); i++) statement.setString(i + 1, values.get(i)); try (ResultSet result = statement.executeQuery()) { while (result.next()) sensors.add(new String[] { result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5), result.getString(6) }); } }
            try (PreparedStatement statement = connection.prepareStatement("SELECT DEVICE_ID,DEVICE_NAME FROM DEVICES WHERE USER_ID=? ORDER BY DEVICE_NAME")) { statement.setString(1, userId); try (ResultSet result = statement.executeQuery()) { while (result.next()) devices.add(new String[] { result.getString(1), result.getString(2) }); } }
        } catch (SQLException e) { getServletContext().log("Failed to load sensors", e); request.setAttribute("dbError", "Could not load your sensors. Check the server log."); }
        request.setAttribute("sensors", sensors); request.setAttribute("devices", devices); request.setAttribute("search", search); request.setAttribute("selectedDevice", device); request.setAttribute("activePage", "sensors"); request.setAttribute("csrfToken", WebSupport.csrf(request)); WebSupport.exposeFlash(request);
        request.getRequestDispatcher("/sensors.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebSupport.validCsrf(request)) { response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token."); return; }
        try (Connection connection = DBConnection.getConnection()) { String action = WebSupport.text(request, "action", 10); if ("create".equals(action)) create(request, connection); else if ("update".equals(action)) update(request, connection); else if ("delete".equals(action)) delete(request, connection); else WebSupport.flash(request, "error", "Unknown sensor action."); }
        catch (SQLException e) { getServletContext().log("Sensor update failed", e); WebSupport.flash(request, "error", "The sensor could not be saved. No changes were applied."); }
        response.sendRedirect(request.getContextPath() + "/sensors");
    }

    private void create(HttpServletRequest request, Connection connection) throws SQLException {
        String name = WebSupport.text(request, "sensorName", 50), device = WebSupport.text(request, "deviceId", 10), type = WebSupport.text(request, "sensorType", 30), unit = WebSupport.text(request, "unit", 20);
        if (!valid(connection, OwnershipSupport.currentUserId(request), name, device, type)) { WebSupport.flash(request, "error", "Choose one of your devices and enter a sensor name and type."); return; }
        String id = DatabaseSupport.nextId(connection, "SENSORS", "SENSOR_ID", "S");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO SENSORS (SENSOR_ID,SENSOR_NAME,DEVICE_ID,SENSOR_TYPE,UNIT) VALUES (?,?,?,?,?)")) { statement.setString(1, id); statement.setString(2, name); statement.setString(3, device); statement.setString(4, type); statement.setString(5, nullable(unit)); statement.executeUpdate(); }
        WebSupport.flash(request, "success", "Sensor " + id + " was added.");
    }

    private void update(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "sensorId", 10), name = WebSupport.text(request, "sensorName", 50), device = WebSupport.text(request, "deviceId", 10), type = WebSupport.text(request, "sensorType", 30), unit = WebSupport.text(request, "unit", 20), userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsSensor(connection, userId, id)) { WebSupport.flash(request, "error", "That sensor is unavailable."); return; }
        if (!valid(connection, userId, name, device, type)) { WebSupport.flash(request, "error", "Choose one of your devices and enter a sensor name and type."); return; }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE SENSORS SET SENSOR_NAME=?,DEVICE_ID=?,SENSOR_TYPE=?,UNIT=? WHERE SENSOR_ID=?")) { statement.setString(1, name); statement.setString(2, device); statement.setString(3, type); statement.setString(4, nullable(unit)); statement.setString(5, id); statement.executeUpdate(); }
        WebSupport.flash(request, "success", "Sensor " + id + " was updated.");
    }

    private void delete(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "sensorId", 10), userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsSensor(connection, userId, id)) { WebSupport.flash(request, "error", "That sensor is unavailable."); return; }
        boolean autoCommit = connection.getAutoCommit();
        try { connection.setAutoCommit(false); int readings = DatabaseSupport.count(connection, "SELECT COUNT(*) FROM SENSOR_READINGS WHERE SENSOR_ID=?", id); try (PreparedStatement statement = connection.prepareStatement("DELETE FROM SENSOR_READINGS WHERE SENSOR_ID=?")) { statement.setString(1, id); statement.executeUpdate(); } try (PreparedStatement statement = connection.prepareStatement("DELETE FROM SENSORS WHERE SENSOR_ID=?")) { statement.setString(1, id); statement.executeUpdate(); } connection.commit(); WebSupport.flash(request, "success", "Sensor " + id + " and " + readings + " historical reading(s) were deleted."); }
        catch (SQLException e) { try { connection.rollback(); } catch (SQLException ignored) { } throw e; }
        finally { try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) { } }
    }

    private boolean valid(Connection connection, String userId, String name, String deviceId, String type) throws SQLException { return WebSupport.hasText(name) && WebSupport.hasText(type) && OwnershipSupport.ownsDevice(connection, userId, deviceId); }
    private String nullable(String value) { return WebSupport.hasText(value) ? value : null; }
}
