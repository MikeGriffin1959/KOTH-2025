<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://jakarta.apache.org/tags/standard/c" %>

<jsp:include page="header.jsp">
    <jsp:param name="pageTitle" value="Change User Name" />
</jsp:include>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Change Username - King of the Hill</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        html, body {
            height: 100%;
        }

        .form-control {
            max-width: 200px;
        }
    </style>
</head>
<body>
    <div class="container-fluid">
	    <div class="row mt-3">
	            <div class="col-12">
	                <a href="home.jsp" class="btn btn-primary btn-main-menu">Main Menu</a>
	            </div>
        </div>

        <form action="UpdateUserNameServlet" method="post" class="mt-4">
            <div class="form-group">
                <label for="currentUserName">Current Username:</label>
                <input type="text" class="form-control" id="currentUserName" name="currentUserName" required maxlength="20">
            </div>
            <div class="form-group">
                <label for="newUserName">New Username:</label>
                <input type="text" class="form-control" id="newUserName" name="newUserName" required maxlength="20">
            </div>
            <button type="submit" class="btn btn-primary">Change</button>
        </form>

    </div>

</body>
</html>

<%@ include file="footer.jsp" %>



