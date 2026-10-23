package com.iot.config;

import java.security.SecureRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

final class WebSupport {
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebSupport() { }

    static String text(HttpServletRequest request, String name, int maxLength) {
        String value = request.getParameter(name);
        if (value == null) return "";
        value = value.trim();
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String csrf(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute("csrfToken");
        if (token == null) {
            byte[] bytes = new byte[24];
            RANDOM.nextBytes(bytes);
            StringBuilder builder = new StringBuilder(48);
            for (byte b : bytes) builder.append(String.format("%02x", b & 0xff));
            token = builder.toString();
            session.setAttribute("csrfToken", token);
        }
        return token;
    }

    static boolean validCsrf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        String expected = (String) session.getAttribute("csrfToken");
        String supplied = request.getParameter("csrfToken");
        return expected != null && expected.equals(supplied);
    }

    static void rotateCsrf(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(48);
        for (byte b : bytes) builder.append(String.format("%02x", b & 0xff));
        session.setAttribute("csrfToken", builder.toString());
    }

    static void flash(HttpServletRequest request, String type, String message) {
        request.getSession(true).setAttribute("flashType", type);
        request.getSession(true).setAttribute("flashMessage", message);
    }

    static void exposeFlash(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        request.setAttribute("flashType", session.getAttribute("flashType"));
        request.setAttribute("flashMessage", session.getAttribute("flashMessage"));
        session.removeAttribute("flashType");
        session.removeAttribute("flashMessage");
    }
}
