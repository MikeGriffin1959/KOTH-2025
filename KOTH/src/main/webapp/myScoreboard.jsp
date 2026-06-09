<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page import="jakarta.servlet.*" %>
<%@ page import="jakarta.servlet.http.*" %>
<%@ page import="java.util.List, java.util.Map, java.util.stream.Collectors" %>
<%@ page import="java.util.ArrayList, java.util.Collections" %>
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
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        .game-time-info {
            font-weight: bold;
            font-size: 0.9rem;
        }
        .winning-score {
            color: #007bff;
            font-size: 1.1em;
            font-weight: bold;
        }
        .selected-1 {
            border: 1px solid #007bff !important;
            box-shadow: 0 0 0 2px #007bff !important;
        }
        .selected-2 {
            border: 1px solid #007bff !important;
            box-shadow: 0 0 0 2px #007bff, 0 0 0 5px black, 0 0 0 8px #007bff !important;
        }
        .selected-3 {
            border: 1px solid #007bff !important;
            box-shadow: 0 0 0 2px #007bff, 0 0 0 5px black, 0 0 0 8px #007bff, 0 0 0 11px black, 0 0 0 14px #007bff !important;
        }
        .team-logo-container {
            position: relative;
        }
        .scoreboard-header {
            background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
            color: white;
            padding: 20px 0;
            margin-bottom: 20px;
        }
        .pick-count {
            color: #6c757d;
            font-size: 0.9em;
            font-weight: normal;
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
        }
    </style>
</head>
<body>
<div class="container">

    <jsp:include page="header.jsp">
        <jsp:param name="pageTitle" value="My Scoreboard" />
    </jsp:include>
    
    <%
 		// Mask Picks attribute
        Boolean maskPicks = (Boolean) request.getAttribute("maskPicks");
        if (maskPicks == null) maskPicks = false;
    %>
    
    <div class="main-content">
        <div class="container mt-3">
            <c:if test="${not empty errorMessage}">
                <div class="alert alert-danger" role="alert">
                    ${errorMessage}
                </div>
            </c:if>

            <div class="row">
                <%
                    List<Game> games = (List<Game>) request.getAttribute("myScorboardGames");
                    Map<String, String> teamNameToAbbrev = (Map<String, String>) request.getAttribute("teamNameToAbbrev");
                    Map<String, List<String>> selectedPicks = (Map<String, List<String>>) request.getAttribute("selectedPicks");
                    Map<String, Integer> teamPickCounts = (Map<String, Integer>) request.getAttribute("teamPickCounts");
                    
                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("EEEE MM/dd/yy hh:mm a", Locale.ENGLISH);
                    
                    if (games != null && !games.isEmpty()) {
                        // Create a custom comparator based on user picks and popularity
                        Comparator<Game> customComparator = (g1, g2) -> {
                            String gameId1 = String.valueOf(g1.getGameID());
                            String gameId2 = String.valueOf(g2.getGameID());
                            
                            // Check if user has picks for these games
                            boolean userHasPick1 = false;
                            boolean userHasPick2 = false;
                            
                            if (selectedPicks != null) {
                                List<String> picks1 = selectedPicks.get(gameId1);
                                List<String> picks2 = selectedPicks.get(gameId2);
                                userHasPick1 = picks1 != null && !picks1.isEmpty();
                                userHasPick2 = picks2 != null && !picks2.isEmpty();
                            }
                            
                            // Calculate popularity scores for games
                            int popularity1 = 0;
                            int popularity2 = 0;
                            
                            if (teamPickCounts != null) {
                                Integer awayPicks1 = teamPickCounts.get(g1.getAwayTeamName());
                                Integer homePicks1 = teamPickCounts.get(g1.getHomeTeamName());
                                Integer awayPicks2 = teamPickCounts.get(g2.getAwayTeamName());
                                Integer homePicks2 = teamPickCounts.get(g2.getHomeTeamName());
                                
                                popularity1 = (awayPicks1 != null ? awayPicks1 : 0) + (homePicks1 != null ? homePicks1 : 0);
                                popularity2 = (awayPicks2 != null ? awayPicks2 : 0) + (homePicks2 != null ? homePicks2 : 0);
                            }
                            
                            // Priority 1: User's picks vs non-user picks
                            if (userHasPick1 && !userHasPick2) return -1;
                            if (!userHasPick1 && userHasPick2) return 1;
                            
                            // Priority 2: For user picks OR for non-user picks, sort by status importance
                            Map<String, Integer> statusPriority = Map.of(
                                "In Progress", 1,
                                "Scheduled", 2,
                                "Final", 3,
                                "F/OT", 3
                            );
                            
                            int status1 = statusPriority.getOrDefault(g1.getStatus(), 4);
                            int status2 = statusPriority.getOrDefault(g2.getStatus(), 4);
                            
                            if (status1 != status2) {
                                return status1 - status2;
                            }
                            
                            // Priority 3: For scheduled games, sort by date (earliest first)
                            if ("Scheduled".equals(g1.getStatus()) && "Scheduled".equals(g2.getStatus())) {
                                return LocalDateTime.parse(g1.getDate(), DateTimeFormatter.ISO_DATE_TIME)
                                       .compareTo(LocalDateTime.parse(g2.getDate(), DateTimeFormatter.ISO_DATE_TIME));
                            }
                            
                            // Priority 4: For final games, sort by date (most recent first)
                            if (("Final".equals(g1.getStatus()) || "F/OT".equals(g1.getStatus())) && 
                                ("Final".equals(g2.getStatus()) || "F/OT".equals(g2.getStatus()))) {
                                return LocalDateTime.parse(g2.getDate(), DateTimeFormatter.ISO_DATE_TIME)
                                       .compareTo(LocalDateTime.parse(g1.getDate(), DateTimeFormatter.ISO_DATE_TIME));
                            }
                            
                            // Priority 5: If neither user has picks, sort by popularity (most popular first)
                            if (!userHasPick1 && !userHasPick2) {
                                if (popularity1 != popularity2) {
                                    return popularity2 - popularity1; // Higher popularity first
                                }
                            }
                            
                            // Final fallback: sort by game date
                            return LocalDateTime.parse(g1.getDate(), DateTimeFormatter.ISO_DATE_TIME)
                                   .compareTo(LocalDateTime.parse(g2.getDate(), DateTimeFormatter.ISO_DATE_TIME));
                        };
                        
                        games = games.stream()
                                    .sorted(customComparator)
                                    .collect(Collectors.toList());
                        
                        for (Game game : games) {
                            String awayTeamName = game.getAwayTeamName();
                            String homeTeamName = game.getHomeTeamName();
                            String awayTeamAbbr = teamNameToAbbrev.get(awayTeamName);
                            String homeTeamAbbr = teamNameToAbbrev.get(homeTeamName);
                            
                            String awayLogoPath = "images/team-Logos/" + (awayTeamAbbr != null ? awayTeamAbbr.toLowerCase() : "default") + "-logo.svg";
                            String homeLogoPath = "images/team-Logos/" + (homeTeamAbbr != null ? homeTeamAbbr.toLowerCase() : "default") + "-logo.svg";

                            boolean isAwaySelected = false;
                            boolean isHomeSelected = false;
                            long awaySelectionCount = 0;
                            long homeSelectionCount = 0;
                        
                            String gameId = String.valueOf(game.getGameID());
                            if (selectedPicks != null && selectedPicks.containsKey(gameId)) {
                                List<String> picks = selectedPicks.get(gameId);
                                
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
                            
                            // Get pick counts for each team
                            Integer awayPickCount = teamPickCounts != null ? teamPickCounts.get(awayTeamName) : null;
                            Integer homePickCount = teamPickCounts != null ? teamPickCounts.get(homeTeamName) : null;
                            
                            //  Mask Picks: determine if pick counts should be hidden for this game
                            boolean maskThisGame = maskPicks && "Scheduled".equals(game.getStatus());
                            
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
                                                <div class="team-name">
                                                    <div class="team-logo-container">
                                                        <img src="<%= awayLogoPath %>"
                                                             alt="<%= awayTeamName %>"
                                                             class="team-logo<%= isAwaySelected ? " selected-" + awaySelectionCount : "" %>">
                                                        <%= awayTeamName %>
                                                        <% if (awayPickCount != null && awayPickCount > 0) { %>
                                                            <span class="pick-count">(<%= maskThisGame ? "?" : awayPickCount %>)</span>
                                                        <% } %>
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
                                                <div class="team-name">
                                                    <div class="team-logo-container">
                                                        <img src="<%= homeLogoPath %>"
                                                             alt="<%= homeTeamName %>"
                                                             class="team-logo<%= isHomeSelected ? " selected-" + homeSelectionCount : "" %>">
                                                        <%= homeTeamName %>
                                                        <% if (homePickCount != null && homePickCount > 0) { %>
                                                            <span class="pick-count">(<%= maskThisGame ? "?" : homePickCount %>)</span>
                                                        <% } %>
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
												        <% 
												            String period = String.valueOf(game.getPeriod());
												            String clock = game.getDisplayClock();
												            
												            if ("2".equals(period) && "0:00".equals(clock)) { 
												        %>
												            Halftime
												        <% } else if (Integer.parseInt(period) > 4) { %>
												            OT <%= clock %>
												        <% } else { %>
												            Q<%= period %> <%= clock %>
												        <% } %>
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
        </div>
    </div>

</div>

<script>
//Auto-refresh functionality for My Scoreboard - Full page reload
let autoRefreshInterval;

function startAutoRefresh() {
    // Refresh every 30 seconds with full page reload
    autoRefreshInterval = setInterval(refreshPage, 30000);
    console.log("Auto-refresh started - full page reload every 30 seconds");
}

function stopAutoRefresh() {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
        console.log("Auto-refresh stopped");
    }
}

function refreshPage() {
    console.log("Refreshing page...");
    
    // Show a subtle refresh indicator before reload
    showRefreshIndicator();
    
    // Small delay to show the indicator, then reload
    setTimeout(() => {
        window.location.reload();
    }, 500);
}

function showRefreshIndicator() {
    const indicator = document.createElement('div');
    indicator.id = 'refresh-indicator';
    indicator.innerHTML = '<i class="fas fa-sync-alt fa-spin"></i> Updating...';
    indicator.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #007bff;
        color: white;
        padding: 8px 12px;
        border-radius: 4px;
        z-index: 10000;
        font-size: 12px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.3);
        opacity: 0.9;
    `;
    document.body.appendChild(indicator);
}

window.onload = function() {
    console.log("My Scoreboard loaded");
    
    // Start auto-refresh
    startAutoRefresh();
    
    // Add visibility change handler to pause/resume refresh when tab is not visible
    document.addEventListener('visibilitychange', function() {
        if (document.hidden) {
            stopAutoRefresh();
            console.log("Tab hidden - auto-refresh paused");
        } else {
            startAutoRefresh();
            console.log("Tab visible - auto-refresh resumed");
        }
    });
};
</script>
</body>
</html>

<%@ include file="footer.jsp" %>