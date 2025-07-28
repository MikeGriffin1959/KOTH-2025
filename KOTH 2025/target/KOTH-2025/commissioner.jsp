<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
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
    <title>Commissioner Dashboard</title>
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
			                        <option value="2023">2023</option>
			                        <option value="2024">2024</option>
			                        <option value="2025">2025</option>
			                        <option value="2026">2026</option>
			                    </select>
			                </div>
			                <div class="form-group">
			                    <label for="week">Week:</label>
			                    <select class="form-control" id="week" name="week">
			                        <option value="">Select</option>
			                        <% for (int i = 0; i <= 22; i++) { %>
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
                                                <input type="checkbox" name="userCommish_${user.idUser}" ${user.commish ? "checked" : ""}>
                                            </td>
                                            <td>
                                                <input type="checkbox" name="userPicksPaid_${user.idUser}" ${user.picksPaid ? "checked" : ""}>
                                            </td>
                                            <td>
                                                <select name="initialPicks_${user.idUser}">
                                                    <c:forEach var="i" begin="0" end="3">
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
</body>
</html>











