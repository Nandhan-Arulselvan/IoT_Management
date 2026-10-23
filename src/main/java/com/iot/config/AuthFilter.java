package com.iot.config;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(javax.servlet.ServletRequest servletRequest,
                         javax.servlet.ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (AuthSupport.isAuthenticated(request)) {
            chain.doFilter(request, response);
            return;
        }

        String query = request.getQueryString();
        request.getSession(true).setAttribute("authRedirect", request.getRequestURI() + (query == null ? "" : "?" + query));
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private boolean isPublic(String path) {
        return path.equals("/login") || path.equals("/register") || path.equals("/logout")
                || path.startsWith("/assets/");
    }
}
