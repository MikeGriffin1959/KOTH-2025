<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*, java.time.LocalDateTime, java.time.format.DateTimeFormatter" %>
<%@ page errorPage="error.jsp" %>
<%
System.out.println("home.jsp: Starting home.jsp");

// Retrieve attributes set by HomeServlet
String currentSeason = (String) request.getAttribute("currentSeason");
String currentWeekStr = (String) request.getAttribute("currentWeek");

System.out.println("home.jsp: Retrieved attributes - Season: " + currentSeason +
        ", Week: " + currentWeekStr);

if (currentSeason == null) currentSeason = String.valueOf(java.time.Year.now().getValue());
if (currentWeekStr == null) currentWeekStr = "1";
int currentWeek = Integer.parseInt(currentWeekStr);

//Initialize playoff round names
Map<Integer, String> playoffRoundNames = new HashMap<>();
playoffRoundNames.put(19, "W");
playoffRoundNames.put(20, "DV"); 
playoffRoundNames.put(21, "CC");
playoffRoundNames.put(22, "SB");

//Use 1 as the start week for regular season
int startWeek = 1;
int endWeek = currentWeek > 18 ? 22 : currentWeek;

System.out.println("home.jsp: Using values - Season: " + currentSeason + 
        ", StartWeek: " + startWeek + ", EndWeek: " + endWeek);

//Determine week labels
String[] weekLabels = new String[endWeek - startWeek + 1];
for (int i = startWeek; i <= endWeek; i++) {
	weekLabels[i - startWeek] = i > 18 ? playoffRoundNames.get(i) : String.valueOf(i);
}
Boolean userHasPaid = (Boolean) request.getAttribute("userHasPaid");

// Retrieve other data attributes
Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData =
    (Map<Integer, Map<String, List<Map<String, Object>>>>) request.getAttribute("optimizedData");
if (optimizedData == null) optimizedData = new HashMap<>();

Map<String, Integer> initialPicks = (Map<String, Integer>) request.getAttribute("initialPicks");
if (initialPicks == null) initialPicks = new HashMap<>();

Map<String, Integer> userLosses = (Map<String, Integer>) request.getAttribute("userLosses");
if (userLosses == null) userLosses = new HashMap<>();

Map<String, String> teamNameToAbbrev = (Map<String, String>) request.getAttribute("teamNameToAbbrev");
if (teamNameToAbbrev == null) teamNameToAbbrev = new HashMap<>();

List<String> allUsers = (List<String>) request.getAttribute("allUsers");
if (allUsers == null) allUsers = new ArrayList<>();

Map<String, String> userFullNames = (Map<String, String>) request.getAttribute("userFullNames");
if (userFullNames == null) userFullNames = new HashMap<>();

Map<String, Boolean> teamResults = (Map<String, Boolean>) request.getAttribute("teamResults");
if (teamResults == null) teamResults = new HashMap<>();

%>

<%!
private List<String> sortUsersByRemainingPicks(List<String> users, Map<String, Integer> initialPicks, Map<String, Integer> userLosses) {
    return users.stream()
        .sorted((u1, u2) -> {
            int remainingPicks1 = getRemainingPicks(u1, initialPicks, userLosses);
            int remainingPicks2 = getRemainingPicks(u2, initialPicks, userLosses);
            
            if (remainingPicks1 > 0 && remainingPicks2 > 0) {
                return u1.toLowerCase().compareTo(u2.toLowerCase());
            } else if (remainingPicks1 > 0) {
                return -1;
            } else if (remainingPicks2 > 0) {
                return 1;
            } else {
                return u1.toLowerCase().compareTo(u2.toLowerCase());
            }
        })
        .collect(java.util.stream.Collectors.toList());
}

private int getRemainingPicks(String user, Map<String, Integer> initialPicks, Map<String, Integer> userLosses) {
    int initial = initialPicks.getOrDefault(user, 0);
    int losses = userLosses.getOrDefault(user, 0);
    return Math.max(0, initial - losses);
}
%>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <title>Home</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
	 /* Base table styles */
	.table-custom {
	    table-layout: fixed;
	    width: auto;
	    margin-bottom: 0;
	}
	
	.table-custom thead th,
	.table-custom th.player-column {
	    background-color: #1A43BF;
	    color: white;
	    font-weight: bold;
	}
	
	.table-container {
	    max-height: none;
	    overflow-y: visible;
	    position: relative;
	}
	
	.table-responsive {
	    overflow-x: auto;
	    -webkit-overflow-scrolling: touch;
	}
	
	/* Player column styles */
	.player-column {
	    width: 80px;
	    min-width: 120px;
	    max-width: 30px;
	    padding: 5px;
	    text-align: left;
	    cursor: pointer;
	    overflow: hidden;
	    white-space: nowrap;
	    position: sticky;
	    left: 0;
	    z-index: 2;
	    background-color: #1A43BF;
	}
	
	tbody .player-column {
	    background-color: black;
	}
	
	.player-column::after {
	    content: '';
	    position: absolute;
	    top: 0;
	    right: 0;
	    bottom: 0;
	    width: 4px;
	    background: linear-gradient(to right, rgba(0,0,0,0.1), rgba(0,0,0,0));
	    pointer-events: none;
	}
	
	.player-column span {
	    font-size: 0.9em;
	    white-space: nowrap;
	    overflow: hidden;
	    text-overflow: ellipsis;
	    display: block;
	}
	
	/* Week column styles */
	.week-column {
	    width: 35px;
	    min-width: 35px;
	    max-width: 35px;
	    padding: 5px;
	    text-align: center;
	    white-space: nowrap;
	    overflow: hidden;
	    text-overflow: ellipsis;
	}
	
	.week-column.playoff {
        background-color: #2A1B3D;
        color: gold;
        font-weight: bold;
    }

    .week-column.playoff .team-logo {
        border-width: 2px !important;
    }
	
	td.week-column .team-logo {
	    width: 30px;
	    height: 30px;
		margin: 1px auto; 
    	display: block; 
	}
	
	td.week-column .logo-container {
		display: flex;
	    flex-direction: column;
	    align-items: center;
	    justify-content: center;
	    transform: none; 
	}
	
	/* Pick circles styles */
	.pick-circles {
	    display: flex;
	    justify-content: flex-start;
	    margin-bottom: 5px;
	}
	
	.pick-circle {
	    width: 20px;
	    height: 20px;
	    border-radius: 50%;
	    margin: 0 2px;
	    border: 1px solid black;
	}
	
	.pick-circle.active {
	    background-color: green;
	}
	
	.pick-circle.inactive {
	    background-color: red;
	}
	
	/* Info box styles */
	.info-box-wrapper {
	    display: flex;
	    justify-content: flex-start;
	    width: 100%;
	    margin-bottom: 20px;
	    padding-left: 0;
	    padding-right: 10px;
	}
	
	.info-box {
	    background-color: black;
	    border-radius: 5px;
	    padding: 10px;
	    box-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
	    display: inline-block;
	    min-width: 100px;
	    margin-left: 15px;
	    margin-top: 10px;
	}
	
	.info-box p {
	    color: white;
	    font-weight: bold;
	    margin-bottom: 10px;
	    white-space: nowrap;
	}
	
	.info-box strong {
	    color: light gray;
	    margin-right: 5px;
	}
	
	/* Teams card styles */
	.teams-card {
	    background-color: black;
	    border: 1px solid white;
	    box-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
	    margin: 0 0 0 20px;
	    min-width: 200px;
	    position: relative;
	    overflow: hidden;
	}
	
	.teams-card .card-header {
	    background-color: #1A43BF;
	    color: white;
	    text-align: center;
	    padding: 10px;
	    font-weight: bold;
	    width: 100%;
	}
	
	.teams-card-body {
	    padding: 8px;
	    background-color: black;
	}
	
	/* Teams container and items */
	.teams-container {
	    display: grid;
	    grid-template-columns: repeat(3, minmax(0, 1fr));
	    gap: 6px;
	    width: 100%;
	    padding: 0;
	    justify-content: center;
	    background-color: black;
	}
	
	.team-item {
	    display: flex;
	    flex-direction: column;
	    align-items: center;
	    justify-content: center;
	    width: 100%;
	}
	
	/* Logo styles */
	.team-logo {
	    background-size: contain;
	    background-position: center;
	    background-repeat: no-repeat;
	    border-style: solid;
	    border-width: 1px !important;
	    border-radius: 0 !important;
	}
	
	.team-item .logo-container {
	    width: 55px;
	    height: 55px;
	    display: flex;
	    align-items: center;
	    justify-content: center;
	    margin: 0 auto;
	}
	
	.team-item .team-logo {
	    width: 50px;
	    height: 50px;
	    margin: 0 auto;
	}
	
	.team-item span {
	    font-size: 0.8em;
	    margin-top: 4px;
	    display: block;
	    width: 100%;
	    text-align: center;
	}
	
	/* Status styles */
	.pick-win,
	.winner .team-logo {
	    border-color: green !important;
	}
	
	.pick-loss,
	.loser .team-logo {
	    border-color: red !important;
	}
	
	.pick-pending {
	    border-color: transparent !important;
	}
	
	/* Message styles */
	.deadbeat-message,
	.unpaid-message {
	    color: red;
	    font-weight: bold;
	    text-align: center;
	    margin-top: 20px;
	    font-size: 24px;
	}
	
	.deadbeat-message {
	    animation: blink 1s linear infinite;
	}
	
	@keyframes blink {
	    0% { opacity: 1; }
	    50% { opacity: 0; }
	    100% { opacity: 1; }
	}
	
	/* Responsive breakpoints */
	@media (min-width: 1200px) {
	    .teams-card {
	        width: 280px;
	    }
	}
	
	@media (min-width: 992px) and (max-width: 1199px) {
	    .teams-card {
	        width: 260px;
	        margin-left: 15px;
	    }
	}
	
	@media (min-width: 768px) and (max-width: 991px) {
	    .teams-card {
	        width: 240px;
	        margin-left: 15px;
	    }
	    
	    .teams-card-body {
	        padding: 6px;
	    }
	    
	    .teams-container {
	        gap: 4px;
	    }
	    
	    .team-item .logo-container {
	        width: 52px;
	        height: 52px;
	    }
	    
	    .team-item .team-logo {
	        width: 48px;
	        height: 48px;
	    }
	    
	    .team-item span {
	        font-size: 0.75em;
	        margin-top: 2px;
	    }
	}
	
	@media (min-width: 576px) and (max-width: 767px) {
	    .teams-card {
	        width: 220px;
	        margin-left: 10px;
	    }
	    
	    .team-item .logo-container {
	        width: 50px;
	        height: 50px;
	    }
	    
	    .team-item .team-logo {
	        width: 45px;
	        height: 45px;
	    }
	}
	
	@media (max-width: 575px) {
	    .teams-card {
	        width: 200px;
	        margin-left: 5px;
	    }
	    
	    .teams-container {
	        gap: 4px;
	        padding: 6px;
	    }
	    
	    .team-item .logo-container {
	        width: 45px;
	        height: 45px;
	    }
	    
	    .team-item .team-logo {
	        width: 40px;
	        height: 40px;
	    }
	}
	@media (max-width: 768px) {
    /* Container padding adjustment */
    .container {
        padding-left: 0px;  /* Reduced from 20px */
        padding-right: 0px; /* Reduced from 20px */
        max-width: 100%;
    }

    /* Info box wrapper adjustment */
    .info-box-wrapper {
        padding-left: 0px;
        padding-right: 0px;
    }

    /* Teams card adjustment */
    .teams-card {
        margin-left: 0px;
    }

    /* Table responsiveness adjustment */
    .table-responsive {
        padding-left: 0;
        padding-right: 0;
    }

    .table-container {
        margin-left: -50px;
        margin-right:-25px;
    }

    .info-box-wrapper {
        padding-left: 5px;
        padding-right: 5px;
        margin-bottom: 10px;  /* Reduced from 20px */
    }

    /* Info box size and spacing adjustments */
    .info-box {
        margin-left: 5px;
        padding: 6px 8px;     /* Reduced padding */
        min-width: 80px;      /* Reduced from 100px */
        margin-top: 5px;      /* Reduced from 10px */
    }

    /* Info box text adjustments */
    .info-box p {
        font-size: 0.85rem;   /* Reduced from default */
        margin-bottom: 5px;   /* Reduced from 10px */
    }

    .info-box strong {
        font-size: 0.85rem;   /* Reduced from default */
        margin-right: 3px;    /* Reduced from 5px */
    }

    /* Container padding adjustment (keeping from previous update) */
/* Adjust the column containing the teams card */
    .col-md-4 {
        margin-left: -5px;  /* Move the entire column left */
    }
    
	}
}
	</style>
    <script>
        function goToMakePicks(username) {
            window.location.href = 'MakePicksServlet?username=' + encodeURIComponent(username);
        }
    </script>
</head>

<body>
  <div class="container">
       <jsp:include page="header.jsp">
           <jsp:param name="pageTitle" value="Home" />
       </jsp:include>
       
       <div class="info-box-wrapper">
           <div class="info-box">
               <p><strong>Total Pot:</strong> $${sessionScope.totalPot}</p>
               <p><strong>Players Left:</strong> ${sessionScope.usersWithRemainingPicks}</p>
               <p><strong>Total Picks Left:</strong> ${sessionScope.totalRemainingPicks}</p>
               <p><strong>Your Picks Left:</strong> ${sessionScope.userRemainingPicks[sessionScope.userName]}</p>
           </div>
       </div>
                 
       <!-- This Week's Picks Section -->
       <div class="col-md-4">
           <div class="teams-card card">
               <div class="card-header">
                   <h5 class="mb-0">This Week's Picks</h5>
               </div>
               <div class="card-body teams-card-body">
                   <div class="teams-container">
                       <%
                       Map<String, Integer> teamPickCounts = (Map<String, Integer>) request.getAttribute("teamPickCounts");

                       if (teamPickCounts != null && !teamPickCounts.isEmpty()) {
                           List<Map.Entry<String, Integer>> sortedTeams = new ArrayList<>(teamPickCounts.entrySet());
                           Collections.sort(sortedTeams, (a, b) -> b.getValue().compareTo(a.getValue()));

                           for (Map.Entry<String, Integer> entry : sortedTeams) {
                               String teamName = entry.getKey();
                               Integer count = entry.getValue();
                               String teamAbbr = teamNameToAbbrev.get(teamName);
                               if (teamAbbr == null) {
                                   teamAbbr = teamName;
                               }
                               
                               String resultClass = "";
                               if (teamResults != null && teamResults.containsKey(teamName)) {
                                   resultClass = teamResults.get(teamName) ? "winner" : "loser";
                               }
                       %>
                               <div class="team-item <%= resultClass %>">
                                   <div class="logo-container">
                                       <div class="team-logo" 
                                            style="background-image: url('images/team-Logos/<%= teamAbbr.toLowerCase() %>-logo.svg');"
                                            title="<%= teamName %>">
                                       </div>
                                   </div>
                                   <span><%= count %></span>
                               </div>
                       <%
                           }
                       } else {
                           out.println("<div></div>");
                       }
                       %>
                   </div>
               </div>
           </div>
       </div>
       
       <% if (userHasPaid != null && !userHasPaid) { %>
           <h1 class="deadbeat-message">Player has not paid</h1>
       <% } %>
       <% if (userHasPaid != null && !userHasPaid && "Dawg Days".equals(session.getAttribute("userName"))) { %>
           <h1 class="deadbeat-message">Pay your KOTH fee, you deadbeat!</h1>
       <% } %>
       
       <div class="table-container">
           <div class="table-responsive">
               <table class="table table-bordered table-custom">
                   <thead>
                       <tr>
                           <th class="player-column">Player</th>
                           <% for (String weekLabel : weekLabels) { %>
                               <th class="week-column"><%= weekLabel %></th>
                           <% } %>
                       </tr>
                   </thead>
                   <tbody>
						<% 
						if (allUsers != null && !allUsers.isEmpty()) {
						   allUsers = sortUsersByRemainingPicks(allUsers, initialPicks, userLosses);
						   
						   for (String user : allUsers) { 
						       Integer userInitialPicksObj = initialPicks.get(user);
						       Integer userLossCountObj = userLosses.get(user);
						       
						       int userInitialPicks = (userInitialPicksObj != null) ? userInitialPicksObj : 0;
						       int userLossCount = (userLossCountObj != null) ? userLossCountObj : 0;
						       
						       int activePicks = Math.max(0, userInitialPicks - userLossCount);
						       
						       String fullName = userFullNames.get(user);
						       if (fullName == null) {
						           fullName = user;
						       }
						%>
						       <tr>
						           <th class="player-column" onclick="goToMakePicks('<%= user %>')">
						               <div class="pick-circles">
						                   <% for (int i = 1; i <= userInitialPicks; i++) { 
						                       boolean isActive = i <= activePicks;
						                   %>
						                       <div class="pick-circle <%= isActive ? "active" : "inactive" %>"></div>
						                   <% } %>
						               </div>
						               <span class="player-name" title="<%= fullName %>"><%= user %></span>
						           </th>
						           <%
						           for (int weekNumber = startWeek; weekNumber <= endWeek; weekNumber++) {
						               boolean weekDataExists = optimizedData != null && optimizedData.containsKey(weekNumber);
						           %>
						               <td class="week-column">
						                   <div class="logo-container">
						                   <% 
						                   if (weekDataExists) {
						                       List<Map<String, Object>> userPicks = new ArrayList<>();
						                       Map<String, List<Map<String, Object>>> weekData = optimizedData.get(weekNumber);
						                       
						                    // Collect all picks for this user in this week
						                       for (List<Map<String, Object>> gamePicks : weekData.values()) {
						                           for (Map<String, Object> pick : gamePicks) {
						                               if (user.equals(pick.get("username")) && pick.get("selectedTeam") != null) {
						                                   String status = (String) pick.get("status");
						                                   String pickResult = "pick-pending";
						                                   
						                                   if ("STATUS_FINAL".equals(status)) {
						                                       int homeScore = (int) pick.get("homeScore");
						                                       int awayScore = (int) pick.get("awayScore");
						                                       String selectedTeam = (String) pick.get("selectedTeam");
						                                       String homeTeamName = (String) pick.get("homeTeamName");

						                                       String selectedTeamAbbr = teamNameToAbbrev.get(selectedTeam);
						                                       String homeTeamAbbr = teamNameToAbbrev.get(homeTeamName);
						                                       boolean isHomeTeam = selectedTeamAbbr.equals(homeTeamAbbr);
						                                       boolean isWin = (homeScore > awayScore && isHomeTeam) || 
						                                                      (awayScore > homeScore && !isHomeTeam);
						                                       pickResult = isWin ? "pick-win" : "pick-loss";
						                                   }
						                                   pick.put("result", pickResult);
						                                   userPicks.add(pick);
						                               }
						                           }
						                       }

						                       // Modified sort to put losses first, then pending, then wins
						                       Collections.sort(userPicks, new Comparator<Map<String, Object>>() {
						                           @Override
						                           public int compare(Map<String, Object> p1, Map<String, Object> p2) {
						                               String result1 = (String) p1.get("result");
						                               String result2 = (String) p2.get("result");
						                               
						                               // Loss = 2, pending = 1, win = 0
						                               int sort1 = "pick-loss".equals(result1) ? 2 : ("pick-pending".equals(result1) ? 1 : 0);
						                               int sort2 = "pick-loss".equals(result2) ? 2 : ("pick-pending".equals(result2) ? 1 : 0);
						                               
						                               return sort2 - sort1;
						                           }
						                       });

						                       // Display up to 3 picks for this week
						                       for (int i = 0; i < Math.min(userPicks.size(), 3); i++) {
						                           Map<String, Object> pick = userPicks.get(i);
						                           String teamAbbr = teamNameToAbbrev.get(pick.get("selectedTeam"));
						                           String pickResult = (String) pick.get("result");
						                       %>
						                           <div class="team-logo <%= pickResult %>" 
						                                style="background-image: url('images/team-Logos/<%= teamAbbr.toLowerCase() %>-logo.svg')"
						                                title="<%= pick.get("selectedTeam") %>">
						                           </div>
						                       <%						                       
						                       }
						                   } else {
						                   %>
						                       <span>-</span>
						                   <%
						                   }
						                   %>
						                   </div>
						               </td>
						           <%
						           }
						           %>
						       </tr>
						<% 
						   }
						}
						%>
						</tbody>
               </table>
           </div>
       </div>
   </div>

   <script>
        function goToMakePicks(username) {
            window.location.href = 'MakePicksServlet?username=' + encodeURIComponent(username);
        }
        
        function truncateUsernames() {
            const playerColumns = document.querySelectorAll('.player-column');
            playerColumns.forEach(column => {
                const span = column.querySelector('.player-name');
                if (!span) return;
                
                const username = span.textContent;
                if (!username) return;
                
                span.textContent = username;
                
                requestAnimationFrame(() => {
                    if (!column.offsetWidth) return;
                    const maxWidth = column.offsetWidth - 20;
                    
                    if (span.offsetWidth > maxWidth) {
                        let text = username;
                        while (span.offsetWidth > maxWidth && text.length > 12) {
                            text = text.slice(0, -1);
                            span.textContent = text + '...';
                        }
                    }
                });
            });
        }

        let resizeTimer;
        window.addEventListener('load', () => {
            setTimeout(truncateUsernames, 100);
        });

        window.addEventListener('resize', () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(truncateUsernames, 100);
        });
    </script>
</body>
<%@ include file="footer.jsp" %>
</html>
