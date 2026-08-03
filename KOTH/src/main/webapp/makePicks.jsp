<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="manifest" href="manifest.webmanifest">
    <meta name="theme-color" content="#1A43BF">
    <link rel="apple-touch-icon" href="icon-180.png">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black">
    <script>if ('serviceWorker' in navigator) { window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js'); }); }</script>
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