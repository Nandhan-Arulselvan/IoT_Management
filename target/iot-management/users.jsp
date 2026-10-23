<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%
String cp = request.getContextPath();
String token = (String) request.getAttribute("csrfToken");
String error = (String) request.getAttribute("dbError");
String flash = (String) request.getAttribute("flashMessage");
String flashType = (String) request.getAttribute("flashType");
List<String[]> users = (List<String[]>) request.getAttribute("users");
%>
<!doctype html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>PulseGrid · Users</title><link rel="stylesheet" href="<%= cp %>/assets/styles.css"></head>
<body><div class="layout"><aside class="sidebar"><div class="brand"><span class="brand-mark">⌁</span> PulseGrid</div>
<%@ include file="/WEB-INF/jspf/navigation.jspf" %></aside>
<main class="main"><div class="page-head"><div><button class="button ghost nav-toggle" data-nav-toggle>Menu</button><div class="eyebrow">Access directory</div><h1>User management</h1><p>Create and maintain people who own IoT devices.</p></div></div>
<% if (flash != null) { %><div class="alert <%= StringEscapeUtils.escapeHtml4(flashType) %>"><%= StringEscapeUtils.escapeHtml4(flash) %></div><% } %>
<% if (error != null) { %><div class="alert error"><%= StringEscapeUtils.escapeHtml4(error) %></div><% } %>
<section class="form-card"><div class="panel-title"><h2>Add user</h2><span class="reading-note">Passwords are BCrypt-hashed and never displayed.</span></div>
<form method="post" action="<%= cp %>/users" class="form-grid"><input type="hidden" name="csrfToken" value="<%= StringEscapeUtils.escapeHtml4(token) %>"><input type="hidden" name="action" value="create">
<div><label>Username</label><input required maxlength="50" name="userName" autocomplete="username"></div>
<div><label>Email</label><input type="email" maxlength="100" name="email" autocomplete="email"></div>
<div><label>Location</label><input maxlength="50" name="location"></div>
<div><label>Password</label><input required type="password" minlength="8" maxlength="72" name="password" autocomplete="new-password"></div>
<div class="wide"><button class="button">Add User</button></div></form></section>
<section class="panel"><div class="panel-title"><h2>Existing users</h2><span class="reading-note"><%= users == null ? 0 : users.size() %> record(s)</span></div><div class="table-wrap"><table><thead><tr><th>ID</th><th>Username</th><th>Email</th><th>Location</th><th>Actions</th></tr></thead><tbody>
<% if (users != null && !users.isEmpty()) { for (String[] user : users) { String id = StringEscapeUtils.escapeHtml4(user[0]); %>
<tr><td><%= id %></td><td><%= StringEscapeUtils.escapeHtml4(user[1]) %></td><td><%= StringEscapeUtils.escapeHtml4(user[2] == null ? "—" : user[2]) %></td><td><%= StringEscapeUtils.escapeHtml4(user[3] == null ? "—" : user[3]) %></td><td><div class="actions"><button type="button" class="text-button" data-edit="edit-<%= id %>">Edit</button><form class="inline-form" method="post" action="<%= cp %>/users" data-confirm="Delete this user and all of their devices, sensors, and historical readings? This cannot be undone."><input type="hidden" name="csrfToken" value="<%= StringEscapeUtils.escapeHtml4(token) %>"><input type="hidden" name="action" value="delete"><input type="hidden" name="userId" value="<%= id %>"><button class="text-button" style="color:var(--red)">Delete</button></form></div></td></tr>
<tr class="edit-row" id="edit-<%= id %>"><td colspan="5"><form method="post" action="<%= cp %>/users" class="edit-grid"><input type="hidden" name="csrfToken" value="<%= StringEscapeUtils.escapeHtml4(token) %>"><input type="hidden" name="action" value="update"><input type="hidden" name="userId" value="<%= id %>"><div><label>Username</label><input required maxlength="50" name="userName" value="<%= StringEscapeUtils.escapeHtml4(user[1]) %>"></div><div><label>Email</label><input type="email" maxlength="100" name="email" value="<%= StringEscapeUtils.escapeHtml4(user[2] == null ? "" : user[2]) %>"></div><div><label>Location</label><input maxlength="50" name="location" value="<%= StringEscapeUtils.escapeHtml4(user[3] == null ? "" : user[3]) %>"></div><div><label>New password <span class="reading-note">optional</span></label><input type="password" minlength="8" maxlength="72" name="password" autocomplete="new-password"></div><div><button class="button">Save changes</button></div></form></td></tr>
<% } } else { %><tr><td colspan="5" class="empty">No users are available.</td></tr><% } %>
</tbody></table></div></section></main></div><script src="<%= cp %>/assets/app.js"></script></body></html>
