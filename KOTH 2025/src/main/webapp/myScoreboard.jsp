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
    <title>My Scoreboard</title>
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
            border: 3px solid #28a745 !important;
            box-shadow: 0 0 0 2px rgba(40, 167, 69, 0.3) !important;
        }
        .selected-2 {
            border: 3px solid #ffc107 !important;
            box-shadow: 0 0 0 2px rgba(255, 193, 7, 0.3) !important;
        }
        .selected-3 {
            border: 3px solid #dc3545 !important;
            box-shadow: 0 0 0 2px rgba(220, 53, 69, 0.3) !important;
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
                                                            <span class="pick-count">(<%= awayPickCount %>)</span>
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
                                                            <span class="pick-count">(<%= homePickCount %>)</span>
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
        </div>
    </div>

</div>

<script>
    // Auto-refresh functionality for game scores and status
    let autoRefreshInterval;
    let isRefreshing = false;

    function startAutoRefresh() {
        // Refresh every 5 minutes (300000 ms)
        autoRefreshInterval = setInterval(refreshGameData, 300000);
        console.log("Auto-refresh started - will update every 5 minutes");
    }

    function stopAutoRefresh() {
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
            console.log("Auto-refresh stopped");
        }
    }

    function refreshGameData() {
        if (isRefreshing) {
            console.log("Refresh already in progress, skipping...");
            return;
        }
        
        isRefreshing = true;
        console.log("Refreshing game data...");
        
        // Get current season and week from page attributes
        const season = '${requestScope.season}';
        const week = '${requestScope.currentWeek}';
        
        if (!season || !week) {
            console.error("Cannot find season/week values for refresh");
            isRefreshing = false;
            return;
        }
        
        // Make AJAX call to get updated game data
        fetch(`/KOTH-2025/api/games/refresh?season=${season}&week=${week}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            }
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            if (data.success) {
                updateGameCards(data.games);
                console.log("Game data refreshed successfully");
                showRefreshIndicator();
            } else {
                console.error("Server returned error:", data.message);
            }
        })
        .catch(error => {
            console.error("Error refreshing game data:", error);
        })
        .finally(() => {
            isRefreshing = false;
        });
    }

    function updateGameCards(games) {
        games.forEach(game => {
            const gameCard = document.getElementById(game.gameID);
            if (!gameCard) return;
            
            // Update scores
            const awayScoreSpan = gameCard.querySelector('.away-score-info');
            const homeScoreSpan = gameCard.querySelector('.home-score-info');
            
            if (game.status === 'Scheduled') {
                // Keep odds display for scheduled games
                if (awayScoreSpan) awayScoreSpan.textContent = `o${game.overUnder || 'N/A'}`;
                if (homeScoreSpan) {
                    const spread = game.pointSpread;
                    homeScoreSpan.textContent = spread !== null ? 
                        (spread >= 0 ? `+${spread}` : spread) : 'N/A';
                }
            } else {
                // Update scores for in-progress or final games
                if (awayScoreSpan) {
                    const isFinal = game.status === 'Final' || game.status === 'F/OT';
                    const awayWon = isFinal && game.awayScore > game.homeScore;
                    awayScoreSpan.innerHTML = awayWon ? 
                        `<span class="winning-score">${game.awayScore}</span>` : 
                        game.awayScore;
                }
                
                if (homeScoreSpan) {
                    const isFinal = game.status === 'Final' || game.status === 'F/OT';
                    const homeWon = isFinal && game.homeScore > game.awayScore;
                    homeScoreSpan.innerHTML = homeWon ? 
                        `<span class="winning-score">${game.homeScore}</span>` : 
                        game.homeScore;
                }
            }
            
            // Update game status badge
            const statusBadge = gameCard.querySelector('.badge');
            if (statusBadge) {
                statusBadge.textContent = game.status;
                statusBadge.className = 'badge ' + getBadgeClass(game.status);
            }
            
            // Update game time info for in-progress games
            const gameTimeInfo = gameCard.querySelector('.game-time-info');
            if (game.status === 'In Progress' && game.displayClock && game.period) {
                if (gameTimeInfo) {
                    gameTimeInfo.textContent = `Q${game.period} ${game.displayClock}`;
                } else {
                    // Create time info if it doesn't exist
                    const statusDiv = gameCard.querySelector('.text-center.game-status');
                    const timeDiv = document.createElement('div');
                    timeDiv.className = 'game-time-info mt-2';
                    timeDiv.textContent = `Q${game.period} ${game.displayClock}`;
                    statusDiv.appendChild(timeDiv);
                }
            } else if (gameTimeInfo) {
                // Remove time info for non-in-progress games
                gameTimeInfo.remove();
            }
        });
    }

    function getBadgeClass(status) {
        switch(status) {
            case 'Scheduled': return 'badge-success';
            case 'In Progress': return 'badge-info';
            case 'Final':
            case 'F/OT': return 'badge-primary';
            default: return 'badge-info';
        }
    }

    function showRefreshIndicator() {
        const indicator = document.createElement('div');
        indicator.id = 'refresh-indicator';
        indicator.innerHTML = '<i class="fas fa-sync-alt fa-spin"></i> Updated scores';
        indicator.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #28a745;
            color: white;
            padding: 10px 15px;
            border-radius: 5px;
            z-index: 10000;
            font-size: 14px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.3);
        `;
        document.body.appendChild(indicator);
        
        setTimeout(() => {
            const elem = document.getElementById('refresh-indicator');
            if (elem) elem.remove();
        }, 3000);
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