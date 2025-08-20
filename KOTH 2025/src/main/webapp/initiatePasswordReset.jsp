<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<jsp:include page="header.jsp">
<jsp:param name="pageTitle" value="Initiate Password Reset" />
</jsp:include>

<div class="container">
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
    <form action="InitiatePasswordResetServlet" method="post">
        <div class="form-group">
            <label for="email">Email</label>
            <input type="email" class="form-control" id="email" name="email" required maxlength="20" placeholder="e.g., john@example.com">
        </div>
        <button type="submit" class="btn btn-primary">Send Reset Link</button>
    </form>
</div>

<%@ include file="footer.jsp" %>