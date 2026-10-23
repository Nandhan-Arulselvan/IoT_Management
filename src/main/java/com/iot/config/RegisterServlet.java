package com.iot.config;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (AuthSupport.isAuthenticated(request)) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        request.setAttribute("csrfToken", WebSupport.csrf(request));
        WebSupport.exposeFlash(request);
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebSupport.validCsrf(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token.");
            return;
        }
        String username = WebSupport.text(request, "username", 50);
        String email = WebSupport.text(request, "email", 100);
        String location = WebSupport.text(request, "location", 50);
        String password = request.getParameter("password");
        String confirmation = request.getParameter("confirmPassword");

        if (!AuthSupport.validUsername(username) || !AuthSupport.validEmail(email)
                || !AuthSupport.validNewPassword(password) || !password.equals(confirmation)) {
            WebSupport.flash(request, "error", "Use a 3–50 character username, a valid email, and matching passwords of 8–72 characters.");
            response.sendRedirect(request.getContextPath() + "/register");
            return;
        }

        try (Connection connection = DBConnection.getConnection()) {
            if (AuthSupport.usernameExists(connection, username, null) || AuthSupport.emailExists(connection, email, null)) {
                WebSupport.flash(request, "error", "That username or email is already in use.");
                response.sendRedirect(request.getContextPath() + "/register");
                return;
            }
            String id = DatabaseSupport.nextId(connection, "USERS", "USER_ID", "U");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO USERS (USER_ID, USER_NAME, PASSWORD_HASH, EMAIL, LOCATION) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, username);
                statement.setString(3, AuthSupport.hashPassword(password));
                statement.setString(4, email);
                statement.setString(5, WebSupport.hasText(location) ? location : null);
                statement.executeUpdate();
            }
            WebSupport.flash(request, "success", "Your account has been created. Please sign in.");
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (SQLException e) {
            getServletContext().log("Registration database error", e);
            WebSupport.flash(request, "error", "We could not create the account. Please try again.");
            response.sendRedirect(request.getContextPath() + "/register");
        }
    }
}
