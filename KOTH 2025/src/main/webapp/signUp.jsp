<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sign Up</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        ::placeholder {
            color: #999;
            opacity: 1;
        }
        .form-control {
            max-width: 225px;
        }
        #initialPicks {
            width: auto;
        }
        .password-requirements {
            font-size: 0.875rem;
            color: #6c757d;
            margin-top: 0.25rem;
        }
    </style>
</head>
<body>
    <div class="content container">
        <h1 class="text-center mt-5">Sign Up</h1>
        <c:if test="${not empty error}">
            <div class="alert alert-danger" role="alert">
                <c:out value="${error}" />
            </div>
        </c:if>
    </div>

    <div class="container">
        <form action="SignUpServlet" method="post" id="signUpForm">
            <div class="form-group">
                <label for="firstName">First Name</label>
                <input type="text" 
                       class="form-control" 
                       id="firstName" 
                       name="firstName" 
                       required 
                       maxlength="20" 
                       placeholder="e.g., John"
                       value="<c:out value="${firstName}" />"
                >
            </div>
            <div class="form-group">
                <label for="lastName">Last Name</label>
                <input type="text" 
                       class="form-control" 
                       id="lastName" 
                       name="lastName" 
                       required 
                       maxlength="20" 
                       placeholder="e.g., Doe"
                       value="<c:out value="${lastName}" />"
                >
            </div>
            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" 
                       class="form-control" 
                       id="username" 
                       name="username" 
                       required 
                       maxlength="20" 
                       placeholder="e.g., johndoe123"
                       value="<c:out value="${username}" />"
                >
            </div>
            <div class="form-group">
                <label for="email">Email</label>
                <input type="email" 
                       class="form-control" 
                       id="email" 
                       name="email" 
                       required 
                       maxlength="40" 
                       placeholder="e.g., john@example.com"
                       value="<c:out value="${email}" />"
                >
            </div>
            <div class="form-group">
                <label for="cellNumber">Cell Number</label>
                <input type="tel" 
                       class="form-control" 
                       id="cellNumber" 
                       name="cellNumber" 
                       maxlength="20" 
                       placeholder="e.g., 123-456-7890"
                       value="<c:out value="${cellNumber}" />"
                >
            </div>
            <div class="form-group position-relative">
			    <label for="password">Password</label>
			    <div class="input-group">
			        <input type="password" 
			               class="form-control" 
			               id="password" 
			               name="password" 
			               required 
			               maxlength="20" 
			               placeholder="Enter your password"
			               pattern="(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&]?)[A-Za-z\d@$!%*#?&]{8,}"
			        >
			        <div class="input-group-append">
			            <button class="btn btn-outline-secondary" type="button" id="togglePassword">
			                Show
			            </button>
			        </div>
			    </div>
			    <small class="form-text">
			        Password must be at least 8 characters long and contain at least one letter and one number
			    </small>
			</div>
			
			<div class="form-group position-relative">
			    <label for="reenterPassword">Re-enter Password</label>
			    <div class="input-group">
			        <input type="password" 
			               class="form-control" 
			               id="reenterPassword" 
			               name="reenterPassword" 
			               required 
			               maxlength="20" 
			               placeholder="Re-enter your password"
			        >
			        <div class="input-group-append">
			            <button class="btn btn-outline-secondary" type="button" id="toggleReenterPassword">
			                Show
			            </button>
			        </div>
			    </div>
			</div>
			    <div class="card" style="max-width: 225px;">
			        <div class="card-header">
			            Pick Prices
			        </div>
			        <ul class="list-group list-group-flush">
			            <c:if test="${not empty pickPrice1}">
			                <li class="list-group-item d-flex justify-content-between align-items-center">
			                    <span class="text-dark">1st Pick</span>
			                    <span class="badge badge-primary badge-pill">$${pickPrice1}</span>
			                </li>
			            </c:if>
			            <c:if test="${not empty pickPrice2 && maxPicks >= 2}">
			                <li class="list-group-item d-flex justify-content-between align-items-center">
			                    <span class="text-dark">2nd Pick</span>
			                    <span class="badge badge-primary badge-pill">$${pickPrice2}</span>
			                </li>
			            </c:if>
			            <c:if test="${not empty pickPrice3 && maxPicks >= 3}">
			                <li class="list-group-item d-flex justify-content-between align-items-center">
			                    <span class="text-dark">3rd Pick</span>
			                    <span class="badge badge-primary badge-pill">$${pickPrice3}</span>
			                </li>
			            </c:if>
			            <c:if test="${not empty pickPrice4 && maxPicks >= 4}">
			                <li class="list-group-item d-flex justify-content-between align-items-center">
			                    <span class="text-dark">4th Pick</span>
			                    <span class="badge badge-primary badge-pill">$${pickPrice4}</span>
			                </li>
			            </c:if>
			            <c:if test="${not empty pickPrice5 && maxPicks >= 5}">
			                <li class="list-group-item d-flex justify-content-between align-items-center">
			                    <span class="text-dark">5th Pick</span>
			                    <span class="badge badge-primary badge-pill">$${pickPrice5}</span>
			                </li>
			            </c:if>
			        </ul>
			    </div>				
  	  				<div class="form-group">
					    <label for="initialPicks">Number of Initial Picks</label>
					    <select class="form-control" id="initialPicks" name="initialPicks">
					        <c:forEach begin="1" end="${maxPicks}" var="i">
					            <option value="${i}" <c:if test="${initialPicks eq i}">selected</c:if>>${i}</option>
					        </c:forEach>
					    </select>
					</div>

					
					<div class="mb-3">
					    <span class="badge badge-primary badge-pill" id="selectedPrice" style="font-size: 1.25rem;">$${pickPrice1}.00</span>
					</div>

				<button type="submit" class="btn btn-primary">Sign Up</button>
            
        </form>
    </div>

<script>
// Store prices in JavaScript variables
const pickPrices = {
    1: ${pickPrice1},
    2: ${pickPrice2},
    3: ${pickPrice3},
    4: ${pickPrice4},
    5: ${pickPrice5}
};

// Calculate total price for number of picks selected
function calculateTotalPrice(numberOfPicks) {
    let total = 0;
    for (let i = 1; i <= numberOfPicks; i++) {
        if (pickPrices[i]) {
            total += parseFloat(pickPrices[i]);
        }
    }
    return total.toFixed(2); // Format to 2 decimal places
}

// Form submission handler
document.getElementById('signUpForm').addEventListener('submit', function(event) {
    event.preventDefault();
    var password = document.getElementById("password").value.trim();
    var reenterPassword = document.getElementById("reenterPassword").value.trim();
    
    if (password !== reenterPassword) {
        alert("Passwords do not match.");
        return;
    }
    
    this.submit();
});

// Toggle password visibility
function setupPasswordToggle(buttonId, inputId) {
    const toggleButton = document.getElementById(buttonId);
    const input = document.getElementById(inputId);
    
    toggleButton.addEventListener('click', function() {
        const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
        input.setAttribute('type', type);
        toggleButton.textContent = type === 'password' ? 'Show' : 'Hide';
    });
}

// Setup toggle for both password fields
setupPasswordToggle('togglePassword', 'password');
setupPasswordToggle('toggleReenterPassword', 'reenterPassword');

// Update price display when selection changes
document.getElementById('initialPicks').addEventListener('change', function() {
    const selectedPicks = parseInt(this.value);
    const priceDisplay = document.getElementById('selectedPrice');
    const totalPrice = calculateTotalPrice(selectedPicks);
    priceDisplay.textContent = '$' + totalPrice;
});

// Set initial price display
window.addEventListener('DOMContentLoaded', function() {
    const initialPicks = parseInt(document.getElementById('initialPicks').value);
    const priceDisplay = document.getElementById('selectedPrice');
    const totalPrice = calculateTotalPrice(initialPicks);
    priceDisplay.textContent = '$' + totalPrice;
});
</script>

</body>
</html>

<%@ include file="footer.jsp" %>