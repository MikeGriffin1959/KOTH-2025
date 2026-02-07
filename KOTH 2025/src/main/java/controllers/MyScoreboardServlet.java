package controllers;

import helpers.*;
import model.Game;
import services.NFLGameFetcherService;
import services.ServletUtility;
import services.CommonProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class MyScoreboardServlet {

    private final SqlConnectorGameTable sqlConnectorGameTable;
    private final SqlConnectorTeamsTable sqlConnectorTeamsTable;
    private final SqlConnectorPicksTable sqlConnectorPicksTable;
    private final NFLGameFetcherService nflGameFetcherService;

    @Autowired
    private ServletUtility servletUtility;

    @Autowired
    private CommonProcessingService commonProcessingService; // ✅ Added

    private static final String STATUS_FINAL = "Final";
    private static final String STATUS_SCHEDULED = "Scheduled";
    private static final String STATUS_IN_PROGRESS = "In Progress";

    @Autowired
    public MyScoreboardServlet(SqlConnectorGameTable sqlConnectorGameTable,
                             SqlConnectorTeamsTable sqlConnectorTeamsTable,
                             SqlConnectorPicksTable sqlConnectorPicksTable,
                             NFLGameFetcherService nflGameFetcherService) {
        this.sqlConnectorGameTable = sqlConnectorGameTable;
        this.sqlConnectorTeamsTable = sqlConnectorTeamsTable;
        this.sqlConnectorPicksTable = sqlConnectorPicksTable;
        this.nflGameFetcherService = nflGameFetcherService;
    }
    
    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;
    
    private boolean isLoggedIn(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("userName") != null && s.getAttribute("userId") != null;
    }

    /** Return null if OK; otherwise a redirect string to LoginServlet with returnTo. */
    private String requireLoginOrRedirect(HttpServletRequest request) {
        if (isLoggedIn(request)) return null;
        String original = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
    }


    private static String convertStatus(String dbStatus) {
        if (dbStatus == null) return "Unknown";
        switch (dbStatus) {
            case "STATUS_FINAL":
                return STATUS_FINAL;
            case "STATUS_SCHEDULED":
                return STATUS_SCHEDULED;
            case "STATUS_IN_PROGRESS":
            case "STATUS_HALFTIME":
            case "STATUS_END_PERIOD":
                return STATUS_IN_PROGRESS;
            default:
                return dbStatus;
        }
    }

    @GetMapping("/MyScoreboardServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("MyScoreboardServlet: doGet method started");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("MyScoreboardServlet: No valid session found, redirecting to login");
            return "redirect:/LoginServlet";
        }

        // Always set season/week consistently
        servletUtility.setCommonAttributes(request, request.getServletContext());

        // 🔁 Always refresh user + picks data so Initial Picks changes are visible immediately
        ServletContext ctx = request.getServletContext();
        commonProcessingService.updateUserData(ctx);   // refresh initialPicks + allSeasonUsers
        commonProcessingService.updatePicksData(ctx);  // recompute userLosses + userRemainingPicks(+PriorWeek)

        String season = (String) request.getAttribute("season");
        String week   = (String) request.getAttribute("week");

        if (season == null) season = (String) ctx.getAttribute("currentSeason");
        if (week   == null) week   = (String) ctx.getAttribute("currentWeek");

        System.out.println("MyScoreboardServlet: Retrieved attributes - Season: " + season + ", Week: " + week);

        int seasonInt = Integer.parseInt(season);
        int weekInt   = Integer.parseInt(week);

        String userName = (String) session.getAttribute("userName");
        Integer userId  = (Integer) session.getAttribute("userId");
        if (userId == null) {
            model.addAttribute("errorMessage", "User ID not found.");
            return "error";
        }

        // ⬅️ Pull the fresh, app-scope map (not the session-cached one)
        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek =
            (Map<String, Integer>) ctx.getAttribute("userRemainingPicksPriorWeek");

        if (userRemainingPicksPriorWeek == null || userRemainingPicksPriorWeek.isEmpty()) {
            model.addAttribute("errorMessage", "Unable to load required user data. Please refresh.");
            return "error";
        }

        int remainingPicks = userRemainingPicksPriorWeek.getOrDefault(userName, 0);
        System.out.println("MyScoreboardServlet: Remaining picks for user " + userName + ": " + remainingPicks);

        try {
            // ✅ Fetch ESPN games for current week and update DB
            List<Game> espnGames = nflGameFetcherService.fetchCurrentWeekGames();
            sqlConnectorGameTable.updateGameTableMinimal(espnGames);

            // ✅ Retrieve data for display
            Map<String, List<String>> selectedPicks = sqlConnectorPicksTable.getUserPicks(userId, seasonInt, weekInt);
            Map<String, String> teamNameToAbbrev   = sqlConnectorTeamsTable.getTeamNameToAbbrev();

            List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            updateOddsForScheduledGames(games);
            games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            processGameStatus(games);

            // ✅ Calculate team pick counts for the current week
            Map<String, Integer> teamPickCounts = calculateTeamPickCounts(seasonInt, weekInt);

            // ✅ Add attributes for JSP
            model.addAttribute("remainingPicks", remainingPicks);
            model.addAttribute("selectedPicks", selectedPicks);
            model.addAttribute("teamNameToAbbrev", teamNameToAbbrev);
            model.addAttribute("myScorboardGames", games);
            model.addAttribute("userName", userName);
            model.addAttribute("season", season);
            model.addAttribute("currentWeek", week);
            model.addAttribute("teamPickCounts", teamPickCounts);
            Boolean maskPicks = (Boolean) ctx.getAttribute("maskPicks");
            if (maskPicks == null) {
                List<model.PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(seasonInt);
                maskPicks = !pricesList.isEmpty() && pricesList.get(0).isMaskPicks();
                ctx.setAttribute("maskPicks", maskPicks);
            }
            model.addAttribute("maskPicks", maskPicks);

            long endTime = System.nanoTime();
            System.out.printf("MyScoreboardServlet.doGet execution time: %.1f Seconds%n",
                    (endTime - startTime) / 1_000_000_000.0);

            return "myScoreboard";

        } catch (Exception e) {
            System.err.println("MyScoreboardServlet: Error processing request: " + e.getMessage());
            model.addAttribute("errorMessage", "Error processing request.");
            return "error";
        }
    }

    

    @PostMapping("/MyScoreboardServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("MyScoreboardServlet: doPost method started");

        // Require login first (covers expired/half-dead sessions)
        String maybeRedirect = requireLoginOrRedirect(request);
        if (maybeRedirect != null) return maybeRedirect;

        HttpSession session = request.getSession(false);
        servletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week   = (String) request.getAttribute("week");
        if (season == null || week == null) {
            model.addAttribute("errorMessage", "Missing required parameters.");
            return "error";
        }

        int seasonInt, weekInt;
        try {
            seasonInt = Integer.parseInt(season);
            weekInt   = Integer.parseInt(week);
        } catch (NumberFormatException nfe) {
            model.addAttribute("errorMessage", "Invalid season/week.");
            return "error";
        }

        String userName = (String) session.getAttribute("userName");
        Integer userId  = (Integer) session.getAttribute("userId");
        if (userId == null || userName == null) {
            return requireLoginOrRedirect(request);
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek =
            (Map<String, Integer>) session.getAttribute("userRemainingPicksPriorWeek");
        if (userRemainingPicksPriorWeek == null) userRemainingPicksPriorWeek = Collections.emptyMap();

        int remainingPicks = userRemainingPicksPriorWeek.getOrDefault(userName, 0);

        Map<String, List<String>> newPicks = parsePicksFromRequest(request, remainingPicks);
        if (newPicks == null) {
            request.setAttribute("errorMessage", "Cannot submit more picks than remaining.");
            return doGet(request, response, model);
        }

        try {
            sqlConnectorPicksTable.updateUserPicks(userId, seasonInt, weekInt, newPicks);
            request.setAttribute("message", "Picks successfully updated!");
        } catch (Exception e) {
            request.setAttribute("errorMessage", "Error updating picks: " + e.getMessage());
        }

        return doGet(request, response, model);
    }


    private Map<String, List<String>> parsePicksFromRequest(HttpServletRequest request, int remainingPicks) {
        Map<String, List<String>> newPicks = new HashMap<>();
        int totalPicksSubmitted = 0;

        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            if (paramName.startsWith("pick_")) {
                String gameId = paramName.substring(5);
                String[] pickValues = request.getParameterValues(paramName);

                if (pickValues != null) {
                    List<String> gamePicks = new ArrayList<>();
                    for (String pickValue : pickValues) {
                        String[] parts = pickValue.split("_");
                        if (parts.length == 2) {
                            String teamName = parts[0];
                            int count = Integer.parseInt(parts[1]);
                            totalPicksSubmitted += count;
                            for (int i = 0; i < count; i++) {
                                gamePicks.add(teamName);
                            }
                        }
                    }
                    if (!gamePicks.isEmpty()) {
                        newPicks.put(gameId, gamePicks);
                    }
                }
            }
        }

        return totalPicksSubmitted > remainingPicks ? null : newPicks;
    }

    private void updateOddsForScheduledGames(List<Game> games) {
        for (Game game : games) {
            if ("STATUS_SCHEDULED".equals(game.getStatus()) || "Scheduled".equals(game.getStatus())) {
                try {
                    String gameId = String.valueOf(game.getGameID());
                    String oddsResponse = ApiFetchers.FetchESPNGameOdds(gameId);
                    if (oddsResponse != null && !oddsResponse.isEmpty()) {
                        Game updatedGame = ApiParsers.ParseESPNOdds(oddsResponse, game);
                        sqlConnectorGameTable.updateGameOdds(updatedGame);
                    }
                } catch (Exception e) {
                    System.err.println("Error updating odds for GameID " + game.getGameID() + ": " + e.getMessage());
                }
            }
        }
    }

    private void processGameStatus(List<Game> games) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        for (Game game : games) {
            try {
                String utcDate = game.getDate();
                if (utcDate != null && !utcDate.isEmpty()) {
                    LocalDateTime utcDateTime = LocalDateTime.parse(utcDate.replace("Z", ""), formatter);
                    ZonedDateTime easternZoned = utcDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/New_York"));
                    game.setDate(easternZoned.toString());
                }

                String dbStatus = game.getStatus();
                String convertedStatus = convertStatus(dbStatus);
                game.setStatus(convertedStatus);
                updateGameDisplayFlags(game);

            } catch (Exception e) {
                System.err.println("Error processing game " + game.getGameID() + ": " + e.getMessage());
            }
        }
    }

    private void updateGameDisplayFlags(Game game) {
        String status = game.getStatus();
        game.setShowOdds(false);
        game.setShowScore(false);

        switch (status) {
            case STATUS_SCHEDULED:
                game.setShowOdds(true);
                break;
            case STATUS_IN_PROGRESS:
            case STATUS_FINAL:
                game.setShowScore(true);
                break;
        }
    }
    private Map<String, Integer> calculateTeamPickCounts(int seasonInt, int weekInt) {
        Map<String, Integer> teamPickCounts = new HashMap<>();
        
        try {
            System.out.println("DEBUG: My Scoreboard Servlet - Calculating team pick counts for season " + seasonInt + ", week " + weekInt);
            
            // Get all picks for the current week across all users
            Map<Integer, Map<String, List<Map<String, Object>>>> allWeeksData =
                    sqlConnectorPicksTable.getPicksForAllWeeks(seasonInt, weekInt);
            
            System.out.println("DEBUG: My Scoreboard Servlet - allWeeksData = " + allWeeksData);
            
            Map<String, List<Map<String, Object>>> weekData = allWeeksData.get(weekInt);
            System.out.println("DEBUG: My Scoreboard Servlet - weekData for week " + weekInt + " = " + weekData);
            
            if (weekData != null) {
                for (List<Map<String, Object>> gamePicks : weekData.values()) {
                    System.out.println("DEBUG: My Scoreboard Servlet - Processing game with " + gamePicks.size() + " picks");
                    for (Map<String, Object> pick : gamePicks) {
                        String selectedTeam = (String) pick.get("selectedTeam");
                        System.out.println("DEBUG: My Scoreboard Servlet - Found pick for team: " + selectedTeam);
                        if (selectedTeam != null) {
                            teamPickCounts.merge(selectedTeam, 1, Integer::sum);
                        }
                    }
                }
            }
            
            System.out.println("DEBUG: My Scoreboard Servlet - Final teamPickCounts = " + teamPickCounts);
        } catch (Exception e) {
            System.err.println("Error calculating team pick counts: " + e.getMessage());
            e.printStackTrace();
        }
        
        return teamPickCounts;
    }
}

