package controllers;

import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksTable;
import helpers.SqlConnectorTeamsTable;
import helpers.SqlConnectorUserTable;
import model.Game;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import services.ServletUtility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

@Controller
public class CommissionerOverrideServlet {

    private final SqlConnectorGameTable sqlConnectorGameTable;
    private final SqlConnectorUserTable sqlConnectorUserTable;
    private final SqlConnectorTeamsTable sqlConnectorTeamsTable;
    private final SqlConnectorPicksTable sqlConnectorPicksTable;

    @Autowired
    private ServletUtility servletUtility; // ✅ Injected instead of static calls

    private static final String STATUS_FINAL = "Final";
    private static final String STATUS_SCHEDULED = "Scheduled";
    private static final String STATUS_IN_PROGRESS = "In Progress";

    @Autowired
    public CommissionerOverrideServlet(SqlConnectorGameTable sqlConnectorGameTable,
                                       SqlConnectorUserTable sqlConnectorUserTable,
                                       SqlConnectorTeamsTable sqlConnectorTeamsTable,
                                       SqlConnectorPicksTable sqlConnectorPicksTable) {
        this.sqlConnectorGameTable = sqlConnectorGameTable;
        this.sqlConnectorUserTable = sqlConnectorUserTable;
        this.sqlConnectorTeamsTable = sqlConnectorTeamsTable;
        this.sqlConnectorPicksTable = sqlConnectorPicksTable;
    }
    
    private boolean isLoggedIn(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("userName") != null;
    }

    private boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
            || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));
    }

    /** Return null if OK; otherwise a redirect string to LoginServlet with returnTo. */
    private String requireLoginOrRedirect(HttpServletRequest request) {
        if (isLoggedIn(request)) return null;
        String original = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
    }

    /** If unauth/unauthorized XHR: write 401 JSON + X-Login-Redirect and return true. */
    private boolean failAjaxUnauthorizedIfNeeded(HttpServletRequest request, HttpServletResponse response, boolean mustBeCommish) throws IOException {
        if (!isAjax(request)) return false;
        HttpSession s = request.getSession(false);
        boolean loggedIn = s != null && s.getAttribute("userName") != null;
        boolean commish = loggedIn && Boolean.TRUE.equals(s.getAttribute("isCommish"));
        if (!loggedIn || (mustBeCommish && !commish)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Login-Redirect",
                request.getContextPath() + "/LoginServlet?expired=1&returnTo=" +
                java.net.URLEncoder.encode(request.getRequestURI() +
                    (request.getQueryString() != null ? "?" + request.getQueryString() : ""),
                    java.nio.charset.StandardCharsets.UTF_8));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized or session expired\"}");
            return true;
        }
        return false;
    }

    private boolean isCommish(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("isCommish"));
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

    @GetMapping("/CommissionerOverrideServlet")
    public String doGet(HttpServletRequest request,
                        Model model,
                        @RequestParam(required = false) String selectedUser,
                        @RequestParam(required = false) String message) throws IOException {

        System.out.println("CommissionerOverrideServlet: doGet method started");
        long startTime = System.nanoTime();

        // Require login first (handles expired sessions cleanly)
        String maybeRedirect = requireLoginOrRedirect(request);
        if (maybeRedirect != null) return maybeRedirect;

        // Then require role
        if (!isCommish(request)) {
            System.out.println("CommissionerOverrideServlet: not a Commish");
            return "redirect:/accessDenied.jsp";
        }

        servletUtility.setCommonAttributes(request, request.getServletContext());

        if (message != null) {
            model.addAttribute("message", java.net.URLDecoder.decode(message, java.nio.charset.StandardCharsets.UTF_8));
        }

        // Pull season/week
        String season = (String) request.getAttribute("season");
        String week   = (String) request.getAttribute("week");

        // Resolve selectedUser from multiple possible sources
        if (selectedUser == null) selectedUser = (String) request.getAttribute("selectedUser");
        if (selectedUser == null) selectedUser = (String) request.getAttribute("overrideUserId");

        if (season == null || week == null || selectedUser == null) {
            System.out.println("CommissionerOverrideServlet: Missing required parameters in doGet");
            return "redirect:/CommissionerDashboard";
        }

        // Parse "userId:userName" safely
        int userId;
        String userName;
        try {
            String[] parts = selectedUser.split(":");
            userId = Integer.parseInt(parts[0]);
            userName = (parts.length > 1) ? parts[1] : "";
        } catch (Exception ex) {
            System.err.println("CommissionerOverrideServlet: Bad selectedUser: " + selectedUser);
            return "redirect:/CommissionerDashboard";
        }

        int seasonInt = Integer.parseInt(season);
        int weekInt   = Integer.parseInt(week);

        User overrideUser = sqlConnectorUserTable.getUserById(userId);
        if (overrideUser == null) {
            model.addAttribute("message", "User not found.");
            return "redirect:/CommissionerDashboard";
        }

        String formattedUserDisplay = String.format("%s, %s (%s)",
                overrideUser.getLastName(), overrideUser.getFirstName(), overrideUser.getUsername());
        model.addAttribute("formattedUserDisplay", formattedUserDisplay);
        model.addAttribute("overrideUser", overrideUser);

        HttpSession session = request.getSession(false);
        session.setAttribute("overrideUserId", userId);
        session.setAttribute("overrideUserName", userName);

        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicks =
            (Map<String, Integer>) session.getAttribute("userRemainingPicks");
        if (userRemainingPicks == null) userRemainingPicks = java.util.Collections.emptyMap();

        model.addAttribute("remainingPicks", userRemainingPicks.getOrDefault(userName, 0));

        // Games + status conversion, try/catch already present in your code (unchanged)
        List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME;
        for (Game game : games) {
            game.setShowOdds(true);
            game.setShowScore(true);
            String utcDate = game.getDate();
            try {
                java.time.LocalDateTime utcDateTime = java.time.LocalDateTime.parse(utcDate.replace("Z",""), formatter);
                java.time.ZonedDateTime utcZoned = utcDateTime.atZone(java.time.ZoneId.of("UTC"));
                java.time.ZonedDateTime easternZoned = utcZoned.withZoneSameInstant(java.time.ZoneId.of("America/New_York"));
                game.setDate(easternZoned.toString());
                game.setStatus(convertStatus(game.getStatus()));
            } catch (Exception e) {
                System.err.println("Error processing game " + game.getGameID() + ": " + e.getMessage());
            }
        }

        Map<String, List<String>> selectedPicks = sqlConnectorPicksTable.getUserPicks(userId, seasonInt, weekInt);
        Map<String, String> teamNameToAbbrev    = sqlConnectorTeamsTable.getTeamNameToAbbrev();

        model.addAttribute("makePicksGames", games);
        model.addAttribute("selectedPicks", selectedPicks);
        model.addAttribute("teamNameToAbbrev", teamNameToAbbrev);
        model.addAttribute("overrideUserId", userId);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("overrideUserName", userName);

        long endTime = System.nanoTime();
        System.out.println("CommissionerOverrideServlet.doGet Method execution time: " +
            String.format("%.1f", (endTime - startTime) / 1_000_000_000.0) + " Seconds");

        return "commissionerOverridePicks";
    }


    @PostMapping("/CommissionerOverrideServlet")
    public String doPost(HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        System.out.println("CommissionerOverrideServlet: doPost method started");
        long startTime = System.nanoTime();

        // If this ever becomes XHR/JSON, handle unauthorized cleanly
        if (failAjaxUnauthorizedIfNeeded(request, (jakarta.servlet.http.HttpServletResponse) null, true)) {
            return null; // (If you plan to return JSON here, pass the real response and write JSON)
        }

        // Require login first
        String maybeRedirect = requireLoginOrRedirect(request);
        if (maybeRedirect != null) return maybeRedirect;

        // Then require role
        if (!isCommish(request)) {
            System.out.println("CommissionerOverrideServlet: not a Commish");
            return "redirect:/accessDenied.jsp";
        }

        servletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week   = (String) request.getAttribute("week");

        HttpSession session = request.getSession(false);
        Integer overrideUserId  = (session != null) ? (Integer) session.getAttribute("overrideUserId") : null;
        String  overrideUserName= (session != null) ? (String)  session.getAttribute("overrideUserName") : null;

        if (season == null || week == null || overrideUserId == null) {
            redirectAttributes.addFlashAttribute("error", "Missing required parameters.");
            return "redirect:/CommissionerDashboard";
        }

        int seasonInt = Integer.parseInt(season);
        int weekInt   = Integer.parseInt(week);

        // Parse form picks (your existing loop is fine) — unchanged
        Map<String, List<String>> newPicks = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String paramName = names.nextElement();
            if (paramName.startsWith("pick_")) {
                String gameId = paramName.substring(5);
                String[] pickValues = request.getParameterValues(paramName);
                if (pickValues != null) {
                    List<String> gamePicks = new ArrayList<>();
                    for (String pv : pickValues) {
                        String[] parts = pv.split("_");
                        if (parts.length == 2) {
                            String teamName = parts[0];
                            try {
                                int count = Integer.parseInt(parts[1]);
                                for (int i = 0; i < count; i++) gamePicks.add(teamName);
                            } catch (NumberFormatException ignore) {}
                        }
                    }
                    if (!gamePicks.isEmpty()) newPicks.put(gameId, gamePicks);
                }
            }
        }

        String message;
        try {
            sqlConnectorPicksTable.updateUserPicks(overrideUserId, seasonInt, weekInt, newPicks);
            message = "Picks have been successfully updated for user " + overrideUserName;
        } catch (Exception e) {
            System.err.println("Error updating picks: " + e.getMessage());
            message = "An error occurred while saving the picks. Please try again.";
        }

        long endTime = System.nanoTime();
        System.out.println("CommissionerOverrideServlet.doPost Method execution time: " +
            String.format("%.1f", (endTime - startTime) / 1_000_000_000.0) + " Seconds");

        String selectedUser = overrideUserId + ":" + (overrideUserName != null ? overrideUserName : "");
        return "redirect:/CommissionerOverrideServlet?selectedUser=" + selectedUser +
               "&season=" + season +
               "&week="   + week +
               "&message=" + URLEncoder.encode(message, "UTF-8");
    }

}
