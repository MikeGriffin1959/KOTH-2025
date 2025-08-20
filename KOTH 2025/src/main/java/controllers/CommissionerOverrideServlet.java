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
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("CommissionerOverrideServlet: User not logged in, redirecting to LoginServlet");
            return "redirect:/LoginServlet";
        }

        if (!isCommish(request)) {
            System.out.println("CommissionerOverrideServlet: User is not a Commish, redirecting to access denied page");
            return "redirect:/accessDenied.jsp";
        }

        // ✅ Now using the injected utility bean
        servletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week = (String) request.getAttribute("week");

        if (message != null) {
            model.addAttribute("message", URLDecoder.decode(message, "UTF-8"));
        }

        if (selectedUser == null) {
            selectedUser = (String) request.getAttribute("selectedUser");
        }

        if (selectedUser == null) {
            selectedUser = (String) request.getAttribute("overrideUserId");
        }

        System.out.println("doGet - Season: " + season + ", Week: " + week + " SelectedUserId: " + selectedUser);

        if (season == null || week == null || selectedUser == null) {
            System.out.println("CommissionerOverrideServlet: Missing required parameters in doGet");
            return "redirect:/CommissionerDashboard";
        }

        int seasonInt = Integer.parseInt(season);
        int weekInt = Integer.parseInt(week);

        // Parse the combined selectedUser value
        String[] userParts = selectedUser.split(":");
        int userId = Integer.parseInt(userParts[0]);
        String userName = userParts[1];

        // Get the user object for the selected user
        User overrideUser = sqlConnectorUserTable.getUserById(userId);

        // Create formatted user display string
        String formattedUserDisplay = String.format("%s, %s (%s)",
                overrideUser.getLastName(),
                overrideUser.getFirstName(),
                overrideUser.getUsername());

        // Add the formatted display to the model
        model.addAttribute("formattedUserDisplay", formattedUserDisplay);
        model.addAttribute("overrideUser", overrideUser);

        // Store the override user info in the session for use in POST
        session.setAttribute("overrideUserId", userId);
        session.setAttribute("overrideUserName", userName);

        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicks = (Map<String, Integer>) session.getAttribute("userRemainingPicks");

        if (userRemainingPicks == null || userRemainingPicks.isEmpty()) {
            System.out.println("CommissionerOverrideServlet: userRemainingPicks is null or empty, redirecting to HomeServlet");
            return "redirect:/HomeServlet";
        }

        int remainingPicks = userRemainingPicks.getOrDefault(userName, 0);
        model.addAttribute("remainingPicks", remainingPicks);

        // Fetch games for the week
        List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);

        // Process each game
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        for (Game game : games) {
            game.setShowOdds(true);
            game.setShowScore(true);

            String utcDate = game.getDate();
            try {
                LocalDateTime utcDateTime = LocalDateTime.parse(utcDate.replace("Z", ""), formatter);
                ZonedDateTime utcZoned = utcDateTime.atZone(ZoneId.of("UTC"));
                ZonedDateTime easternZoned = utcZoned.withZoneSameInstant(ZoneId.of("America/New_York"));
                game.setDate(easternZoned.toString());

                String dbStatus = game.getStatus();
                String convertedStatus = convertStatus(dbStatus);
                game.setStatus(convertedStatus);
            } catch (Exception e) {
                System.err.println("Error processing game " + game.getGameID() + ": " + e.getMessage());
            }
        }

        // Get the user's current picks and team abbreviations
        Map<String, List<String>> selectedPicks = sqlConnectorPicksTable.getUserPicks(userId, seasonInt, weekInt);
        Map<String, String> teamNameToAbbrev = sqlConnectorTeamsTable.getTeamNameToAbbrev();

        // Add all necessary attributes to the model
        model.addAttribute("makePicksGames", games);
        model.addAttribute("selectedPicks", selectedPicks);
        model.addAttribute("teamNameToAbbrev", teamNameToAbbrev);
        model.addAttribute("overrideUserId", userId);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("overrideUserName", userName);

        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("CommissionerOverrideServlet.doGet Method execution time: " + String.format("%.1f", durationInSeconds) + " Seconds");

        return "commissionerOverridePicks";
    }

    @PostMapping("/CommissionerOverrideServlet")
    public String doPost(HttpServletRequest request,
                         RedirectAttributes redirectAttributes) throws IOException {
        System.out.println("CommissionerOverrideServlet: doPost method started");
        long startTime = System.nanoTime();

        if (!isCommish(request)) {
            System.out.println("CommissionerOverrideServlet: User is not a Commish, redirecting to access denied page");
            return "redirect:/accessDenied.jsp";
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("CommissionerOverrideServlet: User not logged in, redirecting to LoginServlet");
            return "redirect:/LoginServlet";
        }

        // ✅ Use the injected utility to refresh common attributes
        servletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week = (String) request.getAttribute("week");

        Integer overrideUserId = (Integer) session.getAttribute("overrideUserId");
        String overrideUserName = (String) session.getAttribute("overrideUserName");

        System.out.println("doPost - Season: " + season + ", Week: " + week + ", OverrideUserId: " + overrideUserId);

        if (season == null || week == null || overrideUserId == null) {
            redirectAttributes.addFlashAttribute("error", "Missing required parameters.");
            return "redirect:/CommissionerDashboard";
        }

        int seasonInt = Integer.parseInt(season);
        int weekInt = Integer.parseInt(week);

        // Process picks from the form submission
        Map<String, List<String>> newPicks = new HashMap<>();
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
                            try {
                                int pickCount = Integer.parseInt(parts[1]);
                                for (int i = 0; i < pickCount; i++) {
                                    gamePicks.add(teamName);
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("Error parsing pick count for game " + gameId + ": " + e.getMessage());
                            }
                        }
                    }
                    if (!gamePicks.isEmpty()) {
                        newPicks.put(gameId, gamePicks);
                    }
                }
            }
        }

        String message;
        try {
            sqlConnectorPicksTable.updateUserPicks(overrideUserId, seasonInt, weekInt, newPicks);
            message = "Picks have been successfully updated for user " + overrideUserName;
        } catch (Exception e) {
            System.err.println("Error updating picks in database: " + e.getMessage());
            message = "An error occurred while saving the picks. Please try again.";
        }

        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("CommissionerOverrideServlet.doPost Method execution time: " + String.format("%.1f", durationInSeconds) + " Seconds");

        String selectedUser = overrideUserId + ":" + overrideUserName;
        return "redirect:/CommissionerOverrideServlet?selectedUser=" + selectedUser +
                "&season=" + season +
                "&week=" + week +
                "&message=" + URLEncoder.encode(message, "UTF-8");
    }

    private boolean isCommish(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Boolean isCommish = (Boolean) session.getAttribute("isCommish");
            return isCommish != null && isCommish;
        }
        return false;
    }
}
