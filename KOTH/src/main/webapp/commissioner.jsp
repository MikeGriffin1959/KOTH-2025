<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"   uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"  %>
<%@ taglib prefix="fn"  uri="jakarta.tags.functions" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="model.User" %>


<%
    // Sort users by last name
    @SuppressWarnings({"unchecked"})
    List<User> sortedUsers = (List<User>) request.getAttribute("users");
    Collections.sort(sortedUsers, new Comparator<User>() {
        public int compare(User u1, User u2) {
            return u1.getLastName().compareToIgnoreCase(u2.getLastName());
        }
    });
    request.setAttribute("sortedUsers", sortedUsers);
%>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="pickPricesJson" content='${pickPricesJson}'>
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="manifest" href="manifest.webmanifest">
    <meta name="theme-color" content="#1A43BF">
    <link rel="apple-touch-icon" href="icon-180.png">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black">
    <script>if ('serviceWorker' in navigator) { window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js'); }); }</script>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        .custom-table {
            background-color: black;
            color: white !important;
            border: 2px solid white;
            table-layout: auto !important;
            width: 100%;
        }
        .custom-table th, .custom-table td {
            height: auto !important;
            padding: 4px 8px !important;
            vertical-align: bottom !important;
            border: 2px solid white;
        }
        .custom-table thead th {
            background-color: black;
            color: white;
        }
        .custom-table th, .custom-table td {
            text-align: center;
            vertical-align: Bottom;
            border: 2px solid white;
        }
        .custom-table tbody tr {
            background-color: #333;
        }
        .btn-primary {
            background-color: #007bff;
            border-color: #007bff;
        }
        .table td {
            background-color: black;
            border: 2px solid white;
            height: 34px;
        }  
        .card {
            overflow: hidden; 
        }
        .custom-table {
            margin-bottom: 0 !important; 
        }
        .card-body form {
            margin: 0; 
        }
        .table {
            width: 100%; 
            margin: 0; 
        }
        .card:has(.custom-table) .card-body {
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
            padding: 0;
        }
        @media (max-width: 767px) {
            .card {
                margin-bottom: 20px;
            }
            .custom-table {
                min-width: 800px;
            }           
            .card:has(.custom-table) .card-body {
                background:
                    linear-gradient(90deg, rgba(0,0,0,.12) 0%, rgba(0,0,0,0) 2px) 0 0,
                    linear-gradient(-90deg, rgba(0,0,0,.12) 0%, rgba(0,0,0,0) 2px) 100% 0;
                background-repeat: no-repeat;
                background-size: 100% 100%;
            }
        }
        .alert {
            display: none;
        }
        .alert.alert-warning {
            background-color: #fff3cd;
            border-color: #ffeeba;
            color: #856404;
        }
        .alert.alert-danger {
            background-color: #f8d7da;
            border-color: #f5c6cb;
            color: #721c24;
        }
        .alert.alert-success {
            background-color: #d4edda;
            border-color: #c3e6cb;
            color: #155724;
        }
        .alert.alert-info {
            background-color: #d1ecf1;
            border-color: #bee5eb;
            color: #0c5460;
        }
        .alert {
            margin-bottom: 1rem;
            padding: 0.75rem 1.25rem;
            border: 1px solid transparent;
            border-radius: 0.25rem;
        }
        .verified-badge {
            display: inline-flex; align-items: center; gap: 4px;
            background-color: #0d3320; color: #90EE90;
            padding: 2px 8px; border-radius: 12px;
            font-size: 0.78rem; font-weight: 500; white-space: nowrap;
        }
        .unverified-badge {
            display: inline-flex; align-items: center; gap: 4px;
            background-color: #3a1a1a; color: #FF6B6B;
            padding: 2px 8px; border-radius: 12px;
            font-size: 0.78rem; font-weight: 500; white-space: nowrap;
        }
    </style>
</head>
<body>               
    <div class="container">
    <jsp:include page="header.jsp">
    <jsp:param name="pageTitle" value="Commissioner Dashboard" />
</jsp:include>
    
        <!-- Row 1 - First set of cards -->
        <div class="row">
            <!-- Override Season/Week Card -->
			<div class="col-12 col-md-4 mb-4">
			    <div class="card">
			        <div class="card-header text-center">
			            <h5 class="mb-0">Override Season/Week</h5>
			        </div>
			        <div class="card-body">
			            <!-- Add alert container -->
			            <div id="seasonWeekAlert" class="alert" style="display: none;"></div>
			            
			            <form id="seasonWeekForm" action="CommissionerServlet" method="post">
			                <input type="hidden" name="action" value="setSeasonWeek">
			                <div class="form-group">
			                    <label for="season">Season:</label>
			                    <select class="form-control" id="season" name="season">
			                        <option value="X">Select</option>
			                        <option value="2025">2025</option>
			                        <option value="2026">2026</option>
			                        <option value="2027">2027</option>
			                        <option value="2028">2028</option>
			                    </select>
			                </div>
			                <div class="form-group">
			                    <label for="week">Week:</label>
			                    <select class="form-control" id="week" name="week">
			                        <option value="">Select</option>
			                        <% for (int i = 0; i <= 23; i++) { %>
			                            <option value="<%= i %>"><%= i %></option>
			                        <% } %>
			                    </select>
			                </div>
			                <div class="form-check mb-3">
			                    <input type="checkbox" class="form-check-input" id="autoSeason" name="autoSeason">
			                    <label class="form-check-label" for="autoSeason">Auto</label>
			                </div>
			                <button type="submit" class="btn btn-primary">Set</button>
			            </form>
			        </div>
			    </div>
			</div>
            
            <!-- Override Picks Card -->
			<div class="col-12 col-md-4 mb-4">
			    <div class="card">
			        <div class="card-header text-center">
			            <h5 class="mb-0">Override Picks</h5>
			        </div>
			        <div class="card-body">
			            <form action="CommissionerOverrideServlet" method="get">
			                <div class="form-group mb-3">
			                    <label for="userDropdown">Select User:</label>
			                    <select class="form-control" id="userDropdown" name="selectedUser">
			                        <c:forEach var="user" items="${sortedUsers}">
			                            <option value="${user.idUser}:${user.username}">${user.lastName}, ${user.firstName} (${user.username})</option>
			                        </c:forEach>
			                    </select>
			                </div>
			                <button type="submit" class="btn btn-primary">Submit</button>
			            </form>
			        </div>
			    </div>
			</div>

          	<!-- New Season Card -->
			<div class="col-12 col-md-4 mb-4">
			    <div class="card">
			        <div class="card-header text-center">
			            <h5 class="mb-0">New Season</h5>
			        </div>
			        <div class="card-body">
			            <div id="newSeasonAlert" class="alert" style="display: none;"></div>
			            <form id="newSeasonForm">
			                <div class="form-group">
			                    <label for="newSeason">Season: <span>${currentSeason}</span></label>
			                    <select class="form-control" id="newSeason" name="season" required>
			                        <option value="">Select</option>
			                    </select>
			                </div>
			                <div class="form-group">
			                    <label for="seasonType">Season Type: <span id="currentSeasonType"></span></label>
			                    <select class="form-control" id="seasonType" name="seasonType" required>
			                        <option value="">Select</option>
			                        <option value="KOTH">KOTH</option>
			                        <option value="KOTH 2">KOTH 2</option>
			                        <option value="KOTH 3">KOTH 3</option>
			                        <option value="KOTH 4">KOTH 4</option>
			                        <option value="KOTH 5">KOTH 5</option>
			                        <option value="KOTH Test">KOTH Test</option>
			                    </select>
			                </div>
			                <button type="submit" class="btn btn-primary">Update</button>
			            </form>
			            <div class="mt-3">
			                <!-- Bootstrap alert classes based on success -->
			                <div id="allowSignUpAlert" class="alert ${param.success ? 'alert-success' : 'alert-danger'}" 
			                     style="display: ${param.messageType == 'allowSignUp' && not empty param.message ? 'block' : 'none'}">
			                    ${param.message}
			                </div>
			                <form id="allowSignUpForm" action="CommissionerServlet" method="post">
			                    <input type="hidden" name="action" value="allowNewUsers">
			                    <div class="form-check mb-3">
			                        <input type="checkbox" class="form-check-input" id="allowSignUpCheck" name="allowNewUsers">
			                        <label class="form-check-label" for="allowSignUpCheck">Allow Sign Up</label>
			                    </div>
			                    <button type="submit" class="btn btn-primary">Set</button>
			                </form>
			            </div>
			            <div class="mt-3">
						    <div id="maskPicksAlert" class="alert ${param.success ? 'alert-success' : 'alert-danger'}" 
						         style="display: ${param.messageType == 'maskPicks' && not empty param.message ? 'block' : 'none'}">
						        ${param.message}
						    </div>
						    <form id="maskPicksForm" action="CommissionerServlet" method="post">
						        <input type="hidden" name="action" value="toggleMaskPicks">
						        <div class="form-check mb-3">
						            <input type="checkbox" class="form-check-input" id="maskPicksCheck" name="maskPicks">
						            <label class="form-check-label" for="maskPicksCheck">Mask Picks</label>
						        </div>
						        <button type="submit" class="btn btn-primary">Set</button>
						    </form>
						</div>
			        </div>
			    </div>
			</div>
			<!-- Pick Prices Card -->
			<div class="col-12 col-md-4 mb-4">
			    <div class="card">
			        <div class="card-header text-center">
			            <h5 class="mb-0">Pick Prices</h5>
			        </div>
			        <div class="card-body">
			            <!-- Bootstrap alert -->
			            <div id="pickPricesAlert" class="alert ${param.success ? 'alert-success' : 'alert-danger'}" 
			                 style="display: ${param.messageType == 'pickPrices' && not empty param.message ? 'block' : 'none'}">
			                ${param.message}
			            </div>
			            
			            <form id="pickPricesForm" action="CommissionerServlet" method="post">
			                <input type="hidden" name="action" value="updatePricePerPick">
			                <input type="hidden" name="season" value="${season}">
			                
			                <!-- Row 1 -->
			                <div class="row mb-3">
			                    <div class="col-6">
			                        <label for="maxPicks">Max Picks: <span>${currentPrices.maxPicks}</span></label>
			                        <select class="form-control" id="maxPicks" name="maxPicks" required>
			                            <option value="">Select</option>
			                            <option value="1">1</option>
			                            <option value="2">2</option>
			                            <option value="3">3</option>
			                            <option value="4">4</option>
			                            <option value="5">5</option>
			                        </select>
			                    </div>
			                    <div class="col-6">
			                        <label for="price1">1st Pick: <span>$${currentPrices.pickPrice1}</span></label>
			                        <div class="input-group">
			                            <div class="input-group-prepend">
			                                <span class="input-group-text">$</span>
			                            </div>
			                            <input type="number" class="form-control text-right" id="price1" name="price1" 
			                                   step="0.50" min="0.50" max="50.00" placeholder="0.00">
			                        </div>
			                    </div>
			                </div>
			                
			                <!-- Row 2 -->
			                <div class="row mb-3">
			                    <div class="col-6">
			                        <label for="price2">2nd Pick: <span>$${currentPrices.pickPrice2}</span></label>
			                        <div class="input-group">
			                            <div class="input-group-prepend">
			                                <span class="input-group-text">$</span>
			                            </div>
			                            <input type="number" class="form-control text-right" id="price2" name="price2" 
			                                   step="0.50" min="0.50" max="50.00" placeholder="0.00">
			                        </div>
			                    </div>
			                    <div class="col-6">
			                        <label for="price3">3rd Pick: <span>$${currentPrices.pickPrice3}</span></label>
			                        <div class="input-group">
			                            <div class="input-group-prepend">
			                                <span class="input-group-text">$</span>
			                            </div>
			                            <input type="number" class="form-control text-right" id="price3" name="price3" 
			                                   step="0.50" min="0.50" max="50.00" placeholder="0.00">
			                        </div>
			                    </div>
			                </div>
			                
			                <!-- Row 3 -->
			                <div class="row mb-3">
			                    <div class="col-6">
			                        <label for="price4">4th Pick: <span>$${currentPrices.pickPrice4}</span></label>
			                        <div class="input-group">
			                            <div class="input-group-prepend">
			                                <span class="input-group-text">$</span>
			                            </div>
			                            <input type="number" class="form-control text-right" id="price4" name="price4" 
			                                   step="0.50" min="0.50" max="50.00" placeholder="0.00">
			                        </div>
			                    </div>
			                    <div class="col-6">
			                        <label for="price5">5th Pick: <span>$${currentPrices.pickPrice5}</span></label>
			                        <div class="input-group">
			                            <div class="input-group-prepend">
			                                <span class="input-group-text">$</span>
			                            </div>
			                            <input type="number" class="form-control text-right" id="price5" name="price5" 
			                                   step="0.50" min="0.50" max="50.00" placeholder="0.00">
			                        </div>
			                    </div>
			                </div>
			                
			                <!-- Submit Button -->
			                <div class="row">
			                    <div class="col-12">
			                        <button type="submit" class="btn btn-primary">Set</button>
			                    </div>
			                </div>
			            </form>
			        </div>
			    </div>
			</div>
		</div>
  
        <!-- Commentary Card (M1) -->
        <div class="row">
            <div class="col-12 col-md-4 mb-4">
                <div class="card">
                    <div class="card-header text-center">
                        <h5 class="mb-0">Commentary</h5>
                    </div>
                    <div class="card-body">
                        <div id="commentaryAlert" class="alert" style="display: none;"></div>

                        <div class="form-check mb-3">
                            <input type="checkbox" class="form-check-input" id="commentaryEnabledCheck">
                            <label class="form-check-label" for="commentaryEnabledCheck">Enable Commentary</label>
                        </div>

                        <div class="form-group mb-3">
                            <label for="snarkLevelSlider">
                                Snark Level: <span id="snarkLevelBadge" class="badge badge-secondary">5</span>
                            </label>
                            <input type="range" class="custom-range" id="snarkLevelSlider" min="0" max="10" step="1" value="5">
                        </div>

                        <div class="form-check mb-3">
                            <input type="checkbox" class="form-check-input" id="commentaryNotificationsCheck">
                            <label class="form-check-label" for="commentaryNotificationsCheck">Text Notifications (Recap &amp; Live Commentary)</label>
                        </div>

                        <div class="form-group mb-3">
                            <label for="previewDaySelect">Preview Day:</label>
                            <select class="form-control" id="previewDaySelect">
                                <option value="1">Monday</option>
                                <option value="2">Tuesday</option>
                                <option value="3">Wednesday</option>
                                <option value="4">Thursday</option>
                                <option value="5">Friday</option>
                                <option value="6">Saturday</option>
                                <option value="7">Sunday</option>
                            </select>
                        </div>

                        <button type="button" class="btn btn-primary" id="testCommentaryBtn">Fire Test Commentary</button>
                        <div class="mt-3">
                            <a href="DossierServlet" class="btn btn-sm btn-outline-light">
                                <i class="fas fa-address-book"></i> Manage Dossiers
                            </a>
                        </div>
                        <div id="testCommentaryReadout" class="mt-3"
                             style="display:none; white-space: pre-wrap; background:#222; color:#eee; padding:10px; border-radius:4px;"></div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Text Messaging Card (SMS, ported from GolferFest) -->
        <div class="row">
            <div class="col-12 col-md-4 mb-4">
                <div class="card">
                    <div class="card-header text-center">
                        <h5 class="mb-0">Text Messaging</h5>
                    </div>
                    <div class="card-body">
                        <div id="smsAlert" class="alert" style="display: none;"></div>

                        <h6><i class="fas fa-vial"></i> Quick Test</h6>
                        <div class="d-flex mb-3" style="gap:8px;">
                            <input type="text" class="form-control" id="testPhone" placeholder="+15551234567">
                            <button type="button" class="btn btn-sm btn-outline-info" onclick="sendTestSms()" id="testSmsBtn">
                                <i class="fas fa-paper-plane"></i> Send
                            </button>
                        </div>

                        <h6><i class="fas fa-user"></i> Send to User</h6>
                        <select class="form-control mb-2" id="smsUserSelect">
                            <option value="">Select user (verified only)</option>
                            <c:forEach var="user" items="${sortedUsers}">
                                <c:if test="${not empty user.cellNumber && user.phoneVerified}">
                                    <option value="${user.idUser}">${user.lastName}, ${user.firstName}</option>
                                </c:if>
                            </c:forEach>
                        </select>
                        <textarea class="form-control mb-1" id="smsUserMessage" maxlength="320" rows="2" placeholder="Message"></textarea>
                        <small style="color:#999;"><span id="smsCharCount">0</span>/320</small>
                        <div class="mb-3">
                            <button type="button" class="btn btn-sm btn-primary" onclick="sendUserSms()" id="userSmsBtn">Send</button>
                        </div>

                        <h6><i class="fas fa-bullhorn"></i> Broadcast to Pool</h6>
                        <textarea class="form-control mb-2" id="smsBroadcastMessage" maxlength="320" rows="2" placeholder="Message to all verified players"></textarea>
                        <button type="button" class="btn btn-sm btn-warning" onclick="sendBroadcastSms()" id="broadcastSmsBtn">Broadcast</button>
                    </div>
                </div>
            </div>

            <!-- Email Card (ported from GolferFest) -->
            <div class="col-12 col-md-4 mb-4">
                <div class="card">
                    <div class="card-header text-center">
                        <h5 class="mb-0">Email</h5>
                    </div>
                    <div class="card-body">
                        <div id="emailAlert" class="alert" style="display: none;"></div>

                        <h6><i class="fas fa-user"></i> Send to User</h6>
                        <select class="form-control mb-2" id="emailUserSelect">
                            <option value="">Select user</option>
                            <c:forEach var="user" items="${sortedUsers}">
                                <c:if test="${not empty user.email}">
                                    <option value="${user.idUser}">${user.lastName}, ${user.firstName}</option>
                                </c:if>
                            </c:forEach>
                        </select>
                        <input type="text" class="form-control mb-2" id="emailUserSubject" maxlength="100" placeholder="Subject">
                        <textarea class="form-control mb-2" id="emailUserMessage" maxlength="2000" rows="3" placeholder="Message"></textarea>
                        <div class="mb-3">
                            <button type="button" class="btn btn-sm btn-primary" onclick="sendUserEmail()" id="userEmailBtn">Send</button>
                        </div>

                        <h6><i class="fas fa-bullhorn"></i> Broadcast to Pool</h6>
                        <input type="text" class="form-control mb-2" id="emailBroadcastSubject" maxlength="100" placeholder="Subject">
                        <textarea class="form-control mb-2" id="emailBroadcastMessage" maxlength="2000" rows="3" placeholder="Message to all players"></textarea>
                        <button type="button" class="btn btn-sm btn-warning" onclick="sendBroadcastEmail()" id="broadcastEmailBtn">Broadcast</button>
                    </div>
                </div>
            </div>
        </div>

        <!-- User Data Table -->
        <div class="row">
            <div class="col-12 mb-4">
                <div class="card">
                    <div class="card-header text-center">
                        <h5 class="mb-0">User Data</h5>
                    </div>
                    <!-- Add alert div for messages -->
		            <div id="userTableAlert" class="alert" role="alert" style="display: none;"></div>
		            
		            <!-- Add warning for delete confirmation -->
		            <div id="deleteWarning" class="alert alert-warning" style="display: none;">
		                Warning: This action cannot be undone! All selected users and their picks will be permanently deleted.
		                Please check the confirmation box below if you wish to proceed.
		            </div>
                    <div class="card-body p-0"> 
		                <form id="userDataForm" action="CommissionerServlet" method="post">
		                    <input type="hidden" name="action" value="updateUsers">
		                    <table class="table custom-table mb-0">
                                <thead>
                                    <tr>
                                        <th>Last Name</th>
                                        <th>First Name</th>
                                        <th>Username</th>
                                        <th>Email</th>
                                        <th title="Cell number verified via texted code">Phone<br>Verified</th>
                                        <th title="Email address verified via emailed link">Email<br>Verified</th>
                                        <th>Commish</th>
                                        <th>Paid</th>
                                        <th>Initial<br>Picks</th>
                                        <th>Delete<br>User</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="user" items="${sortedUsers}">
                                        <tr>
                                            <td class="text-left">${user.lastName}</td>
                                            <td class="text-left">${user.firstName}</td>
                                            <td class="text-left">${user.username}</td>
                                            <td class="text-left">${user.email}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${user.phoneVerified}">
                                                        <span class="verified-badge" title="Phone verified">&#10003; Yes</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="unverified-badge" title="Phone not verified - receives no texts">&#9888; No</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${user.emailVerified}">
                                                        <span class="verified-badge" title="Email verified">&#10003; Yes</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="unverified-badge" title="Email not verified">&#9888; No</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <input type="checkbox" name="userCommish_${user.idUser}" ${user.commish ? "checked" : ""}>
                                            </td>
                                            <td>
                                                <input type="checkbox" name="userPicksPaid_${user.idUser}" ${user.picksPaid ? "checked" : ""}>
                                            </td>
                                            <td>
                                                <select name="initialPicks_${user.idUser}">
                                                    <c:forEach var="i" begin="0" end="5">
                                                        <option value="${i}" ${user.initialPicks == i ? "selected" : ""}>${i}</option>
                                                    </c:forEach>
                                                </select>
                                                <input type="hidden" name="currentInitialPicks_${user.idUser}" value="${user.initialPicks}">
                                                <input type="hidden" name="userId" value="${user.idUser}">
                                            </td>
                                            <td>
                                                <input type="checkbox" name="deleteUser_${user.idUser}" class="delete-user-checkbox">
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                         <div class="card-footer text-center">
	                            <div class="form-check mb-3">
	                                <input type="checkbox" class="form-check-input" id="confirmDelete" name="confirmDelete">
	                                <label class="form-check-label" for="confirmDelete">Confirm Delete Selected Users</label>
	                            </div>
                            <button type="submit" id="updateButton" class="btn btn-primary">Update</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>

<%@ include file="footer.jsp" %>
    
<!-- Scripts -->
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script>
// Global variable for saving season data
let savedSeasonData = {
    season: null,
    seasonType: null
};

document.addEventListener('DOMContentLoaded', function() {
    const allowSignUpCheck = document.getElementById('allowSignUpCheck');
 	    
    // Get the allowSignUp alert element
    const allowSignUpAlert = document.getElementById('allowSignUpAlert');
    
    // If alert is visible, set a timeout to hide it
    if (allowSignUpAlert && allowSignUpAlert.style.display === 'block') {
        setTimeout(() => {
            allowSignUpAlert.style.display = 'none';
        }, 5000);
    }

    // Initialize season dropdown
    const seasonSelect = document.getElementById('newSeason');
    if (seasonSelect) {
        const currentYear = new Date().getFullYear();
        seasonSelect.innerHTML = '<option value="">Select</option>';
        for (let year = currentYear - 1; year <= currentYear + 2; year++) {
            const option = new Option(year.toString(), year.toString());
            seasonSelect.add(option);
        }
    }

 // display current season type & signup status
    try {
        const pickPricesMetaTag = document.querySelector('meta[name="pickPricesJson"]');
        if (pickPricesMetaTag) {
            const pickPricesJson = JSON.parse(pickPricesMetaTag.content);
            
            // Set current season type
            const currentSeasonTypeSpan = document.getElementById('currentSeasonType');
            if (currentSeasonTypeSpan && pickPricesJson.kothSeason) {
                currentSeasonTypeSpan.textContent = pickPricesJson.kothSeason;
            }
            
            // Set allow signup checkbox
            const allowSignUpCheck = document.getElementById('allowSignUpCheck');
            if (allowSignUpCheck && pickPricesJson.allowSignUp !== undefined) {
                allowSignUpCheck.checked = pickPricesJson.allowSignUp;
            }
            
            // Set mask picks checkbox  ← MOVE IT HERE
            const maskPicksCheck = document.getElementById('maskPicksCheck');
            if (maskPicksCheck && pickPricesJson.maskPicks !== undefined) {
                maskPicksCheck.checked = pickPricesJson.maskPicks;
            }
        }
    } catch (e) {
        console.error('Error parsing pickPricesJson:', e);
    }

    // New Season form handler
    document.getElementById('newSeasonForm').addEventListener('submit', function(e) {
    e.preventDefault();
    const alertDiv = document.getElementById('newSeasonAlert');
    const season = document.getElementById('newSeason').value;
    const seasonType = document.getElementById('seasonType').value;
    
    // Update the global savedSeasonData
    savedSeasonData = {
        season: season,
        seasonType: seasonType
    };
    
    console.log('Form submitted with values:', savedSeasonData);
    
    if (!season || !seasonType) {
        alertDiv.className = 'alert alert-warning';
        alertDiv.textContent = !season ? 'Please select season' : 'Please select season type';
        alertDiv.style.display = 'block';
        return;
    }

    alertDiv.className = 'alert alert-warning';
    alertDiv.innerHTML = `Warning: Creating a new season for ${season} will<br>` +
        '- Delete picks from prior seasons<br>' +
        '- Delete users from previous seasons<br>' +
        '- Delete schedule/results from previous seasons<br>' +
        '- Update team and game information for selected season<br>' +
        '- Set season type to ' + seasonType + '<br><br>' +
        'Current Season users/picks are not impacted<br><br>' +
        'Are you sure you want to continue?<br><br>' +
        '<button type="button" class="btn btn-danger mr-2" onclick="confirmScheduleCreation(true)">Confirm</button> ' +
        '<button type="button" class="btn btn-secondary" onclick="confirmScheduleCreation(false)">Cancel</button>';
    alertDiv.style.display = 'block';
});

    // Auto Season checkbox handler
    const autoSeasonCheckbox = document.getElementById('autoSeason');
    const seasonDropdown = document.getElementById('season');
    const weekDropdown = document.getElementById('week');
    const seasonWeekAlert = document.getElementById('seasonWeekAlert');
    const seasonWeekForm = document.getElementById('seasonWeekForm');

    if (autoSeasonCheckbox) {
        autoSeasonCheckbox.addEventListener('change', function() {
            const isChecked = this.checked;
            seasonDropdown.disabled = isChecked;
            weekDropdown.disabled = isChecked;
            seasonDropdown.required = !isChecked;
            weekDropdown.required = !isChecked;
        });
    }

    if (seasonWeekForm) {
        seasonWeekForm.addEventListener('submit', function(e) {
            e.preventDefault();

            const formData = new FormData(this);
            const autoChecked = autoSeasonCheckbox.checked;

            // Explicitly set the autoSeason parameter
            formData.set('autoSeason', autoChecked);

            if (autoChecked) {
                formData.delete('season');
                formData.delete('week');
            } else {
                const season = seasonDropdown.value;
                const week = weekDropdown.value;

                if (season === 'X' || !week) {
                    seasonWeekAlert.textContent = 'Please select both Season and Week when Auto is off.';
                    seasonWeekAlert.className = 'alert alert-danger';
                    seasonWeekAlert.style.display = 'block';
                    setTimeout(() => {
                        seasonWeekAlert.style.display = 'none';
                    }, 5000);
                    return;
                }
            }

            // Debug: Log FormData contents
            console.log('Submitting FormData:');
            for (let pair of formData.entries()) {
                console.log(`${pair[0]}: ${pair[1]}`);
            }

            fetch('CommissionerServlet', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                seasonWeekAlert.innerHTML = data.message;
                seasonWeekAlert.className = data.success ? 'alert alert-success' : 'alert alert-danger';
                seasonWeekAlert.style.display = 'block';
                setTimeout(() => {
                    seasonWeekAlert.style.display = 'none';
                    if (data.success) {
                        window.location.reload();
                    }
                }, 5000);
            })
            .catch(error => {
                console.error('Error:', error);
                seasonWeekAlert.textContent = 'An error occurred while updating season/week.';
                seasonWeekAlert.className = 'alert alert-danger';
                seasonWeekAlert.style.display = 'block';
                setTimeout(() => {
                    seasonWeekAlert.style.display = 'none';
                }, 5000);
            });
        });
    }
});
</script>
<script>
// Update pick count alert handling
document.addEventListener('DOMContentLoaded', function() {
    const pickPricesAlert = document.getElementById('pickPricesAlert');
    const maskPicksAlert = document.getElementById('maskPicksAlert');
    if (maskPicksAlert && maskPicksAlert.style.display === 'block') {
        setTimeout(() => { maskPicksAlert.style.display = 'none'; }, 5000);
    }
    if (pickPricesAlert && pickPricesAlert.style.display === 'block') {
        setTimeout(() => {
            pickPricesAlert.style.display = 'none';
        }, 5000);
    }
});
</script>

<script>
//Delete user alert handling
document.addEventListener('DOMContentLoaded', function() {
    const userTableAlert = document.getElementById('userTableAlert');
    const deleteWarning = document.getElementById('deleteWarning');
    const userDataForm = document.getElementById('userDataForm');
    const confirmDeleteCheckbox = document.getElementById('confirmDelete');
    
    // Get message parameters from URL
    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('message');
    const messageType = urlParams.get('messageType');
    
    // Show alert if there's a message
    if (message) {
        userTableAlert.textContent = message;
        userTableAlert.className = 'alert alert-' + 
            (messageType == 'success' ? 'success' : 
             messageType == 'warning' ? 'warning' : 'danger');
        userTableAlert.style.display = 'block';
        
        // Hide alert after 5 seconds
        setTimeout(() => {
            userTableAlert.style.display = 'none';
        }, 5000);
    }

    // Show/hide delete warning when checkboxes are checked
    document.querySelectorAll('.delete-user-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', function() {
            const anyChecked = Array.from(document.querySelectorAll('.delete-user-checkbox'))
                .some(cb => cb.checked);
            deleteWarning.style.display = anyChecked ? 'block' : 'none';
        });
    });

    // Form submission validation
    userDataForm.addEventListener('submit', function(e) {
        const deleteCheckboxes = document.querySelectorAll('.delete-user-checkbox:checked');
        
        if (deleteCheckboxes.length > 0 && !confirmDeleteCheckbox.checked) {
            e.preventDefault();
            userTableAlert.textContent = 'Please check the "Confirm Delete Selected Users" box to proceed with deletion.';
            userTableAlert.className = 'alert alert-warning';
            userTableAlert.style.display = 'block';
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });
});
</script>
<script>
function confirmScheduleCreation(confirmed) {
    const alertDiv = document.getElementById('newSeasonAlert');
    
    console.log('confirmScheduleCreation called with saved data:', savedSeasonData);
    
    if (!confirmed) {
        alertDiv.style.display = 'none';
        return;
    }

    if (!savedSeasonData || !savedSeasonData.season || !savedSeasonData.seasonType) {
        console.error('Missing required data:', savedSeasonData);
        alertDiv.className = 'alert alert-danger';
        alertDiv.textContent = 'Season and Season Type are required';
        alertDiv.style.display = 'block';
        return;
    }

    // Create URL-encoded form data
    const formData = new URLSearchParams();
    formData.append('action', 'createSchedule');
    formData.append('season', savedSeasonData.season);
    formData.append('seasonType', savedSeasonData.seasonType);
    formData.append('confirmed', 'true');

    console.log('Submitting form data:', formData.toString());

    fetch('CommissionerServlet', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: formData.toString()
    })
    .then(response => response.json())
    .then(data => {
        console.log('Server response:', data);
        alertDiv.className = data.success ? 'alert alert-success' : 'alert alert-danger';
        if (data.messages && Array.isArray(data.messages)) {
            alertDiv.innerHTML = data.messages.join('<br>');
        } else {
            alertDiv.innerHTML = data.message || 'Operation completed';
        }
        if (data.success) {
            setTimeout(() => {
                window.location.reload();
            }, 3000);
        }
    })
    .catch(error => {
        console.error('Error:', error);
        alertDiv.className = 'alert alert-danger';
        alertDiv.innerHTML = 'Error: ' + error.message;
    });
}
</script>

<script>
// Commentary card (M1): init from pickPricesJson meta-tag, instant-save via AJAX.
document.addEventListener('DOMContentLoaded', function() {
    const enableCheck = document.getElementById('commentaryEnabledCheck');
    if (!enableCheck) return; // card not on page

    const snarkSlider = document.getElementById('snarkLevelSlider');
    const snarkBadge = document.getElementById('snarkLevelBadge');
    const notifyCheck = document.getElementById('commentaryNotificationsCheck');
    const previewSelect = document.getElementById('previewDaySelect');
    const testBtn = document.getElementById('testCommentaryBtn');
    const readout = document.getElementById('testCommentaryReadout');
    const alertDiv = document.getElementById('commentaryAlert');

    function updateSnarkBadge(val) {
        snarkBadge.textContent = val;
        let cls = 'badge badge-success';      // green at the calm end
        if (val >= 8) cls = 'badge badge-danger';   // red at the savage end
        else if (val >= 4) cls = 'badge badge-warning';
        snarkBadge.className = cls;
    }

    // Initialize controls from the pickPricesJson meta tag (same source as maskPicks)
    try {
        const meta = document.querySelector('meta[name="pickPricesJson"]');
        if (meta) {
            const cfg = JSON.parse(meta.content);
            if (cfg.commentaryEnabled !== undefined) enableCheck.checked = cfg.commentaryEnabled;
            if (cfg.commentaryNotifications !== undefined) notifyCheck.checked = cfg.commentaryNotifications;
            if (cfg.snarkLevel !== undefined) { snarkSlider.value = cfg.snarkLevel; updateSnarkBadge(parseInt(cfg.snarkLevel, 10)); }
            else { updateSnarkBadge(parseInt(snarkSlider.value, 10)); }
            if (cfg.previewDayOfWeek !== undefined) previewSelect.value = cfg.previewDayOfWeek;
        }
    } catch (e) {
        console.error('Error parsing pickPricesJson for commentary:', e);
        updateSnarkBadge(parseInt(snarkSlider.value, 10));
    }

    function showAlert(data) {
        alertDiv.textContent = data.message || (data.success ? 'Saved' : 'Error');
        alertDiv.className = data.success ? 'alert alert-success' : 'alert alert-danger';
        alertDiv.style.display = 'block';
        setTimeout(function() { alertDiv.style.display = 'none'; }, 4000);
    }

    function postSetting(params) {
        return fetch('CommissionerServlet', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: params
        }).then(function(r) { return r.json(); });
    }

    enableCheck.addEventListener('change', function() {
        postSetting('action=toggleCommentary&commentaryEnabled=' + this.checked)
            .then(showAlert)
            .catch(function(e) { showAlert({ success: false, message: 'Error: ' + e.message }); });
    });

    notifyCheck.addEventListener('change', function() {
        postSetting('action=toggleCommentaryNotifications&commentaryNotifications=' + this.checked)
            .then(showAlert)
            .catch(function(e) { showAlert({ success: false, message: 'Error: ' + e.message }); });
    });

    // Live badge while dragging; persist on release.
    snarkSlider.addEventListener('input', function() { updateSnarkBadge(parseInt(this.value, 10)); });
    snarkSlider.addEventListener('change', function() {
        postSetting('action=setSnarkLevel&snarkLevel=' + this.value)
            .then(showAlert)
            .catch(function(e) { showAlert({ success: false, message: 'Error: ' + e.message }); });
    });

    previewSelect.addEventListener('change', function() {
        postSetting('action=setPreviewDayOfWeek&previewDayOfWeek=' + this.value)
            .then(showAlert)
            .catch(function(e) { showAlert({ success: false, message: 'Error: ' + e.message }); });
    });

    // ── Text Messaging card (SMS) ─────────────────────────────────────────
    const smsAlert = document.getElementById('smsAlert');
    function showSmsAlert(data) {
        smsAlert.textContent = data.message || (data.success ? 'Sent' : 'Error');
        smsAlert.className = data.success ? 'alert alert-success' : 'alert alert-danger';
        smsAlert.style.display = 'block';
        setTimeout(function() { smsAlert.style.display = 'none'; }, 6000);
    }
    const smsUserMessage = document.getElementById('smsUserMessage');
    if (smsUserMessage) {
        smsUserMessage.addEventListener('input', function() {
            document.getElementById('smsCharCount').textContent = this.value.length;
        });
    }
    window.sendTestSms = function() {
        const phone = document.getElementById('testPhone').value.trim();
        if (!phone) { showSmsAlert({ success: false, message: 'Enter a phone number' }); return; }
        const btn = document.getElementById('testSmsBtn');
        btn.disabled = true;
        postSetting('action=sendTestSms&phone=' + encodeURIComponent(phone))
            .then(showSmsAlert)
            .catch(function(e) { showSmsAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { btn.disabled = false; });
    };
    window.sendUserSms = function() {
        const userId = document.getElementById('smsUserSelect').value;
        const msg = smsUserMessage.value.trim();
        if (!userId) { showSmsAlert({ success: false, message: 'Select a user' }); return; }
        if (!msg) { showSmsAlert({ success: false, message: 'Enter a message' }); return; }
        const btn = document.getElementById('userSmsBtn');
        btn.disabled = true;
        postSetting('action=sendUserSms&userId=' + encodeURIComponent(userId) + '&message=' + encodeURIComponent(msg))
            .then(function(data) {
                showSmsAlert(data);
                if (data.success) { smsUserMessage.value = ''; document.getElementById('smsCharCount').textContent = '0'; }
            })
            .catch(function(e) { showSmsAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { btn.disabled = false; });
    };
    window.sendBroadcastSms = function() {
        const msg = document.getElementById('smsBroadcastMessage').value.trim();
        if (!msg) { showSmsAlert({ success: false, message: 'Enter a message' }); return; }
        if (!confirm('Send this text to ALL verified players?')) return;
        const btn = document.getElementById('broadcastSmsBtn');
        btn.disabled = true;
        postSetting('action=sendBroadcastSms&message=' + encodeURIComponent(msg))
            .then(function(data) {
                showSmsAlert(data);
                if (data.success) { document.getElementById('smsBroadcastMessage').value = ''; }
            })
            .catch(function(e) { showSmsAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { btn.disabled = false; });
    };

    // ── Email card ────────────────────────────────────────────────────────
    const emailAlert = document.getElementById('emailAlert');
    function showEmailAlert(data) {
        emailAlert.textContent = data.message || (data.success ? 'Sent' : 'Error');
        emailAlert.className = data.success ? 'alert alert-success' : 'alert alert-danger';
        emailAlert.style.display = 'block';
        setTimeout(function() { emailAlert.style.display = 'none'; }, 6000);
    }
    window.sendUserEmail = function() {
        const userId = document.getElementById('emailUserSelect').value;
        const subject = document.getElementById('emailUserSubject').value.trim();
        const msg = document.getElementById('emailUserMessage').value.trim();
        if (!userId) { showEmailAlert({ success: false, message: 'Select a user' }); return; }
        if (!subject || !msg) { showEmailAlert({ success: false, message: 'Subject and message are required' }); return; }
        const btn = document.getElementById('userEmailBtn');
        btn.disabled = true;
        postSetting('action=sendUserEmail&userId=' + encodeURIComponent(userId)
                + '&subject=' + encodeURIComponent(subject) + '&message=' + encodeURIComponent(msg))
            .then(function(data) {
                showEmailAlert(data);
                if (data.success) {
                    document.getElementById('emailUserSubject').value = '';
                    document.getElementById('emailUserMessage').value = '';
                }
            })
            .catch(function(e) { showEmailAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { btn.disabled = false; });
    };
    window.sendBroadcastEmail = function() {
        const subject = document.getElementById('emailBroadcastSubject').value.trim();
        const msg = document.getElementById('emailBroadcastMessage').value.trim();
        if (!subject || !msg) { showEmailAlert({ success: false, message: 'Subject and message are required' }); return; }
        if (!confirm('Email this to ALL players?')) return;
        const btn = document.getElementById('broadcastEmailBtn');
        btn.disabled = true;
        postSetting('action=sendBroadcastEmail&subject=' + encodeURIComponent(subject)
                + '&message=' + encodeURIComponent(msg))
            .then(function(data) {
                showEmailAlert(data);
                if (data.success) {
                    document.getElementById('emailBroadcastSubject').value = '';
                    document.getElementById('emailBroadcastMessage').value = '';
                }
            })
            .catch(function(e) { showEmailAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { btn.disabled = false; });
    };

    testBtn.addEventListener('click', function() {
        const original = testBtn.textContent;
        testBtn.disabled = true;
        testBtn.textContent = 'Generating…';
        readout.style.display = 'none';
        postSetting('action=testCommentary')
            .then(function(data) {
                showAlert(data);
                if (data.success && data.body) {
                    readout.textContent = data.body;
                    readout.style.display = 'block';
                }
            })
            .catch(function(e) { showAlert({ success: false, message: 'Error: ' + e.message }); })
            .then(function() { testBtn.disabled = false; testBtn.textContent = original; });
    });
});
</script>
</body>
</html>











