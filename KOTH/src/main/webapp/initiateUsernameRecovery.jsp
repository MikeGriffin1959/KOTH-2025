<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="manifest" href="manifest.webmanifest">
    <meta name="theme-color" content="#1A43BF">
    <link rel="apple-touch-icon" href="icon-180.png">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black">
    <script>if ('serviceWorker' in navigator) { window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js'); }); }</script>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
   	<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
</head>
<body>
    <div class="container">
        <h2>Recover Username</h2>
        <form action="InitiateUsernameRecoveryServlet" method="post">
            <div class="form-group">
                <label for="email">Email:</label>
                <input type="email" class="form-control" id="email" name="email" required>
            </div>
            <button type="submit" class="btn btn-primary">Recover Username</button>
        </form>
        
        <c:if test="${not empty error}">
            <div class="alert alert-danger mt-3">${error}</div>
        </c:if>
        <c:if test="${not empty success}">
            <div class="alert alert-success mt-3">${success}</div>
        </c:if>
        
        <div class="mt-3">
            <a href="login.jsp" class="btn btn-link">Back to Login</a>
        </div>
    </div>

    <%@ include file="footer.jsp" %>
</body>
</html>