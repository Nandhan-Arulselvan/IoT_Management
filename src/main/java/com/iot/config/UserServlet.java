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

@WebServlet("/users")
public class UserServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<String[]> users = new ArrayList<String[]>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT USER_ID, USER_NAME, EMAIL, LOCATION FROM USERS ORDER BY USER_ID");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(new String[] { result.getString(1), result.getString(2), result.getString(3), result.getString(4) });
            }
        } catch (SQLException e) {
            getServletContext().log("Failed to load users", e);
            request.setAttribute("dbError", "Could not load users. Check the server log.");
        }
        request.setAttribute("users", users);
        request.setAttribute("activePage", "users");
        request.setAttribute("csrfToken", WebSupport.csrf(request));
        WebSupport.exposeFlash(request);
        request.getRequestDispatcher("/users.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!WebSupport.validCsrf(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token.");
            return;
        }
        String action = WebSupport.text(request, "action", 10);
        try (Connection connection = DBConnection.getConnection()) {
            if ("create".equals(action)) {
                create(request, connection);
            } else if ("update".equals(action)) {
                update(request, connection);
            } else if ("delete".equals(action)) {
                delete(request, connection);
            } else {
                WebSupport.flash(request, "error", "Unknown user action.");
            }
        } catch (SQLException e) {
            getServletContext().log("User update failed", e);
            WebSupport.flash(request, "error", "The user could not be saved. No changes were applied.");
        }
        response.sendRedirect(request.getContextPath() + "/users");
    }

    private void create(HttpServletRequest request, Connection connection) throws SQLException {
        String name = WebSupport.text(request, "userName", 50);
        String email = WebSupport.text(request, "email", 100);
        String location = WebSupport.text(request, "location", 50);
        String password = request.getParameter("password");
        if (!validProfile(name, email) || !validNewPassword(password)) {
            WebSupport.flash(request, "error", "Enter a unique username, a valid email when supplied, and a password of at least 8 characters.");
            return;
        }
        if (userNameExists(connection, name, null) || emailExists(connection, email, null)) {
            WebSupport.flash(request, "error", "That username or email is already in use.");
            return;
        }
        String id = DatabaseSupport.nextId(connection, "USERS", "USER_ID", "U");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO USERS (USER_ID, USER_NAME, PASSWORD_HASH, EMAIL, LOCATION) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, AuthSupport.hashPassword(password));
            statement.setString(4, nullable(email));
            statement.setString(5, nullable(location));
            statement.executeUpdate();
        }
        WebSupport.flash(request, "success", "User " + id + " was added.");
    }

    private void update(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "userId", 10);
        String name = WebSupport.text(request, "userName", 50);
        String email = WebSupport.text(request, "email", 100);
        String location = WebSupport.text(request, "location", 50);
        String password = request.getParameter("password");
        if (!DatabaseSupport.exists(connection, "USERS", "USER_ID", id)) {
            WebSupport.flash(request, "error", "The selected user no longer exists.");
            return;
        }
        if (!validProfile(name, email) || (WebSupport.hasText(password) && !validNewPassword(password))) {
            WebSupport.flash(request, "error", "Enter a unique username, a valid email when supplied, and an optional new password of at least 8 characters.");
            return;
        }
        if (userNameExists(connection, name, id) || emailExists(connection, email, id)) {
            WebSupport.flash(request, "error", "That username or email is already in use.");
            return;
        }
        if (WebSupport.hasText(password)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE USERS SET USER_NAME=?, PASSWORD_HASH=?, EMAIL=?, LOCATION=? WHERE USER_ID=?")) {
                statement.setString(1, name); statement.setString(2, AuthSupport.hashPassword(password));
                statement.setString(3, nullable(email)); statement.setString(4, nullable(location)); statement.setString(5, id);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE USERS SET USER_NAME=?, EMAIL=?, LOCATION=? WHERE USER_ID=?")) {
                statement.setString(1, name); statement.setString(2, nullable(email));
                statement.setString(3, nullable(location)); statement.setString(4, id); statement.executeUpdate();
            }
        }
        WebSupport.flash(request, "success", "User " + id + " was updated.");
    }

    private void delete(HttpServletRequest request, Connection connection) throws SQLException {
        String id = WebSupport.text(request, "userId", 10);
        if (!DatabaseSupport.exists(connection, "USERS", "USER_ID", id)) {
            WebSupport.flash(request, "error", "The selected user no longer exists.");
            return;
        }
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            int devices = count(connection, "SELECT COUNT(*) FROM DEVICES WHERE USER_ID=?", id);
            int sensors = count(connection, "SELECT COUNT(*) FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?)", id);
            int readings = count(connection, "SELECT COUNT(*) FROM SENSOR_READINGS WHERE SENSOR_ID IN (SELECT SENSOR_ID FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?))", id);

            executeDelete(connection, "DELETE FROM SENSOR_READINGS WHERE SENSOR_ID IN (SELECT SENSOR_ID FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?))", id);
            executeDelete(connection, "DELETE FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?)", id);
            executeDelete(connection, "DELETE FROM DEVICES WHERE USER_ID=?", id);
            executeDelete(connection, "DELETE FROM USERS WHERE USER_ID=?", id);
            connection.commit();
            WebSupport.flash(request, "success", "User " + id + " and " + devices + " device(s), " + sensors + " sensor(s), and " + readings + " reading(s) were deleted.");
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
            throw e;
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) { }
        }
    }

    private int count(Connection connection, String sql, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private void executeDelete(Connection connection, String sql, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private boolean userNameExists(Connection connection, String name, String exceptId) throws SQLException {
        return AuthSupport.usernameExists(connection, name, exceptId);
    }

    private boolean emailExists(Connection connection, String email, String exceptId) throws SQLException {
        return AuthSupport.emailExists(connection, email, exceptId);
    }

    private boolean validProfile(String name, String email) {
        return WebSupport.hasText(name) && (!WebSupport.hasText(email) || email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"));
    }

    private boolean validNewPassword(String password) { return AuthSupport.validNewPassword(password); }

    private String nullable(String value) { return WebSupport.hasText(value) ? value : null; }
}
