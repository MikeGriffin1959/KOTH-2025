<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page import="javax.servlet.*" %>
<%@ page import="javax.servlet.http.*" %>
<%@ page import="java.util.List, java.util.Map, java.util.stream.Collectors" %>
<%@ page import="model.Game" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.ZoneId" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Make Picks Body</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        #pickCountDisplay {
            margin-left: auto;
        }
        .game-time-info {
            font-weight: bold;
            font-size: 0.9rem;
        }
        .winning-score {
			color: #007bff;
		    font-size: 1.1em;
		    font-weight: bold;
		}
		@media (max-width: 768px) {
		  .container {
		    padding-left: 0px;
		    padding-right: 0px;
		    max-width: 100%;
		  }		  
		  .card {
		    margin: 10px 0;
		  }
		  .fixed-subheader {
		    top: 180px; 
		  }
		  
		  .floating-submit {
		    left: 0px; 
		  }
		  #pickCountDisplay {
		    color: white;
		    font-weight: bold;
		    position: relative;
		    margin-right: 20px;
		    font-size: 0.9rem; /* Reduced from 1.2rem */
		    padding: 3px 8px; /* Reduced from 5px 10px */
		}
		
		.fixed-subheader {
		    position: fixed;
		    top: 160px;
		    left: 0;
		    right: 0;
		    z-index: 999;
		    padding: 5px 0; /* Reduced from 10px */
		}
		
		.fixed-subheader .container {
		    display: flex;
		    justify-content: space-between;
		    align-items: center;
		    position: relative;
		}
		
		.floating-submit {
		    position: relative;
		    left: 18px;
		    top: 5px; /* Reduced from 10px */
		    z-index: 1000;
		}
		
		.floating-submit .btn {
		    border: 1px solid white;
		    padding: 4px 12px; /* Reduced padding */
		    font-size: 0.9rem; /* Reduced font size */
		}
		
		#pickCountDisplay, h5.pick-instruction {
		    color: white;
		    font-weight: bold;
		    font-size: 0.9rem; /* Reduced from 1.2rem */
		    padding: 3px 8px; /* Reduced from 5px 10px */
		    background-color: rgba(0, 0, 0, 0.75);
		    border-radius: 4px; /* Slightly reduced */
		    display: inline-block;
		    text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8); /* Reduced shadow */
		    box-shadow: 0 0 5px rgba(255, 255, 255, 0.3); /* Reduced shadow */
		    border: 1px solid white;
		}
	}
    </style>
</head>
<body>
    <%
        // Determine if we're in commissioner override mode
        boolean isCommissionerOverride = request.getAttribute("overrideUserId") != null;
	    String formAction = isCommissionerOverride ? "CommissionerOverrideServlet" : "MakePicksServlet";
	    
	    // Debug logging
	    System.out.println("Override User ID from request: " + request.getAttribute("overrideUserId"));
	    System.out.println("Override User Name from request: " + request.getAttribute("overrideUserName"));
	    System.out.println("Is Commissioner Override: " + isCommissionerOverride);
	    System.out.println("Form Action: " + formAction);
    %>

    <div class="fixed-subheader">
        <div class="container">
            <div class="floating-submit">
                <button type="button" class="btn btn-primary btn-main-menu" onclick="submitForm()">Submit</button>
            </div>
            <div id="pickCountDisplay"></div>
        </div>
    </div>

    <div class="main-content">
        <div class="container mt-3">
            <c:if test="${not empty errorMessage}">
                <div class="alert alert-danger" role="alert">
                    ${errorMessage}
                </div>
            </c:if>
            <c:if test="${not empty requestScope.message}">
                <div class="alert alert-success" role="alert">
                    ${requestScope.message}
                </div>
            </c:if>

            <div id="clientErrorAlert" class="alert alert-danger" style="display: none;" role="alert">
            </div>

           <form id="picksForm" 
			      action="<%= formAction %>" 
			      method="post" 
			      onsubmit="return validatePicks()">
			    
			    <input type="hidden" name="season" value="${requestScope.season}">
			    <input type="hidden" name="week" value="${requestScope.currentWeek}">
			    
			    <% if (isCommissionerOverride) { %>
			        <input type="hidden" name="overrideUserId" value="${requestScope.overrideUserId}">
			        <input type="hidden" name="overrideUserName" value="${requestScope.overrideUserName}">
			        <input type="hidden" name="selectedUser" value="${requestScope.selectedUser}">		        
			    <% } %>

                <div class="row">
                <%
                    List<Game> games = (List<Game>) request.getAttribute("makePicksGames");
                    Map<String, String> teamNameToAbbrev = (Map<String, String>) request.getAttribute("teamNameToAbbrev");
                    Map<String, List<String>> selectedPicks = (Map<String, List<String>>) request.getAttribute("selectedPicks");
                    Integer remainingPicks = (Integer) request.getAttribute("remainingPicks");

                    // Debug output for selectedPicks
                    System.out.println("\nJSP Debug - Selected Picks Map: " + selectedPicks);
                    if (selectedPicks != null) {
                        for (Map.Entry<String, List<String>> entry : selectedPicks.entrySet()) {
                            System.out.println("Game ID: " + entry.getKey() + ", Teams: " + entry.getValue());
                        }
                    }

                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("EEEE MM/dd/yy hh:mm a", Locale.ENGLISH);
                    
                    if (games != null && !games.isEmpty()) {
                        Comparator<Game> statusComparator = (g1, g2) -> {
                            Map<String, Integer> statusPriority = Map.of(
                                "In Progress", 1,
                                "Scheduled", 2,
                                "Final", 3,
                                "F/OT", 3
                            );
                            
                            int priority1 = statusPriority.getOrDefault(g1.getStatus(), 4);
                            int priority2 = statusPriority.getOrDefault(g2.getStatus(), 4);
                            
                            if (priority1 != priority2) {
                                return priority1 - priority2;
                            }
                            
                            return LocalDateTime.parse(g1.getDate(), DateTimeFormatter.ISO_DATE_TIME)
                                   .compareTo(LocalDateTime.parse(g2.getDate(), DateTimeFormatter.ISO_DATE_TIME));
                        };
                        
                        games = games.stream()
                                    .sorted(statusComparator)
                                    .collect(Collectors.toList());
                        
                        for (Game game : games) {
                            String awayTeamName = game.getAwayTeamName();
                            String homeTeamName = game.getHomeTeamName();
                            String awayTeamAbbr = teamNameToAbbrev.get(awayTeamName);
                            String homeTeamAbbr = teamNameToAbbrev.get(homeTeamName);
                            
                            String awayLogoPath = "images/team-Logos/" + (awayTeamAbbr != null ? awayTeamAbbr.toLowerCase() : "default") + "-logo.svg";
                            String homeLogoPath = "images/team-Logos/" + (homeTeamAbbr != null ? homeTeamAbbr.toLowerCase() : "default") + "-logo.svg";

                            // Debug current game processing
                            System.out.println("\nProcessing game ID: " + game.getGameID());
                            System.out.println("Away Team: " + awayTeamName + ", Home Team: " + homeTeamName);

                            boolean isAwaySelected = false;
                            boolean isHomeSelected = false;
                            long awaySelectionCount = 0;
                            long homeSelectionCount = 0;
                        
                            String gameId = String.valueOf(game.getGameID());
                            if (selectedPicks != null && selectedPicks.containsKey(gameId)) {
                                List<String> picks = selectedPicks.get(gameId);
                                System.out.println("Game " + gameId + " has picks: " + picks + 
                                                  " checking against away team: " + awayTeamName + 
                                                  " and home team: " + homeTeamName);
                                
                                // Check if the team name exists in the picks list
                                isAwaySelected = picks.contains(awayTeamName);
                                isHomeSelected = picks.contains(homeTeamName);
                                
                                if (isAwaySelected) {
                                    awaySelectionCount = picks.stream()
                                        .filter(pick -> pick.equals(awayTeamName))
                                        .count();
                                }
                                
                                if (isHomeSelected) {
                                    homeSelectionCount = picks.stream()
                                        .filter(pick -> pick.equals(homeTeamName))
                                        .count();
                                }
                            }
                            
                            LocalDateTime gameDateTime = LocalDateTime.parse(game.getDate(), DateTimeFormatter.ISO_DATE_TIME);
                            String formattedDate = gameDateTime.format(outputFormatter);
                            String[] dateParts = formattedDate.split(" ", 2);
                            String formattedDateWithBreak = dateParts[0] + "<br>" + dateParts[1];
                %>
                            <div class="col-md-4 mb-3">
                                <div class="card game-card" id="<%= game.getGameID() %>">
                                    <div class="card-header text-center">
                                        <strong><%= formattedDateWithBreak %></strong>
                                    </div>
                                    <div class="card-body">
                                        <div class="team-info">
                                            <div class="team-score">
                                                <div class="team-name" onclick="selectTeam(this.querySelector('.team-logo'), '<%= game.getGameID() %>', '<%= awayTeamName %>', '<%= game.getStatus() %>')">
                                                    <div class="team-logo-container">
                                                        <img src="<%= awayLogoPath %>"
                                                             alt="<%= awayTeamName %>"
                                                             class="team-logo<%= isAwaySelected ? " selected-" + awaySelectionCount : "" %>"
                                                             <%= !isCommissionerOverride && !"Scheduled".equals(game.getStatus()) ? "style=\"pointer-events: none; opacity: 0.5;\"" : "" %>>
                                                        <%= awayTeamName %>
                                                    </div>
                                                </div>
                                                <span class="away-score-info">
                                                    <% if ("Scheduled".equals(game.getStatus())) { %>
                                                        o<%= game.getOverUnder() != null ? game.getOverUnder() : "N/A" %>
                                                   <% } else { 
													    boolean isFinal = "Final".equals(game.getStatus()) || "F/OT".equals(game.getStatus());
													    boolean awayTeamWon = isFinal && game.getAwayScore() > game.getHomeScore();
													    if (awayTeamWon) { %>
													        <span class="winning-score"><%= game.getAwayScore() %></span>
													    <% } else { %>
													        <%= game.getAwayScore() %>
													    <% } 
													} %>
                                                </span>
                                            </div>
                                            <div class="team-score">
                                                <div class="team-name" onclick="selectTeam(this.querySelector('.team-logo'), '<%= game.getGameID() %>', '<%= homeTeamName %>', '<%= game.getStatus() %>')">
                                                    <div class="team-logo-container">
                                                        <img src="<%= homeLogoPath %>"
                                                             alt="<%= homeTeamName %>"
                                                             class="team-logo<%= isHomeSelected ? " selected-" + homeSelectionCount : "" %>"
                                                             <%= !isCommissionerOverride && !"Scheduled".equals(game.getStatus()) ? "style=\"pointer-events: none; opacity: 0.5;\"" : "" %>>
                                                        <%= homeTeamName %>
                                                    </div>
                                                </div>
                                                <span class="home-score-info">
                                                    <% if ("Scheduled".equals(game.getStatus())) { %>
                                                        <%= game.getPointSpread() != null ? (game.getPointSpread() >= 0 ? "+" + game.getPointSpread() : game.getPointSpread()) : "N/A" %>
                                                   <% } else {
													    boolean isFinal = "Final".equals(game.getStatus()) || "F/OT".equals(game.getStatus());
													    boolean homeTeamWon = isFinal && game.getHomeScore() > game.getAwayScore();
													    if (homeTeamWon) { %>
													        <span class="winning-score"><%= game.getHomeScore() %></span>
													    <% } else { %>
													        <%= game.getHomeScore() %>
													    <% }
													} %>
                                                </span>
                                            </div>
                                            <div class="text-center game-status">
                                                <% 
                                                    String displayStatus = game.getStatus() != null ? 
                                                        game.getStatus().replace("STATUS_", "").replace("_", " ") : "Unknown";
                                                %>
                                                <span class="badge <%= "Scheduled".equals(displayStatus) ? "badge-success" : 
                                                               "In Progress".equals(displayStatus) ? "badge-info" : 
                                                               "Final".equals(displayStatus) || "F/OT".equals(displayStatus) ? "badge-primary" : "badge-info" %>">
                                                    <%= displayStatus %>
                                                </span>
                                                <% if ("In Progress".equals(displayStatus) && game.getDisplayClock() != null && game.getPeriod() != null) { %>
                                                    <div class="game-time-info mt-2">
                                                        Q<%= game.getPeriod() %> <%= game.getDisplayClock() %>
                                                    </div>
                                                <% } %>
                                            </div>
                                        </div>
                                    </div>
                                 </div>
                            </div>
                <%
                        }
                    } else {
                %>
                        <div class="col-12">
                            <p class="text-center">No games available for the selected week and season.</p>
                        </div>
                <%
                    }
                %>
                </div>
            </form>
        </div>
    </div>

<script>
    var remainingPicks = ${requestScope.remainingPicks != null ? requestScope.remainingPicks : 0};
    var totalSelectedPicks = 0;
    var gameSelections = {};

    function isCommissionerOverride() {
        // Look for the override user ID hidden input instead of checking URL
        const overrideInput = document.querySelector('input[name="overrideUserId"]');
        return overrideInput !== null;
    }

    function selectTeam(element, gameId, teamName, gameStatus) {
        console.log("selectTeam called:", {element, gameId, teamName, gameStatus});
        
        if (!isCommissionerOverride() && gameStatus !== "Scheduled") {
            console.log("Game is not scheduled. Picks are locked for non-commissioners.");
            return;
        }

        if (!element.classList.contains('team-logo')) {
            element = element.querySelector('.team-logo');
        }
        console.log("Remaining picks before selection:", remainingPicks);
        console.log("Total selected picks before selection:", totalSelectedPicks);

        let currentSelection = 0;
        if (element.classList.contains('selected-1')) currentSelection = 1;
        else if (element.classList.contains('selected-2')) currentSelection = 2;
        else if (element.classList.contains('selected-3')) currentSelection = 3;

        console.log("Current selection:", currentSelection);

        let nextSelection = (currentSelection + 1) % 4;
        console.log("Next selection:", nextSelection);

        element.classList.remove('selected-1', 'selected-2', 'selected-3');
        if (nextSelection > 0) {
            element.classList.add('selected-' + nextSelection);
        }

        if (!gameSelections[gameId]) {
            gameSelections[gameId] = {};
        }

        if (nextSelection > 0) {
            gameSelections[gameId][teamName] = nextSelection;
        } else {
            delete gameSelections[gameId][teamName];
            if (Object.keys(gameSelections[gameId]).length === 0) {
                delete gameSelections[gameId];
            }
        }

        console.log("Updated gameSelections:", gameSelections);

        totalSelectedPicks = Object.values(gameSelections).reduce((sum, game) => {
            return sum + Object.values(game).reduce((gameSum, count) => gameSum + count, 0);
        }, 0);

        console.log("Updated totalSelectedPicks:", totalSelectedPicks);

        updateHiddenInputs();
        updatePickCountDisplay();
    }

    function updateHiddenInputs() {
        console.log("Updating hidden inputs");
        
        // Remove only pick inputs, preserve other hidden fields
        document.querySelectorAll('input[name^="pick_"]').forEach(el => el.remove());

        // Add new pick inputs
        for (let [gameId, teams] of Object.entries(gameSelections)) {
            for (let [teamName, count] of Object.entries(teams)) {
                let input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'pick_' + gameId;
                input.value = teamName + '_' + count;
                document.getElementById('picksForm').appendChild(input);
                console.log("Added hidden input:", input.name, "=", input.value);
            }
        }

        // Log form data for debugging
        if (isCommissionerOverride()) {
            const form = document.getElementById('picksForm');
            console.log("Form action:", form.action);
            console.log("Override User ID:", form.elements['overrideUserId']?.value);
            console.log("Override User Name:", form.elements['overrideUserName']?.value);
            console.log("Selected User:", form.elements['selectedUser']?.value);
        }
    }

    function initializeTotalSelectedPicks() {
        console.log("Starting initialization of selected picks");
        gameSelections = {};
        totalSelectedPicks = 0;
        
        document.querySelectorAll('.card').forEach(card => {
            let gameId = card.id;
            console.log("Processing game card:", gameId);
            gameSelections[gameId] = {};
            
            let logos = card.querySelectorAll('.team-logo');
            logos.forEach(logo => {
                console.log("Processing logo:", logo);
                console.log("Logo classes:", logo.className);
                console.log("Logo alt (team name):", logo.alt);
                
                let isSelected = logo.classList.contains('selected-1') || 
                               logo.classList.contains('selected-2') || 
                               logo.classList.contains('selected-3');
                               
                console.log("Is logo selected:", isSelected);

                if (isSelected) {
                    let selectionCount = 1;
                    if (logo.classList.contains('selected-2')) selectionCount = 2;
                    if (logo.classList.contains('selected-3')) selectionCount = 3;
                    
                    gameSelections[gameId][logo.alt] = selectionCount;
                    totalSelectedPicks += selectionCount;
                    console.log(`Added selection for ${logo.alt}, count: ${selectionCount}`);
                }
            });
            
            console.log("Game selections after processing card:", gameSelections[gameId]);
        });
        
        console.log("Final gameSelections:", gameSelections);
        console.log("Final totalSelectedPicks:", totalSelectedPicks);
        
        updatePickCountDisplay();
        updateHiddenInputs();
    }

    function updatePickCountDisplay() {
        var pickCountDisplay = document.getElementById('pickCountDisplay');
        pickCountDisplay.innerHTML = remainingPicks + ' Remaining Picks <br>' + 
                                   totalSelectedPicks + ' Selected Picks';
        console.log("Updated pick count display. Selected:", totalSelectedPicks, 
                    "Remaining (Start of week):", remainingPicks);
    }

    function disableAllSelections() {
        document.querySelectorAll('.team-logo').forEach(logo => {
            logo.style.pointerEvents = 'none';
            logo.style.opacity = '0.5';
        });
        document.querySelector('.floating-submit').style.display = 'none';
    }

    function disableNonScheduledGames() {
        if (isCommissionerOverride()) {
            console.log("Commissioner override active - all games remain enabled");
            return;
        }

        document.querySelectorAll('.game-card').forEach(card => {
            let status = card.querySelector('.badge').textContent.trim();
            if (status !== "Scheduled") {
                card.querySelectorAll('.team-logo').forEach(logo => {
                    logo.style.pointerEvents = 'none';
                    logo.style.opacity = '0.5';
                });
            }
        });
    }

    function validatePicks() {
        var clientErrorAlert = document.getElementById('clientErrorAlert');

        if (!isCommissionerOverride() && totalSelectedPicks > remainingPicks) {
            var errorMessage = 'You have selected more than your remaining picks (' + remainingPicks + '). Please adjust your selections.';
            clientErrorAlert.textContent = errorMessage;
            clientErrorAlert.style.display = 'block';
            clientErrorAlert.scrollIntoView({behavior: 'smooth', block: 'center'});
            return false;
        } else if (!isCommissionerOverride() && totalSelectedPicks < remainingPicks) {
            var warningMessage = 'You have ' + (remainingPicks - totalSelectedPicks) + 
                ' picks remaining. Are you sure you want to submit?';
            if (!confirm(warningMessage)) {
                return false;
            }
        }

        clientErrorAlert.style.display = 'none';
        return true;
    }

    function submitForm() {
        if (validatePicks()) {
            document.getElementById('picksForm').submit();
        }
    }

    function handleSuccessMessage() {
        const successMessage = document.querySelector('.alert-success');
        if (successMessage) {
            console.log("Found success message, setting timeout");
            setTimeout(function() {
                successMessage.style.transition = 'opacity 1s';
                successMessage.style.opacity = '0';
                setTimeout(function() {
                    successMessage.style.display = 'none';
                    // If in commissioner override mode, redirect back to commissioner page
                    if (isCommissionerOverride()) {
                        window.location.href = 'CommissionerServlet';
                    }
                }, 1000);
            }, 2000); // Show message for 2 seconds before fade
        }
    }

    window.onload = function() {
        console.log("Initial remaining picks:", remainingPicks);
        initializeTotalSelectedPicks();
        console.log("Total selected picks after initialization:", totalSelectedPicks);
        
        if (!isCommissionerOverride() && remainingPicks <= 0) {
            console.log("No remaining picks. Disabling selections.");
            disableAllSelections();
        } else {
            if (!isCommissionerOverride()) {
                disableNonScheduledGames();
            }
        }

        handleSuccessMessage();
    };
</script>
</body>
</html>