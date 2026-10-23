package com.iot.config;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
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
        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebSupport.validCsrf(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token.");
            return;
        }
        String identifier = WebSupport.text(request, "identifier", 100);
        String password = request.getParameter("password");
        try (Connection connection = DBConnection.getConnection()) {
            String account = AuthSupport.authenticate(connection, identifier, password);
            if (account == null) {
                WebSupport.flash(request, "error", "Invalid username/email or password.");
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
            String[] accountParts = account.split("\\n", 2);
            AuthSupport.signIn(request, accountParts[0], accountParts[1]);
            response.sendRedirect(AuthSupport.postLoginTarget(request));
        } catch (SQLException e) {
            getServletContext().log("Login database error", e);
            WebSupport.flash(request, "error", "Sign-in is temporarily unavailable. Please try again.");
            response.sendRedirect(request.getContextPath() + "/login");
        }
    }
}
