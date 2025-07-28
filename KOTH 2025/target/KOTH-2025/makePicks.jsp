<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Make Picks</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>    
    </style>
</head>
<body>
<div class="container">

	<jsp:include page="header.jsp">
	    <jsp:param name="pageTitle" value="Make Picks Body" />
	</jsp:include>
	
	<jsp:include page="makePicksBody.jsp">
	    <jsp:param name="pageTitle" value="Make Picks Body" />
	</jsp:include>

</div>

<script>
</script>
</body>
</html>

<%@ include file="footer.jsp" %>