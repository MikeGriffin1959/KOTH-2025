package controllers;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import helpers.ApiParsers;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksTable;
import helpers.SqlConnectorUserTable;
import services.NFLGameFetcherService;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Game;
import model.User;
import services.CommonProcessingService;
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
            
            // Log all session attributes
            Enumeration<String> attributeNames = session.getAttributeNames();
            System.out.println("HomeServlet: All session attributes:");
            while (attributeNames.hasMoreElements()) {
                String attributeName = attributeNames.nextElement();
                System.out.println("  " + attributeName + ": " + session.getAttribute(attributeName));
            }
            
            // Use CommonProcessingService to handle common processing
            if (request.getAttribute("commonProcessingDone") == null) {
                commonProcessingService.processCommonData(request, response, context);
            }

            String seasonStr = (String) request.getAttribute("season");
            String weekStr = (String) request.getAttribute("currentWeek");

            if (seasonStr == null) seasonStr = (String) session.getAttribute("season");
            if (weekStr == null) weekStr = (String) session.getAttribute("currentWeek");

            System.out.println("HomeServlet.doGet: Retrieved attributes after CommonProcessingService - Season: " + seasonStr +
                    ", Week: " + weekStr);

            if (seasonStr == null) {
                LocalDateTime now = LocalDateTime.now();
                int year = now.getYear();
                
                if (now.getMonthValue() <= 2) {
                    year--;
                } else {
                    LocalDate seasonStart = LocalDate.of(year, Month.SEPTEMBER, 1)
                        .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                        .plusDays(2);
                        
                    if (now.toLocalDate().isBefore(seasonStart)) {
                        year--;
                    }
                }
                seasonStr = String.valueOf(year);
            }

            if (weekStr == null || weekStr.isEmpty()) {
                weekStr = (String) session.getAttribute("currentWeek");
                if (weekStr == null || weekStr.isEmpty()) {
                    weekStr = "1";
                }
            }

            int seasonInt = Integer.parseInt(seasonStr);
            int weekInt = Integer.parseInt(weekStr);

            System.out.println("HomeServlet.doGet: Using values - Season: " + seasonStr + ", Week: " + weekStr);

            System.out.println("HomeServlet.doGet: Fetching scores from ESPN API");
            String apiResponse = nflGameFetcherService.fetchCurrentWeekGames();

            if (apiResponse == null) {
                System.out.println("HomeServlet: Failed to fetch data from ESPN API");
                return "error";
            }

            System.out.println("HomeServlet.doGet: Parsing ESPN API response");
            List<Game> parsedGames = ApiParsers.ParseESPNAPIMinimal(apiResponse);
            System.out.println("HomeServlet: Parsed " + parsedGames.size() + " games from ESPN API");

            // Create a new list for games with verified season/week values
            List<Game> gamesToUpdate = new ArrayList<>();
            System.out.println("HomeServlet: Setting season/week for games and preparing for update");
            
            for (Game parsedGame : parsedGames) {
                Game gameToUpdate = new Game();
                
                // Set the crucial identifying fields
                gameToUpdate.setGameID(parsedGame.getGameID());
                gameToUpdate.setSeason(seasonInt);
                gameToUpdate.setWeek(weekInt);
                
                // Set all other fields from the parsed game
                gameToUpdate.setStatus(parsedGame.getStatus());
                gameToUpdate.setHomeTeamId(parsedGame.getHomeTeamId());
                gameToUpdate.setAwayTeamId(parsedGame.getAwayTeamId());
                gameToUpdate.setHomeScore(parsedGame.getHomeScore());
                gameToUpdate.setAwayScore(parsedGame.getAwayScore());
                gameToUpdate.setDate(parsedGame.getDate());
                gameToUpdate.setHomeTeamName(parsedGame.getHomeTeamName());
                gameToUpdate.setAwayTeamName(parsedGame.getAwayTeamName());
                gameToUpdate.setDisplayClock(parsedGame.getDisplayClock());
                gameToUpdate.setPeriod(parsedGame.getPeriod());
                
                gamesToUpdate.add(gameToUpdate);
                
                // Debug logging for each game
                System.out.println(String.format("Preparing game %d - Season: %d, Week: %d, Status: %s", 
                    gameToUpdate.getGameID(), 
                    gameToUpdate.getSeason(), 
                    gameToUpdate.getWeek(),
                    gameToUpdate.getStatus()));
            }

            System.out.println("HomeServlet: Updating " + gamesToUpdate.size() + " games in database");
            sqlConnectorGameTable.updateGameTableMinimal(gamesToUpdate);

            System.out.println("HomeServlet: Fetching all weeks' data from database");
            Map<Integer, Map<String, List<Map<String, Object>>>> allWeeksData = 
                sqlConnectorPicksTable.getPicksForAllWeeks(seasonInt, weekInt);

//            @SuppressWarnings("unchecked")
//            Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData = 
//                (Map<Integer, Map<String, List<Map<String, Object>>>>) session.getAttribute("optimizedData");
//
//            if (optimizedData != null && optimizedData.containsKey(weekInt)) {
//                allWeeksData.put(weekInt, optimizedData.get(weekInt));
//            }
//
//            session.setAttribute("optimizedData", allWeeksData);
//            request.setAttribute("optimizedData", allWeeksData);

            // Handle team picks, results, and other processing
            System.out.println("HomeServlet: Calculating teamPickCounts and teamResults");
            Map<String, Integer> teamPickCounts = new HashMap<>();
            Map<String, Boolean> teamResults = new HashMap<>();
            calculateTeamPickCountsAndResults(allWeeksData, weekInt, teamPickCounts, teamResults,
                    (Map<String, String>) session.getAttribute("teamNameToAbbrev"));

            System.out.println("HomeServlet: Fetching user full names");
            Map<String, String> userFullNames = getUserFullNames(
                    (List<String>) session.getAttribute("allUsers"), context);

            boolean userHasPaid = sqlConnectorUserTable.hasUserPaidForSeason(
                    (Integer) session.getAttribute("userId"), seasonInt);

            setRequestAttributes(request, seasonStr, weekStr, allWeeksData,
                    (Map<String, Integer>) session.getAttribute("initialPicks"),
                    (Map<String, Integer>) session.getAttribute("userLosses"),
                    (Map<String, Integer>) session.getAttribute("userRemainingPicks"),
                    (Map<String, String>) session.getAttribute("teamNameToAbbrev"),
                    (List<String>) session.getAttribute("allUsers"),
                    (Integer) session.getAttribute("totalPot"),
                    (Integer) session.getAttribute("usersWithRemainingPicks"),
                    (Integer) session.getAttribute("totalRemainingPicks"),
                    (Integer) session.getAttribute("currentUserRemainingPicks"),
                    userFullNames, userHasPaid,
                    (Map<Integer, Map<String, String>>) context.getAttribute("gameWinners"),
                    teamPickCounts, teamResults);

            System.out.println("HomeServlet: Forwarding to JSP");

            long endTime = System.nanoTime();
            double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
            System.out.println("HomeServlet.doGet Method execution time: " + String.format("%.1f", durationInSeconds) + " Seconds");

            return "home";
        } catch (Exception e) {
            System.out.println("HomeServlet: Unhandled exception occurred");
            e.printStackTrace();
            return "error";
        }
    }
    
    @PostMapping("/HomeServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("HomeServlet: doPost() called, delegating to doGet()");
        return doGet(request, response);
    }



    private Map<String, String> getUserFullNames(List<String> usernames, ServletContext context) {
        System.out.println("HomeServlet: getUserFullNames method started");
    	
        Map<String, String> fullNames = new HashMap<>();
        for (String username : usernames) {
            User user = ServletUtility.getUserFromContext(context, username);
            if (user != null) {
                String fullName = user.getFirstName() + " " + user.getLastName();
                fullNames.put(username, fullName);
            } else {
                fullNames.put(username, username); // Fallback to username if user not found
                System.out.println("HomeServlet: User not found in context for username: " + username);
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
        System.out.println("HomeServlet: setRequestAttributes method started");
        
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

    private void logReceivedData(HttpServletRequest request) {
        System.out.println("HomeServlet: logReceivedData method started");        
        System.out.println("HomeServlet: Logging received data");
        System.out.println("  optimizedData: " + request.getAttribute("optimizedData"));
        System.out.println("  initialPicks: " + request.getAttribute("initialPicks"));
        System.out.println("  userLosses: " + request.getAttribute("userLosses"));
        System.out.println("  userRemainingPicks: " + request.getAttribute("userRemainingPicks"));
        System.out.println("  teamNameToAbbrev: " + request.getAttribute("teamNameToAbbrev"));
        System.out.println("  allUsers: " + request.getAttribute("allUsers"));
        System.out.println("  totalPot: " + request.getAttribute("totalPot"));
        System.out.println("  usersWithRemainingPicks: " + request.getAttribute("usersWithRemainingPicks"));
        System.out.println("  totalRemainingPicks: " + request.getAttribute("totalRemainingPicks"));
        System.out.println("  currentUserRemainingPicks: " + request.getAttribute("currentUserRemainingPicks"));
        System.out.println("  userFullNames: " + request.getAttribute("userFullNames"));
        System.out.println("  userHasPaid: " + request.getAttribute("userHasPaid"));
        System.out.println("  gameWinners: " + request.getAttribute("gameWinners"));
        System.out.println("  teamPickCounts: " + request.getAttribute("teamPickCounts"));
        System.out.println("  teamResults: " + request.getAttribute("teamResults"));
    }
    
	private void calculateTeamPickCountsAndResults(Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData,
	                                           int currentWeekInt, Map<String, Integer> teamPickCounts,
	                                           Map<String, Boolean> teamResults,
	                                           Map<String, String> teamNameToAbbrev) {
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