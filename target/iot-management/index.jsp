<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%
String cp = request.getContextPath();

Integer devices = (Integer) request.getAttribute("deviceCount");
Integer sensors = (Integer) request.getAttribute("sensorCount");
Integer readings = (Integer) request.getAttribute("readingCount");
Integer online = (Integer) request.getAttribute("onlineCount");
Integer offline = (Integer) request.getAttribute("offlineCount");

if (devices == null) devices = 0;
if (sensors == null) sensors = 0;
if (readings == null) readings = 0;
if (online == null) online = 0;
if (offline == null) offline = 0;

List<String[]> rows = (List<String[]>) request.getAttribute("devices");
String error = (String) request.getAttribute("dbError");
String flash = (String) request.getAttribute("flashMessage");
String flashType = (String) request.getAttribute("flashType");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PulseGrid · Dashboard</title>
    <link rel="stylesheet" href="<%= cp %>/assets/styles.css">
</head>
<body>
<div class="layout">
    <aside class="sidebar">
        <div class="brand">
            <span class="brand-mark">⌁</span> PulseGrid
        </div>

        <%@ include file="/WEB-INF/jspf/navigation.jspf" %>
    </aside>

    <main class="main">
        <div class="page-head">
            <div>
                <button class="button ghost nav-toggle" data-nav-toggle>Menu</button>
                <div class="eyebrow">Operations overview</div>
                <h1>Connected, at a glance.</h1>
                <p>Database-backed inventory and stored telemetry history.</p>
            </div>

            <div class="quick-links">
                <a class="button" href="<%= cp %>/devices">Manage devices</a>
                <a class="button ghost" href="<%= cp %>/readings">View readings</a>
            </div>
        </div>

        <% if (flash != null) { %>
            <div class="alert <%= StringEscapeUtils.escapeHtml4(flashType == null ? "" : flashType) %>">
                <%= StringEscapeUtils.escapeHtml4(flash) %>
            </div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert error">
                <%= StringEscapeUtils.escapeHtml4(error) %>
            </div>
        <% } %>

        <section class="cards">
            <div class="card">
                <div class="label">Devices</div>
                <div class="number accent-cyan"><%= devices %></div>
            </div>

            <div class="card">
                <div class="label">Sensors</div>
                <div class="number accent-violet"><%= sensors %></div>
            </div>

            <div class="card">
                <div class="label">Stored readings</div>
                <div class="number accent-green"><%= readings %></div>
            </div>

        </section>

        <section class="cards" style="margin-top:16px">
            <div class="card">
                <div class="label">Active / online</div>
                <div class="number accent-green"><%= online %></div>
            </div>

            <div class="card">
                <div class="label">Inactive / offline</div>
                <div class="number accent-amber"><%= offline %></div>
            </div>

            <div class="card">
                <div class="label">Data source</div>
                <div class="number" style="font-size:18px">Oracle XE</div>
                <p class="reading-note">Historical, not live telemetry.</p>
            </div>
        </section>

        <section class="panel">
            <div class="panel-title">
                <h2>Device status overview</h2>
                <a class="text-button" href="<%= cp %>/devices">Open inventory →</a>
            </div>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Device</th>
                            <th>Type</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (rows != null && !rows.isEmpty()) {
                            for (String[] d : rows) { %>
                                <tr>
                                    <td><%= StringEscapeUtils.escapeHtml4(d[0]) %></td>
                                    <td><%= StringEscapeUtils.escapeHtml4(d[1]) %></td>
                                    <td><%= StringEscapeUtils.escapeHtml4(d[2] == null ? "—" : d[2]) %></td>
                                    <td>
                                        <span class="badge <%= d[3] != null && (d[3].equalsIgnoreCase("ACTIVE") || d[3].equalsIgnoreCase("ONLINE")) ? "" : "warn" %>">
                                            <%= StringEscapeUtils.escapeHtml4(d[3] == null ? "Unknown" : d[3]) %>
                                        </span>
                                    </td>
                                </tr>
                            <% }
                        } else { %>
                            <tr>
                                <td colspan="4" class="empty">No devices are available.</td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>

<script src="<%= cp %>/assets/app.js"></script>
</body>
</html>
