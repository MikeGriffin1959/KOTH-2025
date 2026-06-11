<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page import="model.User" %>

<jsp:include page="header.jsp">
<jsp:param name="pageTitle" value="Update User Information" />
</jsp:include>

<%
User user = (User) request.getAttribute("user");
if (user == null) {
    response.sendRedirect("login.jsp");
    return;
}
String message = (String) session.getAttribute("message");
String messageType = (String) session.getAttribute("messageType");
// Clear the message after retrieving it
session.removeAttribute("message");
session.removeAttribute("messageType");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        html, body {
            height: 100%;
        }
        .form-control {
            max-width: 175px;
        }
    </style>
</head>
<body>

<% if (message != null && !message.isEmpty()) { %>
    <div class="alert alert-<%= messageType.equals("success") ? "success" : "danger" %> alert-dismissible fade show" role="alert">
        <%= message %>
        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
            <span aria-hidden="true">&times;</span>
        </button>
    </div>
<% } %>

<form action="UpdateUserInfoServlet" method="post" class="mt-4">
    <div class="form-group">
        <label for="firstName">First Name:</label>
        <input type="text" class="form-control" id="firstName" name="firstName" value="<%= user != null ? user.getFirstName() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="lastName">Last Name:</label>
        <input type="text" class="form-control" id="lastName" name="lastName" value="<%= user != null ? user.getLastName() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="userName">Username:</label>
        <input type="text" class="form-control" id="userName" name="userName" value="<%= user != null ? user.getUsername() : "" %>" required>
    </div>
    <div class="form-group">
        <label for="email">
            Email:
            <% if (user.isEmailVerified()) { %>
                <span class="badge badge-success"><i class="fas fa-check-circle"></i> Verified</span>
            <% } else { %>
                <span class="badge badge-warning"><i class="fas fa-exclamation-circle"></i> Not Verified</span>
            <% } %>
        </label>
        <input type="email" class="form-control" id="email" name="email" required maxlength="40"
               placeholder="e.g., john@example.com" value="<%= user.getEmail() != null ? user.getEmail() : "" %>">
        <% if (!user.isEmailVerified()) { %>
            <div class="mt-2">
                <div id="emailVerifyAlert" class="alert" style="display:none; max-width:350px;"></div>
                <button type="button" class="btn btn-sm btn-outline-info" onclick="resendVerificationEmail()" id="resendEmailBtn">
                    <i class="fas fa-envelope"></i> Send Verification Email
                </button>
            </div>
        <% } %>
    </div>
    <div class="form-group">
        <label for="cellNumber">
            Cell Number:
            <% if (user.isPhoneVerified()) { %>
                <span class="badge badge-success"><i class="fas fa-check-circle"></i> Verified</span>
            <% } else if (user.getCellNumber() != null && !user.getCellNumber().isEmpty()) { %>
                <span class="badge badge-warning"><i class="fas fa-exclamation-circle"></i> Not Verified</span>
            <% } %>
        </label>
        <input type="text" class="form-control" id="cellNumber" name="cellNumber" value="<%= user != null ? user.getCellNumber() : "" %>" required>
        <small class="form-text" style="color:#bbb;">US: 10 digits. International: include country code.</small>

        <!-- Phone verification (Twilio Verify) -->
        <div class="mt-2" id="phoneVerifySection">
            <div id="verifyAlert" class="alert" style="display:none; max-width: 350px;"></div>
            <% if (!user.isPhoneVerified()) { %>
                <div id="verifyStep1">
                    <button type="button" class="btn btn-sm btn-outline-info" onclick="sendVerificationCode()" id="sendCodeBtn">
                        <i class="fas fa-mobile-alt"></i> Send Verification Code
                    </button>
                </div>
                <div id="verifyStep2" style="display:none;">
                    <div class="d-flex" style="gap:8px; margin-top:6px;">
                        <input type="text" class="form-control" id="verifyCodeInput" maxlength="6"
                               placeholder="123456" pattern="[0-9]{6}" style="max-width:120px;">
                        <button type="button" class="btn btn-sm btn-success" onclick="checkVerificationCode()" id="checkCodeBtn">
                            <i class="fas fa-check"></i> Verify
                        </button>
                    </div>
                </div>
            <% } %>
        </div>
    </div>
    <div class="form-group">
            <label for="initialPicks">Number of Initial Picks</label>
            <select class="form-control" id="initialPicks" name="initialPicks">
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3">3</option>
            </select>
        </div>
    <button type="submit" class="btn btn-primary">Update</button>
</form>

<!-- Text Message Notification Preferences -->
<div class="card mt-4 mb-4" style="max-width: 480px; background-color: #111; border: 1px solid #444;">
    <div class="card-header" style="background-color:#1A43BF; color:white;">
        <i class="fas fa-comment-sms"></i> Text Message Notifications
    </div>
    <div class="card-body" style="color:#ddd;">
        <% if (!user.isPhoneVerified()) { %>
            <p class="mb-0">Verify your cell number above, then choose which text notifications you'd like to receive.</p>
        <% } else { %>
            <form action="UpdateUserInfoServlet" method="post">
                <input type="hidden" name="action" value="updateSmsPrefs">
                <c:forEach var="p" items="${smsPrefs}">
                    <div class="form-check mb-2">
                        <input class="form-check-input" type="checkbox"
                               id="pref_${p.typeKey}" name="pref_${p.typeKey}"
                               value="true" ${p.enabled ? 'checked' : ''}>
                        <label class="form-check-label" for="pref_${p.typeKey}">
                            ${p.displayName}
                            <small class="d-block" style="color:#999;">${p.description}</small>
                        </label>
                    </div>
                </c:forEach>
                <button type="submit" class="btn btn-primary mt-2">Save Preferences</button>
            </form>
        <% } %>
    </div>
</div>

<script>
// Phone verification (Twilio Verify) — mirrors GolferFest's flow.
function showVerifyAlert(success, msg) {
    var a = document.getElementById('verifyAlert');
    a.textContent = msg;
    a.className = success ? 'alert alert-success' : 'alert alert-danger';
    a.style.display = 'block';
    setTimeout(function(){ a.style.display = 'none'; }, 6000);
}
function postForm(params) {
    return fetch('UpdateUserInfoServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
        body: params
    }).then(function(r){ return r.json(); });
}
function resendVerificationEmail() {
    var btn = document.getElementById('resendEmailBtn');
    var a = document.getElementById('emailVerifyAlert');
    btn.disabled = true; btn.textContent = 'Sending…';
    postForm('action=resendVerificationEmail')
        .then(function(data) {
            a.textContent = data.message;
            a.className = data.success ? 'alert alert-success' : 'alert alert-danger';
            a.style.display = 'block';
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-envelope"></i> Send Verification Email';
        })
        .catch(function(e) {
            a.textContent = 'Error: ' + e.message;
            a.className = 'alert alert-danger';
            a.style.display = 'block';
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-envelope"></i> Send Verification Email';
        });
}
function sendVerificationCode() {
    var phone = document.getElementById('cellNumber').value.trim();
    if (!phone) { showVerifyAlert(false, 'Enter your cell number first'); return; }
    var btn = document.getElementById('sendCodeBtn');
    btn.disabled = true; btn.textContent = 'Sending…';
    postForm('action=sendVerificationCode&phone=' + encodeURIComponent(phone))
        .then(function(data) {
            showVerifyAlert(data.success, data.message);
            if (data.success) {
                document.getElementById('verifyStep1').style.display = 'none';
                document.getElementById('verifyStep2').style.display = 'block';
            } else {
                btn.disabled = false; btn.innerHTML = '<i class="fas fa-mobile-alt"></i> Send Verification Code';
            }
        })
        .catch(function(e) {
            showVerifyAlert(false, 'Error: ' + e.message);
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-mobile-alt"></i> Send Verification Code';
        });
}
function checkVerificationCode() {
    var phone = document.getElementById('cellNumber').value.trim();
    var code = document.getElementById('verifyCodeInput').value.trim();
    if (!/^[0-9]{6}$/.test(code)) { showVerifyAlert(false, 'Enter the 6-digit code'); return; }
    var btn = document.getElementById('checkCodeBtn');
    btn.disabled = true; btn.textContent = 'Checking…';
    postForm('action=checkVerificationCode&phone=' + encodeURIComponent(phone) + '&code=' + encodeURIComponent(code))
        .then(function(data) {
            showVerifyAlert(data.success, data.message);
            if (data.success) { setTimeout(function(){ window.location.reload(); }, 1200); }
            else { btn.disabled = false; btn.innerHTML = '<i class="fas fa-check"></i> Verify'; }
        })
        .catch(function(e) {
            showVerifyAlert(false, 'Error: ' + e.message);
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-check"></i> Verify';
        });
}
</script>

<script src="https://code.jquery.com/jquery-3.5.1.slim.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.5.3/dist/umd/popper.min.js"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/js/bootstrap.min.js"></script>

</body>
</html>

<%@ include file="footer.jsp" %>



