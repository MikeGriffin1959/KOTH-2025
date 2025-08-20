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

import helpers.SqlConnectorGameTable;
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

    @GetMapping({"/", "/home", "/index", "/HomeServlet"})
    public String doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("HomeServlet: doGet() started");
        long startTime = System.nanoTime();

        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                System.out.println("HomeServlet: No session found, redirecting to LoginServlet");
                return "redirect:/LoginServlet";
            }

            ServletContext context = request.getServletContext();

            // ✅ Process all common data, including total pot, players left, picks left
            commonProcessingService.processCommonData(request, response, context);

            // ✅ Fetch current week games & update DB
            List<Game> parsedGames = nflGameFetcherService.fetchCurrentWeekGames();
            sqlConnectorGameTable.updateGameTableMinimal(parsedGames);

            // ✅ Retrieve season and week from request attributes (set in processCommonData)
            String seasonStr = (String) request.getAttribute("season");
            String weekStr = (String) request.getAttribute("currentWeek");
            int seasonInt = Integer.parseInt(seasonStr);
            int weekInt = Integer.parseInt(weekStr);

            // ✅ Get all weeks picks data
            Map<Integer, Map<String, List<Map<String, Object>>>> allWeeksData =
                    sqlConnectorPicksTable.getPicksForAllWeeks(seasonInt, weekInt);

            // ✅ Calculate team pick counts and results
            Map<String, Integer> teamPickCounts = new HashMap<>();
            Map<String, Boolean> teamResults = new HashMap<>();
            calculateTeamPickCountsAndResults(allWeeksData, weekInt, teamPickCounts, teamResults,
                    (Map<String, String>) context.getAttribute("teamNameToAbbrev"));

            // ✅ Prepare user full names
            List<String> allUsers = (List<String>) session.getAttribute("allUsers");
            Map<String, String> userFullNames = getUserFullNames(allUsers, context);

            boolean userHasPaid = sqlConnectorUserTable.hasUserPaidForSeason(
                    (Integer) session.getAttribute("userId"), seasonInt);

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

    private Map<String, String> getUserFullNames(List<String> usernames, ServletContext context) {
        Map<String, String> fullNames = new HashMap<>();
        for (String username : usernames) {
            User user = servletUtility.getUserFromContext(context, username); // ✅ Injected utility
            if (user != null) {
                fullNames.put(username, user.getFirstName() + " " + user.getLastName());
            } else {
                fullNames.put(username, username);
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

                // Count picks
                for (Map<String, Object> pick : gamePicks) {
                    String selectedTeam = (String) pick.get("selectedTeam");
                    if (selectedTeam != null) {
                        teamPickCounts.merge(selectedTeam, 1, Integer::sum);
                    }
                }

                // Store results
                if (game.get("status") != null && game.get("status").toString().contains("FINAL")) {
                    String homeTeam = (String) game.get("homeTeamName");
                    String awayTeam = (String) game.get("awayTeamName");
                    int homeScore = (int) game.get("homeScore");
                    int awayScore = (int) game.get("awayScore");

                    boolean homeTeamWon = homeScore > awayScore;
                    teamResults.put(teamNameToAbbrev.get(homeTeam), homeTeamWon);
                    teamResults.put(teamNameToAbbrev.get(awayTeam), !homeTeamWon);
                }
            }
        }
    }
}
