package controllers;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import model.Game;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import helpers.SqlConnectorCommentaryTable;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksPriceTable;
import helpers.SqlConnectorPicksTable;
import helpers.SqlConnectorUserTable;
import services.CommonProcessingService;
import services.NFLGameFetcherService;
import services.ServletUtility;

@Controller
public class HomeServlet {

    @Autowired
    private CommonProcessingService commonProcessingService;

    @Autowired
    private SqlConnectorGameTable sqlConnectorGameTable;

    @Autowired
    private SqlConnectorPicksTable sqlConnectorPicksTable;

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private NFLGameFetcherService nflGameFetcherService;

    @Autowired
    private ServletUtility servletUtility; // ✅ Injected instead of static
    
    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private SqlConnectorCommentaryTable sqlConnectorCommentaryTable;

    //Testing Only//   

    @Autowired private services.EdgeOrchestrator edgeOrchestrator;
    @Autowired private services.EdgeCandidateService edgeCandidateService;
    


    @org.springframework.web.bind.annotation.GetMapping("/admin/edge/run")
    @org.springframework.web.bind.annotation.ResponseBody
    public String runEdge(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer season,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer week) {

        int s = (season != null) ? season : 2025;
        int w = (week != null) ? week : 1;

        edgeOrchestrator.runWeeklyEdge(s, w);

        java.util.List<model.EdgeCandidate> ranked =
            edgeCandidateService.buildRankedCandidates(s, w, 5,
                services.EdgeCandidateService.Allocation.SPREAD);
        ranked.forEach(c -> System.out.println("DEBUG[edge-rank]: " + c));

        return "Edge run complete for " + s + " wk" + w +
               " — " + ranked.size() + " candidates ranked. Check logs for DEBUG[edge-rank].";
    }
    
//End Testing code //

    @SuppressWarnings("unchecked")
	@GetMapping({"/", "/home", "/index", "/HomeServlet"})
    public String doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("HomeServlet: doGet() started");
        long startTime = System.nanoTime();

        try {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("userName") == null) {
                System.out.println("HomeServlet: Not logged in, redirecting to LoginServlet");
                String original = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
                String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
                return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
            }

            ServletContext context = request.getServletContext();
            Long appVer  = (Long) context.getAttribute("derivedDataVersion");
            Long seenVer = (Long) session.getAttribute("derivedDataVersionSeen");
            if (appVer != null && (seenVer == null || !appVer.equals(seenVer))) {
                commonProcessingService.ensureSessionData(session, context);
                session.setAttribute("derivedDataVersionSeen", appVer);
            }

            // ✅ Fetch current week games & update DB FIRST
            List<Game> parsedGames = nflGameFetcherService.fetchCurrentWeekGames();
            sqlConnectorGameTable.updateGameTableMinimal(parsedGames);

            // ✅ NEW: Ensure required context data is initialized before refreshing picks
            // On first load after server start, initialPicks won't exist yet
            if (context.getAttribute("initialPicks") == null) {
                // Initialize prerequisites that updatePicksData depends on
                commonProcessingService.updateSeasonAndWeek(context);
                commonProcessingService.updateTeamData(context);
                commonProcessingService.updateUserData(context);
            }
            
            // ✅ Now refresh picks data - game statuses will be current from DB update above
            commonProcessingService.updatePicksData(context);

            // ✅ Process all common data, including total pot, players left, picks left
            commonProcessingService.processCommonData(request, response, context);

            // ✅ Retrieve season and week from request attributes (set in processCommonData)
            String seasonStr = (String) request.getAttribute("season");
            String weekStr = (String) request.getAttribute("currentWeek");
            int seasonInt = Integer.parseInt(seasonStr);
            int weekInt = Integer.parseInt(weekStr);

            // Commentary blurb for the Home teaser (never let it break the page).
            try {
                List<model.PicksPrice> commentaryPrices = sqlConnectorPicksPriceTable.getPickPrices(seasonInt);
                boolean commentaryEnabled = !commentaryPrices.isEmpty() && commentaryPrices.get(0).isCommentaryEnabled();
                request.setAttribute("commentaryEnabled", commentaryEnabled);
                request.setAttribute("latestCommentary",
                        commentaryEnabled ? sqlConnectorCommentaryTable.getLatestForSeason(seasonInt) : null);
            } catch (Exception ce) {
                System.err.println("HomeServlet: commentary blurb load failed (non-fatal): " + ce.getMessage());
                request.setAttribute("commentaryEnabled", false);
                request.setAttribute("latestCommentary", null);
            }

            // ✅ Get all weeks picks data (now has fresh game statuses)
            Map<Integer, Map<String, List<Map<String, Object>>>> allWeeksData =
                    sqlConnectorPicksTable.getPicksForAllWeeks(seasonInt, weekInt);

            // ✅ Calculate team pick counts and results
            Map<String, Integer> teamPickCounts = new HashMap<>();
            Map<String, Boolean> teamResults = new HashMap<>();
            calculateTeamPickCountsAndResults(allWeeksData, weekInt, teamPickCounts, teamResults,
                    (Map<String, String>) context.getAttribute("teamNameToAbbrev"));

            // ✅ Prepare user full names
            List<String> allUsers = (List<String>) session.getAttribute("allUsers");
            if (allUsers == null) allUsers = java.util.Collections.emptyList();
            Map<String, String> userFullNames = getUserFullNames(allUsers, context);

            boolean userHasPaid = sqlConnectorUserTable.hasUserPaidForSeason(
                    (Integer) session.getAttribute("userId"), seasonInt);

         // Mask Picks: load from context, or from DB on first access.
            Boolean maskPicks = (Boolean) context.getAttribute("maskPicks");
            if (maskPicks == null) {
                // Resolve from THIS request's season (set by processCommonData above),
                // not the app-scoped "currentSeason" — on a fresh server start that
                // app attribute isn't populated on the first Home load, which left
                // Week-1 picks unmasked until a second visit cached the value.
                List<model.PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(seasonInt);
                maskPicks = !pricesList.isEmpty() && pricesList.get(0).isMaskPicks();
                context.setAttribute("maskPicks", maskPicks);
            }
            request.setAttribute("maskPicks", maskPicks);
            request.setAttribute("currentUserName", (String) session.getAttribute("userName"));

            // ✅ Set attributes for JSP (relying on session + calculated maps)
            setRequestAttributes(request, seasonStr, weekStr, allWeeksData,
                    (Map<String, Integer>) session.getAttribute("initialPicks"),
                    (Map<String, Integer>) session.getAttribute("userLosses"),
                    (Map<String, Integer>) session.getAttribute("userRemainingPicks"),
                    (Map<String, String>) session.getAttribute("teamNameToAbbrev"),
                    allUsers,
                    (Integer) session.getAttribute("totalPot"),
                    (Integer) session.getAttribute("usersWithRemainingPicks"),
                    (Integer) session.getAttribute("totalRemainingPicks"),
                    ((Map<String, Integer>) session.getAttribute("userRemainingPicks")) != null
                        ? ((Map<String, Integer>) session.getAttribute("userRemainingPicks"))
                            .getOrDefault((String) session.getAttribute("userName"), 0)
                        : 0,

                    userFullNames, userHasPaid,
                    (Map<Integer, Map<String, String>>) context.getAttribute("gameWinners"),
                    teamPickCounts, teamResults);

            long endTime = System.nanoTime();
            System.out.printf("HomeServlet.doGet execution time: %.1f Seconds%n",
                    (endTime - startTime) / 1_000_000_000.0);

            return "home";

        } catch (Exception e) {
            System.out.println("HomeServlet: Unhandled exception occurred");
            e.printStackTrace();
            return "error";
        }
    }

    @PostMapping("/HomeServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        return doGet(request, response);
    }

    @SuppressWarnings("static-access")
	private Map<String, String> getUserFullNames(List<String> usernames, ServletContext context) {
        Map<String, String> fullNames = new HashMap<>();
        
        // Add this debug logging
        @SuppressWarnings("unchecked")
        Map<String, User> userMap = (Map<String, User>) context.getAttribute("userMap");
        System.out.println("=== DEBUG: Home Servlet - userMap status ===");
        if (userMap != null) {
            System.out.println("userMap found with " + userMap.size() + " users");
            System.out.println("All usernames in map: " + userMap.keySet());
            for (Map.Entry<String, User> entry : userMap.entrySet()) {
                User user = entry.getValue();
                System.out.println("  " + entry.getKey() + " -> ID: " + user.getIdUser() + 
                                 ", Name: " + user.getFirstName() + " " + user.getLastName());
            }
        } else {
            System.out.println("userMap is NULL!");
        }
        System.out.println("=== END DEBUG ===");
        
        for (String username : usernames) {
            User user = servletUtility.getUserFromContext(context, username);
            if (user != null) {
                fullNames.put(username, user.getFirstName() + " " + user.getLastName());
                System.out.println("SUCCESS: Found user data for: " + username + " (ID: " + user.getIdUser() + ")");
            } else {
                fullNames.put(username, username);
                System.out.println("FAILED: No user data found for: " + username);
            }
        }
        return fullNames;
    }

    private void setRequestAttributes(HttpServletRequest request, String currentSeason, String currentWeek,
                                      Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData,
                                      Map<String, Integer> initialPicks, Map<String, Integer> userLosses,
                                      Map<String, Integer> userRemainingPicks, Map<String, String> teamNameToAbbrev,
                                      List<String> allUsers, Integer totalPot, Integer usersWithRemainingPicks,
                                      Integer totalRemainingPicks, Integer currentUserRemainingPicks,
                                      Map<String, String> userFullNames, boolean userHasPaid,
                                      Map<Integer, Map<String, String>> gameWinners,
                                      Map<String, Integer> teamPickCounts, Map<String, Boolean> teamResults) {

        request.setAttribute("currentSeason", currentSeason);
        request.setAttribute("currentWeek", currentWeek);
        request.setAttribute("optimizedData", optimizedData);
        request.setAttribute("initialPicks", initialPicks);
        request.setAttribute("userLosses", userLosses);
        request.setAttribute("userRemainingPicks", userRemainingPicks);
        request.setAttribute("teamNameToAbbrev", teamNameToAbbrev);
        request.setAttribute("allUsers", allUsers);
        request.setAttribute("totalPot", totalPot);
        request.setAttribute("usersWithRemainingPicks", usersWithRemainingPicks);
        request.setAttribute("totalRemainingPicks", totalRemainingPicks);
        request.setAttribute("currentUserRemainingPicks", currentUserRemainingPicks);
        request.setAttribute("userFullNames", userFullNames);
        request.setAttribute("userHasPaid", userHasPaid);
        request.setAttribute("gameWinners", gameWinners);
        request.setAttribute("teamPickCounts", teamPickCounts);
        request.setAttribute("teamResults", teamResults);
    }

	private void calculateTeamPickCountsAndResults(Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData,
	                                               int currentWeekInt, Map<String, Integer> teamPickCounts,
	                                               Map<String, Boolean> teamResults,
	                                               Map<String, String> teamNameToAbbrev) {
	
	    if (teamNameToAbbrev == null) {
	        System.err.println("ERROR: teamNameToAbbrev is NULL. Check ServletContext initialization.");
	        return;
	    }
	
	    Map<String, List<Map<String, Object>>> weekData = optimizedData.get(currentWeekInt);
	    if (weekData == null) return;
	
	    for (List<Map<String, Object>> gamePicks : weekData.values()) {
	        if (!gamePicks.isEmpty()) {
	            Map<String, Object> game = gamePicks.get(0);
	
	            // Count picks. Normalize to the abbreviation before merging — legacy rows
	            // stored mascot names ("Bills") alongside abbreviations ("BUF"), which
	            // rendered the same team as two tiles with split counts.
	            for (Map<String, Object> pick : gamePicks) {
	                String selectedTeam = (String) pick.get("selectedTeam");
	                if (selectedTeam != null) {
	                    String key = teamNameToAbbrev.getOrDefault(selectedTeam, selectedTeam);
	                    teamPickCounts.merge(key, 1, Integer::sum);
	                }
	            }
	
	            // Store results
	            if (game.get("status") != null && game.get("status").toString().contains("FINAL")) {
	                String homeTeam = (String) game.get("homeTeamName");
	                String awayTeam = (String) game.get("awayTeamName");
	                int homeScore = (int) game.get("homeScore");
	                int awayScore = (int) game.get("awayScore");
	
	                if (homeScore == awayScore) {
	                    // Tie - both teams lose
	                    teamResults.put(teamNameToAbbrev.get(homeTeam), false);
	                    teamResults.put(teamNameToAbbrev.get(awayTeam), false);
	                } else {
	                    boolean homeTeamWon = homeScore > awayScore;
	                    teamResults.put(teamNameToAbbrev.get(homeTeam), homeTeamWon);
	                    teamResults.put(teamNameToAbbrev.get(awayTeam), !homeTeamWon);
	                }
	            }
	        }
	    }
	}
}