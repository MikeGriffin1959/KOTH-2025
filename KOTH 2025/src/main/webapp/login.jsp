<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://jakarta.apache.org/tags/standard/c" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - King of the Hill</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        html, body {
            height: 100%;
        }
        .form-control {
            max-width: 200px;
        }
        .btn {
            width: 200px;
            margin-bottom: 10px;
        }
        .forgot-password {
            font-size: 0.9em;
            margin-left: 10px;
        }
        .case-sensitive {
            font-size: 0.8em;
            color: white;
            display: block;
            margin-top: -5px;
            margin-bottom: 5px;
        }
        .username-container {
            display: flex;
            align-items: center;
        }
        .username-input {
            flex-grow: 1;
        }
    </style>
</head>

<body>
    <div class="content container">
        <h1 class="text-center mt-5">${applicationScope.kothSeason}</h1>
        <c:if test="${not empty errorMessage}">
            <div class="alert alert-danger" role="alert">
                ${error}
            </div>
        </c:if>

        <c:if test="${not empty message}">
            <div class="alert alert-success" role="alert">
                ${message}
            </div>
        </c:if>
        
        <form action="LoginServlet" method="post" class="mt-4" id="loginForm">
            <div class="form-group form-check">
                <input type="checkbox" class="form-check-input" id="rememberMe" name="rememberMe">
                <label class="form-check-label" for="rememberMe">Remember Me</label>
            </div>
            <div class="form-group">
                <label for="userName">Username:</label>
                <span class="case-sensitive">(case sensitive)</span>
                <div class="username-container">
                    <input type="text" class="form-control username-input" id="userName" name="userName" required maxlength="20">
                    <a href="InitiateUsernameRecoveryServlet" class="forgot-password">Forgot Username?</a>
                </div>
            </div>
            <div class="form-group">
                <label for="password">Password:</label>
                <div class="d-flex align-items-center">
                    <input type="password" class="form-control" id="password" name="password" required maxlength="20">
                    <a href="ResetPasswordServlet" class="forgot-password">Forgot Password?</a>
                </div>
            </div>
            <button type="submit" class="btn btn-primary">Sign In</button>
        </form>

       
       	 <c:if test="${applicationScope.allowSignUp}">
            <p class="mt-3">New Users:</p>
            <form action="SignUpServlet" method="get">
                <button type="submit" class="btn btn-secondary">Sign Up</button>
            </form>
         </c:if>
    </div>
</body>
</html>

<%@ include file="footer.jsp" %>
