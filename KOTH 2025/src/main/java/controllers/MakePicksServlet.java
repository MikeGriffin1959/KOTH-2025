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
public class MakePicksServlet {

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
    public MakePicksServlet(SqlConnectorGameTable sqlConnectorGameTable,
                             SqlConnectorTeamsTable sqlConnectorTeamsTable,
                             SqlConnectorPicksTable sqlConnectorPicksTable,
                             NFLGameFetcherService nflGameFetcherService) {
        this.sqlConnectorGameTable = sqlConnectorGameTable;
        this.sqlConnectorTeamsTable = sqlConnectorTeamsTable;
        this.sqlConnectorPicksTable = sqlConnectorPicksTable;
        this.nflGameFetcherService = nflGameFetcherService;
    }
    
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

    @GetMapping("/MakePicksServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("MakePicksServlet: doGet method started");
        long startTime = System.nanoTime();

        // Treat missing/half-dead sessions as not logged in; preserve returnTo
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null || session.getAttribute("userId") == null) {
            String original = request.getRequestURI() +
                    (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
            return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
        }

        // If commissioner updated anything, force a refresh of derived session data
        ServletContext context = request.getServletContext();
        Long appVer  = (Long) context.getAttribute("derivedDataVersion");
        Long seenVer = (Long) session.getAttribute("derivedDataVersionSeen");
        if (appVer != null && (seenVer == null || !appVer.equals(seenVer))) {
            System.out.println("MakePicksServlet: cache version changed → refreshing session data");
            commonProcessingService.ensureSessionData(session, context);
            session.setAttribute("derivedDataVersionSeen", appVer);
        }

        // Populate common attributes (season/week, etc.)
        servletUtility.setCommonAttributes(request, context);

        String season = (String) request.getAttribute("season");
        String week   = (String) request.getAttribute("week");
        if (season == null) season = (String) context.getAttribute("currentSeason");
        if (week   == null) week   = (String) context.getAttribute("currentWeek");

        if (season == null || week == null) {
            model.addAttribute("errorMessage", "Season/week not resolved.");
            return "error";
        }

        final int seasonInt;
        final int weekInt;
        try {
            seasonInt = Integer.parseInt(season);
            weekInt   = Integer.parseInt(week);
        } catch (NumberFormatException nfe) {
            model.addAttribute("errorMessage", "Invalid season/week.");
            return "error";
        }

        String  userName = (String) session.getAttribute("userName");
        Integer userId   = (Integer) session.getAttribute("userId");
        if (userId == null || userName == null) {
            String original = request.getRequestURI() +
                    (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
            return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek =
                (Map<String, Integer>) session.getAttribute("userRemainingPicksPriorWeek");

        // If missing, try to refresh once from the service/context
        if (userRemainingPicksPriorWeek == null || userRemainingPicksPriorWeek.isEmpty()) {
            System.out.println("MakePicksServlet: Prior week picks missing, recalculating...");
            commonProcessingService.ensureSessionData(session, context);
            @SuppressWarnings("unchecked")
            Map<String, Integer> refreshedPriorWeek =
                    (Map<String, Integer>) context.getAttribute("userRemainingPicksPriorWeek");
            userRemainingPicksPriorWeek = (refreshedPriorWeek != null) ? refreshedPriorWeek : java.util.Collections.emptyMap();
            session.setAttribute("userRemainingPicksPriorWeek", userRemainingPicksPriorWeek);

            if (userRemainingPicksPriorWeek.isEmpty()) {
                model.addAttribute("errorMessage", "Unable to load required user data. Please refresh or go to Home first.");
                return "error";
            }
        }

        int remainingPicks = userRemainingPicksPriorWeek.getOrDefault(userName, 0);
        System.out.println("MakePicksServlet: Remaining picks for user " + userName + ": " + remainingPicks);

        try {
            // Update current week games (minimal fields) before showing
            java.util.List<Game> espnGames = nflGameFetcherService.fetchCurrentWeekGames();
            if (espnGames != null && !espnGames.isEmpty()) {
                sqlConnectorGameTable.updateGameTableMinimal(espnGames);
            }

            Map<String, java.util.List<String>> selectedPicks =
                    sqlConnectorPicksTable.getUserPicks(userId, seasonInt, weekInt);
            if (selectedPicks == null) selectedPicks = java.util.Collections.emptyMap();

            Map<String, String> teamNameToAbbrev = sqlConnectorTeamsTable.getTeamNameToAbbrev();
            if (teamNameToAbbrev == null) teamNameToAbbrev = java.util.Collections.emptyMap();

            java.util.List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            if (games == null) games = new java.util.ArrayList<>();

            // Update odds for scheduled games and normalize statuses/dates
            updateOddsForScheduledGames(games);
            games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            if (games == null) games = new java.util.ArrayList<>();
            processGameStatus(games);

            // Model for JSP
            model.addAttribute("remainingPicks", remainingPicks);
            model.addAttribute("selectedPicks", selectedPicks);
            model.addAttribute("teamNameToAbbrev", teamNameToAbbrev);
            model.addAttribute("makePicksGames", games);
            model.addAttribute("userName", userName);
            model.addAttribute("season", season);
            model.addAttribute("currentWeek", week);

            long endTime = System.nanoTime();
            System.out.printf("MakePicksServlet.doGet execution time: %.1f Seconds%n",
                    (endTime - startTime) / 1_000_000_000.0);

            return "makePicks";

        } catch (Exception e) {
            System.err.println("MakePicksServlet: Error processing request: " + e.getMessage());
            model.addAttribute("errorMessage", "Error processing request.");
            return "error";
        }
    }


    @PostMapping("/MakePicksServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("MakePicksServlet: doPost method started");

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
}
