<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="http://jakarta.apache.org/tags/standard/c" %>

<jsp:include page="header.jsp">
    <jsp:param name="pageTitle" value="Error" />
</jsp:include>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Error</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
</head>
<body>
    <div class="container mt-5">
        <div class="row">
            <div class="col-md-6 offset-md-3">
                <h1 class="text-center mb-4">Oops! Something went wrong</h1>
                <div class="alert alert-danger">
                    <p><strong>Error Details:</strong></p>
                    <p>
                    <%
					String errorDetails = (String) request.getAttribute("errorDetails");
					if (errorDetails != null && !errorDetails.isEmpty()) {
					    out.println(errorDetails);
					} else if (exception != null) {
					    out.println(exception.toString());
					}
					%>
                    </p>
                </div>
                <div class="text-center mt-4">
                    <a href="HomeServlet" class="btn btn-primary">Return to Home</a>
                </div>
            </div>
        </div>
    </div>
</body>
</html>

<%@ include file="footer.jsp" %>