<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<jsp:include page="header.jsp">
    <jsp:param name="pageTitle" value="Reset Password" />
</jsp:include>

<div class="container">
    <h2>Reset Password</h2>
    <c:if test="${not empty error}">
        <div class="alert alert-danger" role="alert">
            ${error}
        </div>
    </c:if>
    <c:if test="${not empty success}">
        <div class="alert alert-success" role="alert">
            ${success}
        </div>
    </c:if>
    <form action="ResetPasswordServlet" method="post">
        <input type="hidden" name="token" value="${token}">
        <div class="form-group">
            <label for="newPassword">New Password</label>
            <input type="password" class="form-control" id="newPassword" name="newPassword" required>
        </div>
        <div class="form-group">
            <label for="confirmPassword">Confirm New Password</label>
            <input type="password" class="form-control" id="confirmPassword" name="confirmPassword" required>
        </div>
        <button type="submit" class="btn btn-primary">Reset Password</button>
    </form>
</div>

<%@ include file="footer.jsp" %>