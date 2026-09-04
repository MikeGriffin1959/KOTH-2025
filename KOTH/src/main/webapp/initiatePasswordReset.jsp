<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<jsp:include page="headerForNotSignedIn.jsp">
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
            ${success}. Check your email for the link. Taking you back to sign in&hellip;
        </div>
        <script>
            // Sent — bounce back to the sign-in page after 3 seconds
            setTimeout(function () { window.location.href = 'LoginServlet'; }, 3000);
        </script>
    </c:if>
    <c:if test="${empty success}">
    <form action="InitiatePasswordResetServlet" method="post">
        <div class="form-group">
            <label for="email">Email</label>
            <input type="email" class="form-control" id="email" name="email" required maxlength="50" placeholder="e.g., john@example.com">
        </div>
        <button type="submit" class="btn btn-primary">Send Reset Link</button>
    </form>
    </c:if>
</div>

<%@ include file="footer.jsp" %>