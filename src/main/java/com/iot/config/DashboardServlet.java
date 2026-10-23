
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

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        int deviceCount = 0;
        int sensorCount = 0;
        int readingCount = 0;
        int onlineCount = 0;
        int offlineCount = 0;
        String userId = OwnershipSupport.currentUserId(request);

        List<String[]> devices = new ArrayList<String[]>();

        try (Connection con = DBConnection.getConnection()) {

            deviceCount = getCount(con, "SELECT COUNT(*) FROM DEVICES WHERE USER_ID=?", userId);
            sensorCount = getCount(con, "SELECT COUNT(*) FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?)", userId);
            readingCount = getCount(con, "SELECT COUNT(*) FROM SENSOR_READINGS WHERE SENSOR_ID IN (SELECT SENSOR_ID FROM SENSORS WHERE DEVICE_ID IN (SELECT DEVICE_ID FROM DEVICES WHERE USER_ID=?))", userId);
            onlineCount = getCount(con, "SELECT COUNT(*) FROM DEVICES WHERE USER_ID=? AND UPPER(STATUS) IN ('ACTIVE', 'ONLINE')", userId);
            offlineCount = getCount(con, "SELECT COUNT(*) FROM DEVICES WHERE USER_ID=? AND UPPER(STATUS) IN ('INACTIVE', 'OFFLINE')", userId);

            String sql =
                "SELECT DEVICE_ID, DEVICE_NAME, DEVICE_TYPE, STATUS " +
                "FROM DEVICES WHERE USER_ID=? ORDER BY DEVICE_ID";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    devices.add(new String[] {
                        rs.getString("DEVICE_ID"),
                        rs.getString("DEVICE_NAME"),
                        rs.getString("DEVICE_TYPE"),
                        rs.getString("STATUS")
                    });
                }
                }
            }

        } catch (SQLException e) {
            getServletContext().log("Dashboard database error", e);
            request.setAttribute(
                "dbError",
                "Could not retrieve dashboard data. Check the database connection."
            );
        }

        request.setAttribute("deviceCount", deviceCount);
        request.setAttribute("sensorCount", sensorCount);
        request.setAttribute("readingCount", readingCount);
        request.setAttribute("onlineCount", onlineCount);
        request.setAttribute("offlineCount", offlineCount);
        request.setAttribute("devices", devices);
        request.setAttribute("activePage", "dashboard");
        WebSupport.exposeFlash(request);
        request.setAttribute("csrfToken", WebSupport.csrf(request));

        request.getRequestDispatcher("/index.jsp")
               .forward(request, response);
    }

    private int getCount(Connection con, String sql, String userId)
            throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            }
        }
    }
}
