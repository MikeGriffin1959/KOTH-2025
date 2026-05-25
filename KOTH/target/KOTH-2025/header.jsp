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
/* Layout & Container Styles */
.container .nav-bar .nav.nav-tabs {
  display: flex !important;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 4px;
  border-bottom: none;
}

.container .nav-bar .nav.nav-tabs .nav-item {
  margin: 0;
  border: none;
}

/* Base Nav Link Styles */
.container .nav-bar .nav.nav-tabs .nav-item .nav-link {
  min-width: 40px;
  min-height: 40px;
  display: flex !important;
  flex-direction: column;
  align-items: left;
  justify-content: center;
  padding: 8px;
  background-color: black;
  position: relative;
  margin-bottom: -1px;
  border: none;
}

/* Icon Styles */
.container .nav-bar .nav.nav-tabs .nav-item .nav-link .icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

/* Page Name Styles */
.container .nav-bar .nav.nav-tabs .nav-item .nav-link .page-name {
  font-size: 12px;
  text-align: center;
  margin-top: auto;
}

/* Active State Styles */
.container .nav-bar .nav.nav-tabs .nav-item .nav-link.active,
.nav-tabs .nav-link.active {
  background-color: white !important;
  color: #000000 !important;
  border: none !important;
  z-index: 1;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.5);
}
/* Small Screens Styles */
@media (max-width: 767px) {
    /* Utility Bar */
    .utility-bar {
        padding: 5px;
        margin-bottom: 10px;
    }

    .koth-info {
        font-size: 0.9rem;
        padding: 3px 8px;
        border-radius: 4px;
        box-shadow: 0 0 5px rgba(255, 255, 255, 0.3);
    }

    .user-info {
        padding: 3px 8px;
        border-radius: 4px;
        box-shadow: 0 0 5px rgba(255, 255, 255, 0.3);
    }

    .user-info-top {
        margin-bottom: 3px;
    }

    .user-info i {
        margin-right: 8px;
        font-size: 1rem;
    }

    .user-info a,
    .user-info a.username {
        font-size: 0.9rem;
        text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8);
    }

    /* Fixed Subheader */
    #pickCountDisplay {
        font-size: 0.9rem;
        padding: 3px 8px;
    }

    .fixed-subheader {
        padding: 5px 0;
    }

    .floating-submit {
        top: 5px;
    }

    .floating-submit .btn {
        padding: 4px 12px;
        font-size: 0.9rem;
    }

    #pickCountDisplay, 
    h5.pick-instruction {
        font-size: 0.9rem;
        padding: 3px 8px;
        border-radius: 4px;
        text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8);
        box-shadow: 0 0 5px rgba(255, 255, 255, 0.3);
    }
}

/* Medium Screen Styles */
@media screen and (min-width: 768px) {
  .container .nav-bar .nav.nav-tabs .nav-item .nav-link {
    min-width: 80px;
    min-height: 80px;
  }

  .container .nav-bar .nav.nav-tabs .nav-item .nav-link .icon i,
  .container .nav-bar .nav.nav-tabs .nav-item .nav-link i {
    font-size: 40px;
  }

  .container .nav-bar .nav.nav-tabs .nav-item .nav-link .page-name {
    font-size: 14px;
  }
}

/* Large Screen Styles */
@media screen and (min-width: 992px) {
  .container .nav-bar .nav.nav-tabs .nav-item .nav-link {
    min-width: 120px;
    min-height: 120px;
  }

  .container .nav-bar .nav.nav-tabs .nav-item .nav-link .icon i,
  .container .nav-bar .nav.nav-tabs .nav-item .nav-link i {
    font-size: 60px;
  }

  .container .nav-bar .nav.nav-tabs .nav-item .nav-link .page-name {
    font-size: 16px;
  }
}
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
        <div class="nav-bar">
            <ul class="nav nav-tabs">
                <li class="nav-item">
                    <a class="nav-link" href="HomeServlet">
                        <i class="fas fa-home"></i>
                        <span class="page-name">Home</span>
                    </a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" href="MakePicksServlet">
                        <i class="fa-solid fa-football"></i>
                        <span class="page-name">Make Picks</span>
                    </a>
                </li>
                <c:if test="${not empty sessionScope.isCommish and sessionScope.isCommish eq true}">
                    <li class="nav-item">
                        <a class="nav-link" href="CommissionerServlet">
                            <i class="fas fa-fas fa-tools"></i>
                            <span class="page-name">Commissioner</span>
                        </a>
                    </li>
                </c:if>
            </ul>
        </div>
    </div>

    <script src="https://code.jquery.com/jquery-3.5.1.slim.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.5.3/dist/umd/popper.min.js"></script>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/js/bootstrap.min.js"></script>
    <script>
        // Add active class to current page's tab
        $(document).ready(function() {
            $('.nav-tabs .nav-link').each(function() {
                var linkHref = this.href.split('?')[0]; // Remove query parameters
                var currentUrl = window.location.href.split('?')[0]; // Remove query parameters
                if (currentUrl.includes(linkHref)) {
                    $(this).addClass('active');
                }
            });
        });
    </script>
</body>
</html>