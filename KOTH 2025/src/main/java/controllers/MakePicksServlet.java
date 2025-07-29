package controllers;

import helpers.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Game;
import services.NFLGameFetcherService;
import services.ServletUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model) throws ServletException, IOException {
        System.out.println("MakePicksServlet: doGet method started");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("MakePicksServlet: No valid session found, redirecting to login");
            return "redirect:/login";
        }

        ServletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week = (String) request.getAttribute("week");

        if (season == null) {
            season = (String) request.getServletContext().getAttribute("currentSeason");
        }
        if (week == null) {
            week = (String) request.getServletContext().getAttribute("currentWeek");
        }

        System.out.println("MakePicksServlet: Retrieved attributes - Season: " + season + ", Week: " + week);

        int seasonInt = Integer.parseInt(season);
        int weekInt = Integer.parseInt(week);

        String userName = (String) session.getAttribute("userName");
        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            System.out.println("MakePicksServlet: User ID not found in session");
            model.addAttribute("errorMessage", "User ID not found.");
            return "error";
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek =
                (Map<String, Integer>) session.getAttribute("userRemainingPicksPriorWeek");

        if (userRemainingPicksPriorWeek == null || userRemainingPicksPriorWeek.isEmpty()) {
            System.out.println("MakePicksServlet: userRemainingPicksPriorWeek is null or empty, redirecting to HomeServlet");
            return "redirect:/HomeServlet";
        }

        int remainingPicks = userRemainingPicksPriorWeek.getOrDefault(userName, 0);
        System.out.println("MakePicksServlet: Remaining picks for user " + userName + ": " + remainingPicks);

        try {
            // ✅ Fetch filtered ESPN games for current week
            System.out.println("MakePicksServlet: Fetching ESPN score data");
            List<Game> espnGames = nflGameFetcherService.fetchCurrentWeekGames();
            System.out.println("MakePicksServlet: Fetched " + espnGames.size() + " games for current week");

            // ✅ Update DB with fresh game data
            sqlConnectorGameTable.updateGameTableMinimal(espnGames);

            // ✅ Retrieve picks and teams
            Map<String, List<String>> selectedPicks = sqlConnectorPicksTable.getUserPicks(userId, seasonInt, weekInt);
            Map<String, String> teamNameToAbbrev = sqlConnectorTeamsTable.getTeamNameToAbbrev();

            // ✅ Get games from DB (already filtered by season/week)
            List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            System.out.println("MakePicksServlet: Retrieved " + games.size() + " games for Season: " + season + ", Week: " + week);

            // ✅ Update odds for scheduled games
            updateOddsForScheduledGames(games);
            games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);

            // ✅ Process statuses and display flags
            processGameStatus(games);

            // ✅ Add attributes for JSP
            model.addAttribute("remainingPicks", remainingPicks);
            model.addAttribute("selectedPicks", selectedPicks);
            model.addAttribute("teamNameToAbbrev", teamNameToAbbrev);
            model.addAttribute("makePicksGames", games);
            model.addAttribute("userName", userName);
            model.addAttribute("season", season);
            model.addAttribute("currentWeek", week);

            long endTime = System.nanoTime();
            double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
            System.out.printf("MakePicksServlet.doGet Method execution time: %.1f Seconds%n", durationInSeconds);

            return "makePicks";

        } catch (Exception e) {
            System.err.println("MakePicksServlet: Error processing request: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error processing request.");
            return "error";
        }
    }

 
    @PostMapping("/MakePicksServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model) throws ServletException, IOException {
        System.out.println("MakePicksServlet: doPost method started");
        long startTime = System.nanoTime();
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("MakePicksServlet: No valid session found, redirecting to login");
            return "redirect:/login";
        }

        ServletUtility.setCommonAttributes(request, request.getServletContext());

        String season = (String) request.getAttribute("season");
        String week = (String) request.getAttribute("week");

        System.out.println("MakePicksServlet: Retrieved attributes - Season: " + season + ", Week: " + week);

        if (season == null || week == null) {
            System.out.println("MakePicksServlet: Missing required parameters");
            model.addAttribute("errorMessage", "Missing required parameters.");
            return "error";
        }

        System.out.println("MakePicksServlet: Processing picks for Season: " + season + ", Week: " + week);

        @SuppressWarnings("unused")
		int seasonInt = Integer.parseInt(season);
        @SuppressWarnings("unused")
		int weekInt = Integer.parseInt(week);

        String userName = (String) session.getAttribute("userName");
        Integer userId = (Integer) session.getAttribute("userId");
        
        if (userId == null) {
            System.out.println("MakePicksServlet: User ID not found in session");
            model.addAttribute("errorMessage", "User ID not found.");
            return "error";
        }

        // Get remaining picks from prior week
        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek = 
            (Map<String, Integer>) session.getAttribute("userRemainingPicksPriorWeek");

        if (userRemainingPicksPriorWeek == null || userRemainingPicksPriorWeek.isEmpty()) {
            System.out.println("MakePicksServlet: userRemainingPicksPriorWeek is null or empty");
            model.addAttribute("errorMessage", "Unable to validate picks - missing prior week data.");
            return "error";
        }

        int remainingPicks = userRemainingPicksPriorWeek.getOrDefault(userName, 0);
        System.out.println("MakePicksServlet: Prior week remaining picks for user " + userName + ": " + remainingPicks);

        // Process the picks
        Map<String, List<String>> newPicks = new HashMap<>();
        int totalPicksSubmitted = 0;
        
        // Get all form parameters
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            if (paramName.startsWith("pick_")) {
                String gameId = paramName.substring(5); // Remove "pick_" prefix
                String[] pickValues = request.getParameterValues(paramName); // Get all values for this parameter
                
                if (pickValues != null) {
                    List<String> gamePicks = new ArrayList<>();
                    
                    System.out.println("Processing game " + gameId + " with " + pickValues.length + " picks"); // Debug log
                    
                    for (String pickValue : pickValues) {
                        String[] parts = pickValue.split("_");
                        if (parts.length == 2) {
                            String teamName = parts[0];
                            int count = Integer.parseInt(parts[1]);
                            System.out.println("  Team: " + teamName + ", Count: " + count); // Debug log
                            
                            totalPicksSubmitted += count;
                            
                            // Add the team name to the list the specified number of times
                            for (int i = 0; i < count; i++) {
                                gamePicks.add(teamName);
                            }
                        }
                    }
                    
                    if (!gamePicks.isEmpty()) {
                        newPicks.put(gameId, gamePicks);
                        System.out.println("Added picks for game " + gameId + ": " + gamePicks); // Debug log
                    }
                }
            }
        }

        // Validate total picks against remaining picks
        if (totalPicksSubmitted > remainingPicks) {
            String errorMessage = "Cannot submit " + totalPicksSubmitted + " picks when only " + remainingPicks + " picks remain from prior week.";
            System.out.println("MakePicksServlet: " + errorMessage);
            request.setAttribute("errorMessage", errorMessage);
            return doGet(request, response, model);
        }

        // Update picks in database
        try {
            System.out.println("Total picks being submitted: " + totalPicksSubmitted);
            
            sqlConnectorPicksTable.updateUserPicks(
                userId.intValue(), 
                Integer.parseInt(season), 
                Integer.parseInt(week), 
                newPicks
            );
            
            String message = "Picks successfully updated!";
            request.setAttribute("message", message);
        } catch (Exception e) {
            String errorMessage = "Error updating picks: " + e.getMessage();
            request.setAttribute("errorMessage", errorMessage);
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("MakePicksServlet.doPost Method execution time: %.1f Seconds%n", durationInSeconds);

        // Return the view from doGet
        return doGet(request, response, model);
    }

    
    private void updateOddsForScheduledGames(List<Game> games) {
        System.out.println("\nMakePicksServlet.updateOddsForScheduledGames: Starting odds update for scheduled games");
        int scheduledGamesCount = 0;
        int updatedGamesCount = 0;
        
        System.out.println("Total games to check: " + games.size());
        
        for (Game game : games) {
            System.out.println("\nChecking game: GameID=" + game.getGameID() + ", Status=" + game.getStatus());
            
            // Handle both "STATUS_SCHEDULED" and "Scheduled"
            if ("STATUS_SCHEDULED".equals(game.getStatus()) || "Scheduled".equals(game.getStatus())) {
                scheduledGamesCount++;
                try {
                    String gameId = String.valueOf(game.getGameID());
                    System.out.println("Processing odds for GameID: " + gameId);
                    
                    String oddsResponse = ApiFetchers.FetchESPNGameOdds(gameId);
                    if (oddsResponse != null && !oddsResponse.isEmpty()) {
                        System.out.println("Received odds response for GameID: " + gameId + 
                                         "\nResponse length: " + oddsResponse.length());
                        Game updatedGame = ApiParsers.ParseESPNOdds(oddsResponse, game);
                        
                        // Only update if we actually got odds data
                        if (updatedGame.getPointSpread() != null || updatedGame.getOverUnder() != null) {
                            sqlConnectorGameTable.updateGameOdds(updatedGame);
                            updatedGamesCount++;
                        } else {
                            System.out.println("No valid odds data found for GameID: " + gameId);
                        }
                    } else {
                        System.out.println("No odds response received for GameID: " + gameId);
                    }
                } catch (Exception e) {
                    System.err.println("Error updating odds for GameID " + game.getGameID());
                    System.err.println("Error message: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Skipping game: " + game.getGameID() + " - Status is " + game.getStatus());
            }
        }
        
        System.out.println("\nMakePicksServlet.updateOddsForScheduledGames: Completed odds update" +
                          "\n Total games checked: " + games.size() +
                          "\n Total scheduled games found: " + scheduledGamesCount +
                          "\n Successfully updated games: " + updatedGamesCount);
    }
    
    private void processGameStatus(List<Game> games) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        
        for (Game game : games) {
            try {
                // Convert date/time
                String utcDate = game.getDate();
                System.out.println("Processing game date/time - Original UTC: " + utcDate);
                
                if (utcDate != null && !utcDate.isEmpty()) {
                    LocalDateTime utcDateTime = LocalDateTime.parse(utcDate.replace("Z", ""), formatter);
                    ZonedDateTime utcZoned = utcDateTime.atZone(ZoneId.of("UTC"));
                    ZonedDateTime easternZoned = utcZoned.withZoneSameInstant(ZoneId.of("America/New_York"));
                    game.setDate(easternZoned.toString());
                    System.out.println("Converted to Eastern: " + easternZoned);
                }

                // Convert status
                String dbStatus = game.getStatus();
                String convertedStatus = convertStatus(dbStatus);
                System.out.println("Game " + game.getGameID() + " - Converting status from: " + dbStatus + " to: " + convertedStatus);
                game.setStatus(convertedStatus);

                // Set display flags based on status
                updateGameDisplayFlags(game);

            } catch (Exception e) {
                System.err.println("Error processing game " + game.getGameID() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateGameDisplayFlags(Game game) {
        String status = game.getStatus();
        
        // Default values
        game.setShowOdds(false);
        game.setShowScore(false);

        switch (status) {
            case STATUS_SCHEDULED:
                game.setShowOdds(true);
                System.out.println("Game " + game.getGameID() + ": Showing odds (Spread: " + 
                                 game.getPointSpread() + ", O/U: " + game.getOverUnder() + ")");
                break;
                
            case STATUS_IN_PROGRESS:
                game.setShowScore(true);
                System.out.println("Game " + game.getGameID() + ": Showing score (" + 
                                 game.getAwayTeamName() + ": " + game.getAwayScore() + ", " +
                                 game.getHomeTeamName() + ": " + game.getHomeScore() + ")");
                
                // Add game clock info for in-progress games
                if (game.getDisplayClock() != null && game.getPeriod() != null) {
                    System.out.println("Game " + game.getGameID() + ": " + 
                                     "Quarter " + game.getPeriod() + " - " + game.getDisplayClock());
                }
                break;
                
            case STATUS_FINAL:
                game.setShowScore(true);
                System.out.println("Game " + game.getGameID() + ": Final score (" + 
                                 game.getAwayTeamName() + ": " + game.getAwayScore() + ", " +
                                 game.getHomeTeamName() + ": " + game.getHomeScore() + ")");
                break;
                
            default:
                System.out.println("Game " + game.getGameID() + ": Unknown status - " + status);
                break;
        }
    }
}