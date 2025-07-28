<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>


<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        
    </style>
</head>
<body>
    <div class="utility-bar">
        <div class="koth-info">
            <div>${applicationScope.kothSeason}</div>
            <div>${season} Week: ${week}</div>
        </div>

	    <div class="user-info">
		    <div class="user-info-top">
		        <a href="UpdateUserInfoServlet" class="username">
		            <i class="fas fa-user-circle"></i>
		            ${userName}
		        </a>
		    </div>
		    <a href="LogoutServlet" class="logout">Logout</a>
		</div>
    </div>
    	<div class="container">
        	<H3>Override picks for ${formattedUserDisplay}</H3>
       	</div>

    <script src="https://code.jquery.com/jquery-3.5.1.slim.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.5.3/dist/umd/popper.min.js"></script>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/js/bootstrap.min.js"></script>
 </body>
</html>