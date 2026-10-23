package com.iot.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;

/** Shared authentication and account-validation helpers. */
final class AuthSupport {
    static final String USER_ID = "authenticatedUserId";
    static final String USER_NAME = "authenticatedUserName";

    private AuthSupport() { }

    static String authenticate(Connection connection, String identifier, String password) throws SQLException {
        if (!WebSupport.hasText(identifier) || password == null) return null;

        String sql = "SELECT USER_ID, USER_NAME, PASSWORD_HASH FROM USERS "
                + "WHERE UPPER(USER_NAME)=UPPER(?) OR UPPER(EMAIL)=UPPER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identifier);
            statement.setString(2, identifier);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                String hash = result.getString("PASSWORD_HASH");
                if (!validPassword(password, hash)) return null;
                return result.getString("USER_ID") + "\n" + result.getString("USER_NAME");
            }
        }
    }

    static boolean usernameExists(Connection connection, String username, String exceptId) throws SQLException {
        return valueExists(connection, "USER_NAME", username, exceptId);
    }

    static boolean emailExists(Connection connection, String email, String exceptId) throws SQLException {
        return WebSupport.hasText(email) && valueExists(connection, "EMAIL", email, exceptId);
    }

    static boolean validUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9._-]{3,50}");
    }

    static boolean validEmail(String email) {
        return WebSupport.hasText(email) && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    static boolean validNewPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 72;
    }

    static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    static void signIn(HttpServletRequest request, String userId, String userName) {
        request.changeSessionId();
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID, userId);
        session.setAttribute(USER_NAME, userName);
        WebSupport.rotateCsrf(request);
    }

    static boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(USER_ID) != null;
    }

    static String postLoginTarget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String target = session == null ? null : (String) session.getAttribute("authRedirect");
        if (session != null) session.removeAttribute("authRedirect");
        return target != null && target.startsWith(request.getContextPath() + "/") ? target : request.getContextPath() + "/dashboard";
    }

    private static boolean validPassword(String password, String hash) {
        if (hash == null || !hash.startsWith("$2")) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean valueExists(Connection connection, String column, String value, String exceptId) throws SQLException {
        String sql = "SELECT 1 FROM USERS WHERE UPPER(" + column + ")=UPPER(?)"
                + (exceptId == null ? "" : " AND USER_ID<>?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            if (exceptId != null) statement.setString(2, exceptId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
