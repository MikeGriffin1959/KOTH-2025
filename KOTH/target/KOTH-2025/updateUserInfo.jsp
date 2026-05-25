<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="model.User" %>

<jsp:include page="header.jsp">
<jsp:param name="pageTitle" value="Update User Information" />
</jsp:include>

<%
User user = (User) request.getAttribute("user");
if (user == null) {
    response.sendRedirect("login.jsp");
    return;
}
String message = (String) session.getAttribute("message");
String messageType = (String) session.getAttribute("messageType");
// Clear the message after retrieving it
session.removeAttribute("message");
session.removeAttribute("messageType");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Update User Info - King of the Hill</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        html, body {
            height: 100%;
        }
        .form-control {
            max-width: 175px;
        }
    </style>
</head>
<body>

<% if (message != null && !message.isEmpty()) { %>
    <div class="alert alert-<%= messageType.equals("success") ? "success" : "danger" %> alert-dismissible fade show" role="alert">
        <%= message %>
        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
            <span aria-hidden="true">&times;</span>
        </button>
    </div>
<% } %>

<form action="UpdateUserInfoServlet" method="post" class="mt-4">
    <div class="form-group">
        <label for="firstName">First Name:</label>
        <input type="text" class="form-control" id="firstName" name="firstName" value="<%= user != null ? user.getFirstName() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="lastName">Last Name:</label>
        <input type="text" class="form-control" id="lastName" name="lastName" value="<%= user != null ? user.getLastName() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="userName">Username:</label>
        <input type="text" class="form-control" id="userName" name="userName" value="<%= user != null ? user.getUsername() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="email">Email:</label>
             <input type="email" class="form-control" id="email" name="email" required maxlength="40" placeholder="e.g., john@example.com">
    </div>
    <div class="form-group">
        <label for="cellNumber">Cell Number:</label>
        <input type="text" class="form-control" id="cellNumber" name="cellNumber" value="<%= user != null ? user.getCellNumber() : "" %>" required>
    </div>
    <div class="form-group">
            <label for="initialPicks">Number of Initial Picks</label>
            <select class="form-control" id="initialPicks" name="initialPicks">
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3">3</option>
            </select>
        </div>
    <button type="submit" class="btn btn-primary">Update</button>
</form>

<script src="https://code.jquery.com/jquery-3.5.1.slim.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.5.3/dist/umd/popper.min.js"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/js/bootstrap.min.js"></script>

</body>
</html>

<%@ include file="footer.jsp" %>



