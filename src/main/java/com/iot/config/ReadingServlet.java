package com.iot.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/readings")
public class ReadingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String userId = OwnershipSupport.currentUserId(request), sensor = WebSupport.text(request, "sensor", 10), from = WebSupport.text(request, "from", 10), to = WebSupport.text(request, "to", 10);
        List<String[]> readings = new ArrayList<String[]>(), sensors = new ArrayList<String[]>();
        try (Connection connection = DBConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT r.READING_ID,r.SENSOR_ID,s.SENSOR_NAME,d.DEVICE_NAME,r.READING_VALUE,s.UNIT,TO_CHAR(r.READING_TIME,'YYYY-MM-DD HH24:MI:SS'),TO_CHAR(r.READING_TIME,'YYYY-MM-DD\"T\"HH24:MI') FROM SENSOR_READINGS r JOIN SENSORS s ON r.SENSOR_ID=s.SENSOR_ID JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE d.USER_ID=?");
            List<Object> values = new ArrayList<Object>(); values.add(userId);
            if (WebSupport.hasText(sensor)) { sql.append(" AND r.SENSOR_ID=?"); values.add(sensor); }
            if (validDate(from)) { sql.append(" AND r.READING_TIME>=?"); values.add(Date.valueOf(from)); }
            if (validDate(to)) { sql.append(" AND r.READING_TIME<? + 1"); values.add(Date.valueOf(to)); }
            sql.append(" ORDER BY r.READING_TIME DESC");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) { for (int i = 0; i < values.size(); i++) { if (values.get(i) instanceof Date) statement.setDate(i + 1, (Date) values.get(i)); else statement.setString(i + 1, (String) values.get(i)); } try (ResultSet result = statement.executeQuery()) { while (result.next()) readings.add(new String[] { result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5), result.getString(6), result.getString(7), result.getString(8) }); } }
            try (PreparedStatement statement = connection.prepareStatement("SELECT s.SENSOR_ID,s.SENSOR_NAME FROM SENSORS s JOIN DEVICES d ON s.DEVICE_ID=d.DEVICE_ID WHERE d.USER_ID=? ORDER BY s.SENSOR_NAME")) { statement.setString(1, userId); try (ResultSet result = statement.executeQuery()) { while (result.next()) sensors.add(new String[] { result.getString(1), result.getString(2) }); } }
        } catch (SQLException e) { getServletContext().log("Failed to load readings", e); request.setAttribute("dbError", "Could not load your historical readings. Check the server log."); }
        request.setAttribute("readings", readings); request.setAttribute("sensors", sensors); request.setAttribute("selectedSensor", sensor); request.setAttribute("from", from); request.setAttribute("to", to); request.setAttribute("activePage", "readings"); request.setAttribute("csrfToken", WebSupport.csrf(request)); WebSupport.exposeFlash(request);
        request.getRequestDispatcher("/readings.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebSupport.validCsrf(request)) { response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token."); return; }
        try (Connection connection = DBConnection.getConnection()) { String action = WebSupport.text(request, "action", 10); if ("create".equals(action)) create(request, connection); else if ("update".equals(action)) update(request, connection); else if ("delete".equals(action)) delete(request, connection); else WebSupport.flash(request, "error", "Unknown reading action."); }
        catch (SQLException e) { getServletContext().log("Reading update failed", e); WebSupport.flash(request, "error", "The reading could not be saved. No changes were applied."); }
        response.sendRedirect(request.getContextPath() + "/readings");
    }

    private void create(HttpServletRequest request, Connection connection) throws SQLException {
        ReadingInput input = input(request, connection); if (input == null) return;
        String id = DatabaseSupport.nextId(connection, "SENSOR_READINGS", "READING_ID", "R");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO SENSOR_READINGS (READING_ID,SENSOR_ID,READING_VALUE,READING_TIME) VALUES (?,?,?,?)")) { statement.setString(1, id); statement.setString(2, input.sensorId); statement.setBigDecimal(3, input.value); statement.setTimestamp(4, input.recordedAt); statement.executeUpdate(); }
        WebSupport.flash(request, "success", "Reading " + id + " was added.");
    }

    private void update(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "readingId", 20), userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsReading(connection, userId, id)) { WebSupport.flash(request, "error", "That reading is unavailable."); return; }
        ReadingInput input = input(request, connection); if (input == null) return;
        try (PreparedStatement statement = connection.prepareStatement("UPDATE SENSOR_READINGS SET SENSOR_ID=?,READING_VALUE=?,READING_TIME=? WHERE READING_ID=?")) { statement.setString(1, input.sensorId); statement.setBigDecimal(2, input.value); statement.setTimestamp(3, input.recordedAt); statement.setString(4, id); statement.executeUpdate(); }
        WebSupport.flash(request, "success", "Reading " + id + " was updated.");
    }

    private void delete(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "readingId", 20), userId = OwnershipSupport.currentUserId(request);
        if (!OwnershipSupport.ownsReading(connection, userId, id)) { WebSupport.flash(request, "error", "That reading is unavailable."); return; }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM SENSOR_READINGS WHERE READING_ID=?")) { statement.setString(1, id); statement.executeUpdate(); }
        WebSupport.flash(request, "success", "Reading " + id + " was deleted.");
    }

    private ReadingInput input(HttpServletRequest request, Connection connection) throws SQLException {
        String sensorId = WebSupport.text(request, "sensorId", 10), value = WebSupport.text(request, "readingValue", 40); Timestamp recordedAt = timestamp(WebSupport.text(request, "readingTime", 16)); BigDecimal parsedValue;
        try { parsedValue = new BigDecimal(value); } catch (NumberFormatException e) { parsedValue = null; }
        if (!OwnershipSupport.ownsSensor(connection, OwnershipSupport.currentUserId(request), sensorId) || parsedValue == null || parsedValue.precision() > 20 || parsedValue.scale() > 10 || recordedAt == null) { WebSupport.flash(request, "error", "Select one of your sensors and enter a valid numeric value and recorded date/time."); return null; }
        return new ReadingInput(sensorId, parsedValue, recordedAt);
    }

    private Timestamp timestamp(String value) { if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) return null; try { return Timestamp.valueOf(value.replace('T', ' ') + ":00"); } catch (IllegalArgumentException e) { return null; } }
    private boolean validDate(String value) { if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) return false; try { SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd"); format.setLenient(false); format.parse(value); return true; } catch (Exception e) { return false; } }
    private static final class ReadingInput { final String sensorId; final BigDecimal value; final Timestamp recordedAt; ReadingInput(String sensorId, BigDecimal value, Timestamp recordedAt) { this.sensorId = sensorId; this.value = value; this.recordedAt = recordedAt; } }
}
