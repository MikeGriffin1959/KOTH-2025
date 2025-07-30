package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import helpers.SqlConnectorUserTable;
import helpers.SqlConnectorTeamsTable;
import helpers.SqlConnectorPicksTable;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksPriceTable;
import model.User;
import model.Game;
import model.PicksPrice;

@Service
public class CommonProcessingService {

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private SqlConnectorTeamsTable sqlConnectorTeamsTable;

    @Autowired
    private SqlConnectorPicksTable sqlConnectorPicksTable;

    @Autowired
    private SqlConnectorGameTable sqlConnectorGameTable;
    
    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;
    
    private class ProcessingResult {
        int totalPot;
        int usersWithRemainingPicks;
        int totalRemainingPicks;

        ProcessingResult() {
            this.totalPot = 0;
            this.usersWithRemainingPicks = 0;
            this.totalRemainingPicks = 0;
        }
    }


    @SuppressWarnings("unchecked")
    public void processCommonData(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext)
            throws ServletException, IOException {
        System.out.println("CommonProcessingService.processCommonData method started");

        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            throw new ServletException("No active session found");
        }

        String currentSeason = (String) servletContext.getAttribute("season");
        String currentWeek = (String) servletContext.getAttribute("week");

        System.out.println("  Season: " + currentSeason + ", Week: " + currentWeek);

        int season = Integer.parseInt(currentSeason);
        int week = Integer.parseInt(currentWeek);

        String userName = (String) httpSession.getAttribute("userName");
        Integer userId = (Integer) httpSession.getAttribute("userId");
        Boolean isAdmin = (Boolean) httpSession.getAttribute("isAdmin");
        Boolean isCommish = (Boolean) httpSession.getAttribute("isCommish");

        System.out.println("  Name: " + userName + ", ID: " + userId + ", Admin: " + isAdmin + ", Commish: " + isCommish);

        // ✅ Fetch allowSignUp from DB and set in application scope
        try {
            PicksPrice picksPrice = sqlConnectorPicksPriceTable
                    .getPickPrices(season)
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (picksPrice != null) {
                boolean allowSignUp = picksPrice.isAllowSignUp();
                servletContext.setAttribute("allowSignUp", allowSignUp);
                System.out.println("CommonProcessingService: allowSignUp set to " + allowSignUp);
            } else {
                servletContext.setAttribute("allowSignUp", false);
                System.out.println("CommonProcessingService: No PicksPrice found, default allowSignUp=false");
            }
        } catch (Exception e) {
            System.err.println("Error fetching allowSignUp: " + e.getMessage());
            servletContext.setAttribute("allowSignUp", false);
        }

        // Retrieve or update cached data
        Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData = getCachedData(servletContext, "optimizedData");
        Map<String, String> teamNameToAbbrev = getCachedData(servletContext, "teamNameToAbbrev");
        List<User> allSeasonUsers = getCachedData(servletContext, "allSeasonUsers");
        Map<String, Integer> initialPicks = getCachedData(servletContext, "initialPicks");
        Map<String, Integer> userLosses = getCachedData(servletContext, "userLosses");

        Map<String, Integer> userRemainingPicks = getCachedData(servletContext, "userRemainingPicks");
        if (userRemainingPicks == null || userRemainingPicks.isEmpty()) {
            System.out.println("  userRemainingPicks is null or empty, recalculating");
            updatePicksData(servletContext);
            userRemainingPicks = (Map<String, Integer>) servletContext.getAttribute("userRemainingPicks");
        }

        System.out.println("  optimizedData size: " + (optimizedData != null ? optimizedData.size() : "null"));
        System.out.println("  teamNameToAbbrev size: " + (teamNameToAbbrev != null ? teamNameToAbbrev.size() : "null"));
        System.out.println("  allSeasonUsers size: " + (allSeasonUsers != null ? allSeasonUsers.size() : "null"));
        System.out.println("  initialPicks size: " + (initialPicks != null ? initialPicks.size() : "null"));
        System.out.println("  userLosses size: " + (userLosses != null ? userLosses.size() : "null"));
        System.out.println("  userRemainingPicks size: " + (userRemainingPicks != null ? userRemainingPicks.size() : "null"));

        // Process user data with new approach
        List<String> allUsers = new ArrayList<>();
        ProcessingResult result = new ProcessingResult();

        if (allSeasonUsers != null) {
            for (User user : allSeasonUsers) {
                processUserData(user, allUsers, userRemainingPicks, initialPicks, userLosses, result, servletContext);
            }
        } else {
            System.out.println("  allSeasonUsers is null after cache update");
            throw new ServletException("Unable to retrieve user data");
        }

        List<Game> games = getCachedData(servletContext, "gamesUpToWeek");
        List<Game> gamesForWeek = getCachedData(servletContext, "gamesForWeek");

        boolean userHasPaid = sqlConnectorUserTable.hasUserPaidForSeason(userId, season);

        Map<String, Map<Integer, Integer>> remainingPicksWeekly = calculateRemainingPicksWeekly(servletContext);
        servletContext.setAttribute("remainingPicksWeekly", remainingPicksWeekly);

        // Set attributes in the session
        setSessionAttributes(httpSession, servletContext, season, week, userName, userId, isAdmin, isCommish,
                result.totalPot, result.usersWithRemainingPicks, result.totalRemainingPicks, userRemainingPicks, userHasPaid,
                optimizedData, initialPicks, userLosses, teamNameToAbbrev, allUsers, games, gamesForWeek,
                remainingPicksWeekly);

        // Set attributes in the request as well
        request.setAttribute("season", String.valueOf(season));
        request.setAttribute("currentWeek", String.valueOf(week));
        request.setAttribute("totalPot", result.totalPot);
        request.setAttribute("usersWithRemainingPicks", result.usersWithRemainingPicks);
        request.setAttribute("totalRemainingPicks", result.totalRemainingPicks);

        System.out.println("  userRemainingPicks for " + userName + ": " + userRemainingPicks.get(userName));
        System.out.println("  Total Pot: $" + result.totalPot);
        System.out.println("  Users with Remaining Picks: " + result.usersWithRemainingPicks);
        System.out.println("  Total Remaining Picks: " + result.totalRemainingPicks);

        // Set a flag to indicate that common processing has been done
        request.setAttribute("commonProcessingDone", true);

        System.out.println("CommonProcessingService.processCommonData() completed");
    }


    public void updateSeasonAndWeek(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updateSeasonAndWeek method started");
        int currentSeason = nflSeasonCalculator.getCurrentNFLSeason();
        int currentWeek = nflSeasonCalculator.getCurrentNFLWeekNumber();

        servletContext.setAttribute("season", String.valueOf(currentSeason));
        servletContext.setAttribute("week", String.valueOf(currentWeek));

        System.out.println("  Updated season: " + currentSeason + ", week: " + currentWeek);
    }

    public void updateTeamData(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updateTeamData method started");
        Map<String, String> teamNameToAbbrev = sqlConnectorTeamsTable.getTeamNameToAbbrev();
        if (teamNameToAbbrev == null || teamNameToAbbrev.isEmpty()) {
            System.err.println("WARNING: Team name map is NULL or EMPTY. Check DB.");
        } else {
            System.out.println("Updated team data, total teams: " + teamNameToAbbrev.size());
        }
        servletContext.setAttribute("teamNameToAbbrev", teamNameToAbbrev != null ? teamNameToAbbrev : new HashMap<>());
    }



    public void updateUserData(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updateUserData method started");
        int currentSeason = Integer.parseInt((String) servletContext.getAttribute("season"));
        int currentWeek = Integer.parseInt((String) servletContext.getAttribute("week"));

        List<User> currentSeasonUsers = sqlConnectorUserTable.getCurrentSeasonUsers(currentSeason, currentWeek);
        Map<String, Integer> initialPicks = new HashMap<>();
        for (User user : currentSeasonUsers) {
            int userInitialPicks = sqlConnectorUserTable.getUserInitialPicks(user.getIdUser(), currentSeason);
            initialPicks.put(user.getUsername(), userInitialPicks);
        }
        servletContext.setAttribute("initialPicks", initialPicks);
        servletContext.setAttribute("allSeasonUsers", currentSeasonUsers);
        System.out.println("  Updated currentSeasonUsers size: " + currentSeasonUsers.size());
    }

    public void updateGameData(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updateGameData method started");
        int currentSeason = Integer.parseInt((String) servletContext.getAttribute("season"));
        int currentWeek = Integer.parseInt((String) servletContext.getAttribute("week"));

        List<Game> gamesUpToWeek = sqlConnectorGameTable.getGamesUpToWeek(currentSeason, currentWeek);
        List<Game> gamesForWeek = sqlConnectorGameTable.getGamesForWeek(currentSeason, currentWeek);
        servletContext.setAttribute("gamesUpToWeek", gamesUpToWeek);
        servletContext.setAttribute("gamesForWeek", gamesForWeek);
        System.out.println("  Updated game data, games up to week: " + gamesUpToWeek.size() + ", games for current week: " + gamesForWeek.size());
    }

    public void updatePicksData(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updatePicksData method started");
        int currentSeason = Integer.parseInt((String) servletContext.getAttribute("season"));
        int currentWeek = Integer.parseInt((String) servletContext.getAttribute("week"));

        Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData = 
            sqlConnectorPicksTable.getOptimizedPicksAndGames(currentSeason, currentWeek);
        servletContext.setAttribute("optimizedData", optimizedData);

        @SuppressWarnings("unchecked")
        Map<String, Integer> initialPicks = (Map<String, Integer>) servletContext.getAttribute("initialPicks");
        @SuppressWarnings("unchecked")
        Map<String, String> teamNameToAbbrev = (Map<String, String>) servletContext.getAttribute("teamNameToAbbrev");
        int startWeek = 1;
        
        // Calculate current week results
        PicksCalculationResult result = calculateAllUserLossesAndRemainingPicks(optimizedData, teamNameToAbbrev, 
                startWeek, currentWeek, currentSeason, initialPicks);
        
        // Calculate prior week results
        PicksCalculationResult priorResult = calculateAllUserLossesAndRemainingPicksPriorWeek(optimizedData, 
                teamNameToAbbrev, startWeek, currentWeek, currentSeason, initialPicks);
        
        servletContext.setAttribute("userLosses", result.getUserLosses());
        servletContext.setAttribute("userRemainingPicks", result.getUserRemainingPicks());
        servletContext.setAttribute("userLossesPriorWeek", priorResult.getUserLosses());
        servletContext.setAttribute("userRemainingPicksPriorWeek", priorResult.getUserRemainingPicks());

        System.out.println("  userLosses: " + result.getUserLosses());
        System.out.println("  userRemainingPicks: " + result.getUserRemainingPicks());
        System.out.println("  userLossesPriorWeek: " + priorResult.getUserLosses());
        System.out.println("  userRemainingPicksPriorWeek: " + priorResult.getUserRemainingPicks());
    }
    
    private Map<String, Map<Integer, Integer>> calculateRemainingPicksWeekly(ServletContext servletContext) {
        System.out.println("CommonProcessingService.calculateRemainingPicksWeekly method started");
//        int currentSeason = Integer.parseInt((String) servletContext.getAttribute("season"));
        int currentWeek = Integer.parseInt((String) servletContext.getAttribute("week"));
        @SuppressWarnings("unchecked")
		Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData = 
            (Map<Integer, Map<String, List<Map<String, Object>>>>) servletContext.getAttribute("optimizedData");
        @SuppressWarnings("unchecked")
		Map<String, String> teamNameToAbbrev = (Map<String, String>) servletContext.getAttribute("teamNameToAbbrev");
        @SuppressWarnings("unchecked")
		Map<String, Integer> initialPicks = (Map<String, Integer>) servletContext.getAttribute("initialPicks");

        Map<String, Map<Integer, Integer>> remainingPicksWeekly = new HashMap<>();

        // Initialize remainingPicksWeekly with initial picks for each user
        for (String username : initialPicks.keySet()) {
            Map<Integer, Integer> weeklyPicks = new HashMap<>();
            for (int week = 1; week <= currentWeek; week++) {
                weeklyPicks.put(week, initialPicks.get(username));
            }
            remainingPicksWeekly.put(username, weeklyPicks);
        }

        // Calculate remaining picks for each week
        for (int week = 1; week < currentWeek; week++) {
            Map<String, List<Map<String, Object>>> weekData = optimizedData.get(week);
            if (weekData != null) {
                for (List<Map<String, Object>> gamePicks : weekData.values()) {
                    for (Map<String, Object> pick : gamePicks) {
                        String username = (String) pick.get("username");
                        Boolean isWin = isWinningPick(pick, teamNameToAbbrev);
                        
                        if (isWin != null) {
                            Map<Integer, Integer> userWeeklyPicks = remainingPicksWeekly.get(username);
                            if (userWeeklyPicks != null) {
                                int currentRemaining = userWeeklyPicks.get(week);
                                if (!isWin) {
                                    currentRemaining--;
                                }
                                for (int futureWeek = week + 1; futureWeek <= currentWeek; futureWeek++) {
                                    userWeeklyPicks.put(futureWeek, currentRemaining);
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("CommonProcessingService.calculateRemainingPicksWeekly completed");
        return remainingPicksWeekly;
    }

    public void updateGameWinners(ServletContext servletContext) {
//        System.out.println("CommonProcessingService.updateGameWinners method started");
        @SuppressWarnings("unchecked")
		Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData = 
            (Map<Integer, Map<String, List<Map<String, Object>>>>) servletContext.getAttribute("optimizedData");
        @SuppressWarnings("unchecked")
		Map<String, String> teamNameToAbbrev = (Map<String, String>) servletContext.getAttribute("teamNameToAbbrev");

        Map<Integer, Map<String, String>> gameWinners = calculateGameWinners(optimizedData, teamNameToAbbrev);
        servletContext.setAttribute("gameWinners", gameWinners);
        System.out.println("  Updated game winners");
    }

    // Helper methods
    @SuppressWarnings("unchecked")
    private <T> T getCachedData(ServletContext servletContext, String attributeName) {
        T data = (T) servletContext.getAttribute(attributeName);
        if (data == null) {
            System.out.println("CommonProcessingService: " + attributeName + " is null, updating cache");
            updateCache(servletContext);
            data = (T) servletContext.getAttribute(attributeName);
            
            // If userRemainingPicks is still null after cache update, recalculate it
            if (data == null && "userRemainingPicks".equals(attributeName)) {
                System.out.println("CommonProcessingService: userRemainingPicks is still null, recalculating");
                updatePicksData(servletContext);
                data = (T) servletContext.getAttribute(attributeName);
            }
        }
        return data;
    }

    private void processUserData(User user, List<String> allUsers, Map<String, Integer> userRemainingPicks,
            Map<String, Integer> initialPicks, Map<String, Integer> userLosses,
            ProcessingResult result, ServletContext servletContext) {
        String username = user.getUsername();
        allUsers.add(username);
        
        int userInitialPicks = initialPicks.getOrDefault(username, 0);
        int losses = userLosses.getOrDefault(username, 0);
        
        int remainingPicks = Math.max(0, userInitialPicks - losses);
        userRemainingPicks.put(username, remainingPicks);
        
        String currentSeason = (String) servletContext.getAttribute("season");
        String kothSeason = (String) servletContext.getAttribute("kothSeason");
        // Get prices for matching season number and KOTH season
        PicksPrice prices = sqlConnectorPicksPriceTable.getPickPrices(Integer.parseInt(currentSeason))
            .stream()
            .filter(p -> p.getKothSeason().equals(kothSeason))
            .findFirst()
            .orElse(null);
        
        if (prices != null) {
            BigDecimal totalCost = BigDecimal.ZERO;
            int picks = Math.min(userInitialPicks, prices.getMaxPicks());
            
            for (int i = 1; i <= picks; i++) {
                switch (i) {
                    case 1: totalCost = totalCost.add(prices.getPickPrice1()); break;
                    case 2: totalCost = totalCost.add(prices.getPickPrice2()); break;
                    case 3: totalCost = totalCost.add(prices.getPickPrice3()); break;
                    case 4: totalCost = totalCost.add(prices.getPickPrice4()); break;
                    case 5: totalCost = totalCost.add(prices.getPickPrice5()); break;
                }
            }
            
            result.totalPot += totalCost.intValue();
        }
        
        if (remainingPicks > 0) {
            result.usersWithRemainingPicks++;
            result.totalRemainingPicks += remainingPicks;
        }
    }

    private void setSessionAttributes(HttpSession session, ServletContext servletContext, 
            int season, int week, String userName, Integer userId,
            Boolean isAdmin, Boolean isCommish, int totalPot, int usersWithRemainingPicks,
            int totalRemainingPicks, Map<String, Integer> userRemainingPicks, boolean userHasPaid,
            Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData,
            Map<String, Integer> initialPicks, Map<String, Integer> userLosses,
            Map<String, String> teamNameToAbbrev, List<String> allUsers,
            List<Game> games, List<Game> gamesForWeek,
            Map<String, Map<Integer, Integer>> remainingPicksWeekly) {
        
        session.setAttribute("season", String.valueOf(season));
        session.setAttribute("currentWeek", String.valueOf(week));
        session.setAttribute("userName", userName);
        session.setAttribute("userId", userId);
        session.setAttribute("isAdmin", isAdmin);
        session.setAttribute("isCommish", isCommish);
        session.setAttribute("totalPot", totalPot);
        session.setAttribute("usersWithRemainingPicks", usersWithRemainingPicks);
        session.setAttribute("totalRemainingPicks", totalRemainingPicks);
        session.setAttribute("userRemainingPicks", userRemainingPicks);
        session.setAttribute("userHasPaid", userHasPaid);
        session.setAttribute("optimizedData", optimizedData);
        session.setAttribute("initialPicks", initialPicks);
        session.setAttribute("userLosses", userLosses);
        session.setAttribute("teamNameToAbbrev", teamNameToAbbrev);
        session.setAttribute("allUsers", allUsers);
        session.setAttribute("games", games);
        session.setAttribute("gamesForWeek", gamesForWeek);
        session.setAttribute("remainingPicksWeekly", remainingPicksWeekly);
        
        // Add prior week attributes
        @SuppressWarnings("unchecked")
        Map<String, Integer> userLossesPriorWeek = 
            (Map<String, Integer>) servletContext.getAttribute("userLossesPriorWeek");
        session.setAttribute("userLossesPriorWeek", userLossesPriorWeek);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicksPriorWeek = 
            (Map<String, Integer>) servletContext.getAttribute("userRemainingPicksPriorWeek");
        session.setAttribute("userRemainingPicksPriorWeek", userRemainingPicksPriorWeek);
    }

    private boolean isTeamMatch(String selectedTeam, String teamName, Map<String, String> teamNameToAbbrev) {
//        System.out.println("CommonProcessingService.isTeamMatch method started");
        if (selectedTeam == null || teamName == null) {
            System.out.println("  WARNING: selectedTeam or teamName is null. selectedTeam: '" + selectedTeam + "', teamName: '" + teamName + "'");
            return false;
        }

        if (selectedTeam.equalsIgnoreCase(teamName)) {
            return true;
        }

        String selectedAbbrev = teamNameToAbbrev.get(selectedTeam);
        String teamAbbrev = teamNameToAbbrev.get(teamName);

        if (selectedAbbrev != null && teamAbbrev != null) {
            if (selectedAbbrev.equalsIgnoreCase(teamAbbrev)) {
                return true;
            }
        } else {
            System.out.println("  WARNING: Unable to find abbreviation for selectedTeam: '" + selectedTeam + "' or teamName: '" + teamName + "'");
        }

        if (teamAbbrev != null && teamAbbrev.equalsIgnoreCase(selectedTeam)) {
            return true;
        }

        return teamName.toLowerCase().contains(selectedTeam.toLowerCase()) ||
               selectedTeam.toLowerCase().contains(teamName.toLowerCase());
    }
    
    private Map<Integer, Map<String, String>> calculateGameWinners(
            Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData,
            Map<String, String> teamNameToAbbrev) {
        Map<Integer, Map<String, String>> gameWinners = new HashMap<>();

        for (Map.Entry<Integer, Map<String, List<Map<String, Object>>>> weekEntry : optimizedData.entrySet()) {
//            System.out.println("CommonProcessingService.calculateGameWinners 1 method started");
            int week = weekEntry.getKey();
            Map<String, String> weekWinners = new HashMap<>();

            for (Map.Entry<String, List<Map<String, Object>>> gameEntry : weekEntry.getValue().entrySet()) {
//                System.out.println("CommonProcessingService.calculateGameWinners 2 method started");
                String gameId = gameEntry.getKey();
                List<Map<String, Object>> gamePicks = gameEntry.getValue();

                if (!gamePicks.isEmpty()) {
                    Map<String, Object> firstPick = gamePicks.get(0);
                    String status = (String) firstPick.get("status");

                    if ("Final".equals(status) || "F/OT".equals(status)) {
                        int homeScore = (int) firstPick.get("homeScore");
                        int awayScore = (int) firstPick.get("awayScore");
                        String homeTeamName = (String) firstPick.get("homeTeamName");
                        String awayTeamName = (String) firstPick.get("awayTeamName");

                        String winner = (homeScore > awayScore) ? homeTeamName : awayTeamName;
                        weekWinners.put(gameId, winner);
                    }
                }
            }

            gameWinners.put(week, weekWinners);
        }

        return gameWinners;
    }

    // Inner class for PicksCalculationResult
    public class PicksCalculationResult {
        private final Map<String, Integer> userLosses;
        private final Map<String, Integer> userRemainingPicks;

        public PicksCalculationResult(Map<String, Integer> userLosses, Map<String, Integer> userRemainingPicks) {
//            System.out.println("CommonProcessingService.PicksCalculationResult method started");
            this.userLosses = userLosses;
            this.userRemainingPicks = userRemainingPicks;
        }

        public Map<String, Integer> getUserLosses() {
            return userLosses;
        }

        public Map<String, Integer> getUserRemainingPicks() {
            return userRemainingPicks;
        }
    }

    // Method to update cache (previously in CacheUpdate)
    private void updateCache(ServletContext servletContext) {
        System.out.println("CommonProcessingService.updateCache method started");
        long startTime = System.currentTimeMillis();

        try {
            updateSeasonAndWeek(servletContext);
            updateTeamData(servletContext);
            updateUserData(servletContext);
            updateGameData(servletContext);
            updatePicksData(servletContext);
            updateGameWinners(servletContext);

            long endTime = System.currentTimeMillis();
            System.out.println("CommonProcessingService.updateCache method completed in " + (endTime - startTime) + " ms");
        } catch (Exception e) {
            System.err.println("CommonProcessingService.updateCache method - Error during cache update: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private PicksCalculationResult calculateAllUserLossesAndRemainingPicks(
            Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData, 
            Map<String, String> teamNameToAbbrev, 
            int startWeek, 
            int currentWeek, 
            int season, 
            Map<String, Integer> initialPicks) {
        System.out.println("CommonProcessingService.calculateAllUserLossesAndRemainingPicks method started");
        System.out.println("  Initial picks: " + initialPicks);
        System.out.println("  Calculating from week " + startWeek + " to week " + currentWeek);
        
        Map<String, Integer> userLosses = new HashMap<>();
        Map<String, Integer> remainingPicks = new HashMap<>(initialPicks);
        
        // Initialize losses and remaining picks for all users
        for (String username : initialPicks.keySet()) {
            userLosses.put(username, 0);
            remainingPicks.put(username, initialPicks.get(username));
        }

        // Process each week's picks
        for (int week = startWeek; week <= currentWeek; week++) {
            Map<String, List<Map<String, Object>>> weekData = optimizedData.get(week);
//            System.out.println("\nProcessing week " + week);
            
            if (weekData != null && !weekData.isEmpty()) {
//                System.out.println("  Found " + weekData.size() + " games for week " + week);
                
                // Process each game's picks
                for (Map.Entry<String, List<Map<String, Object>>> gameEntry : weekData.entrySet()) {
                    String gameId = gameEntry.getKey();
                    List<Map<String, Object>> gamePicks = gameEntry.getValue();
                    
//                    System.out.println("  Game " + gameId + ": " + gamePicks.size() + " picks");
                    
                    // Process each pick for this game
                    for (Map<String, Object> pick : gamePicks) {
                        String username = (String) pick.get("username");
                        String selectedTeam = (String) pick.get("selectedTeam");
                        String status = (String) pick.get("status");
                        
                        if (username == null || selectedTeam == null) {
                            continue;
                        }

                        // Calculate pick result
                        Boolean isWin = isWinningPick(pick, teamNameToAbbrev);
                        
//                        System.out.println("    User: " + username + 
//                                         ", Team: " + selectedTeam + 
//                                         ", Status: " + status + 
//                                         ", Result: " + (isWin == null ? "Pending" : (isWin ? "Win" : "Loss")));
//                        
                        // Update losses and remaining picks if game is final
                        if (isWin != null && !isWin) {
                            int currentLosses = userLosses.getOrDefault(username, 0);
                            int currentRemaining = remainingPicks.getOrDefault(username, 0);
                            
                            userLosses.put(username, currentLosses + 1);
                            remainingPicks.put(username, Math.max(0, currentRemaining - 1));
                            
                            System.out.println("      Updated " + username + " - Losses: " + (currentLosses + 1) + 
                                             ", Remaining: " + Math.max(0, currentRemaining - 1));
                        }
                    }
                }
            } else {
                System.out.println("  No data found for week " + week);
            }
        }

        // Check if there are any scheduled games remaining
        List<Game> games = sqlConnectorGameTable.fetchGamesFromDatabase(season, currentWeek);
        System.out.println("\nFetched " + (games != null ? games.size() : 0) + " games for current week");
        
        boolean hasScheduledGames = games != null && games.stream()
                .anyMatch(game -> "STATUS_SCHEDULED".equalsIgnoreCase(game.getStatus()));
        System.out.println("Has scheduled games: " + hasScheduledGames);

        // Validate final results
        for (String username : initialPicks.keySet()) {
            int initial = initialPicks.get(username);
            int losses = userLosses.get(username);
            int remaining = remainingPicks.get(username);
            
//            System.out.println("\nFinal results for " + username + ":");
//            System.out.println("  Initial picks: " + initial);
//            System.out.println("  Losses: " + losses);
//            System.out.println("  Remaining: " + remaining);
            
            // Verify calculations
            if (losses + remaining != initial) {
                System.out.println("  WARNING: Calculation mismatch for " + username + 
                                 " - Initial: " + initial + 
                                 ", Losses: " + losses + 
                                 ", Remaining: " + remaining);
            }
        }

        System.out.println("\nFinal tallies:");
        System.out.println("User losses: " + userLosses);
        System.out.println("Remaining picks: " + remainingPicks);

        return new PicksCalculationResult(userLosses, remainingPicks);
    }

    private Boolean isWinningPick(Map<String, Object> pick, Map<String, String> teamNameToAbbrev) {
        String status = (String) pick.get("status");
//        System.out.println("  Status: " + status);  // Add logging
        
        if ("Final".equals(status) || "F/OT".equals(status) || "STATUS_FINAL".equals(status)) {
            int homeScore = safeCastToInteger(pick.get("homeScore"));
            int awayScore = safeCastToInteger(pick.get("awayScore"));
            String selectedTeam = (String) pick.get("selectedTeam");
            String homeTeamName = (String) pick.get("homeTeamName");
            String awayTeamName = (String) pick.get("awayTeamName");

//            System.out.println("  Pick details:"); // Add logging
//            System.out.println("    Selected: " + selectedTeam);
//            System.out.println("    Home: " + homeTeamName + " (" + homeScore + ")");
//            System.out.println("    Away: " + awayTeamName + " (" + awayScore + ")");

            if (selectedTeam != null && homeTeamName != null && awayTeamName != null) {
                boolean isHomeTeam = isTeamMatch(selectedTeam, homeTeamName, teamNameToAbbrev);
                boolean isAwayTeam = isTeamMatch(selectedTeam, awayTeamName, teamNameToAbbrev);

//                System.out.println("  Is Home Team? " + isHomeTeam);
//                System.out.println("  Is Away Team? " + isAwayTeam);

                if (homeScore != awayScore) {  // Ensure there's a winner
                    boolean isWin = (homeScore > awayScore && isHomeTeam) || 
                                  (awayScore > homeScore && isAwayTeam);
//                    System.out.println("  Result: " + (isWin ? "WIN" : "LOSS"));
                    return isWin;
                }
            }
        }
        return null; // Game not final or no clear winner yet
    }

    private int safeCastToInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Double) {
            return ((Double) value).intValue();
        } else if (value instanceof Long) {
            return ((Long) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                System.out.println("Error parsing integer: " + e.getMessage());
                return 0;
            }
        }
        System.out.println("Unexpected type for integer: " + value.getClass().getName());
        return 0;
    }
    private PicksCalculationResult calculateAllUserLossesAndRemainingPicksPriorWeek(
            Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData, 
            Map<String, String> teamNameToAbbrev, 
            int startWeek, 
            int currentWeek, 
            int season, 
            Map<String, Integer> initialPicks) {
        System.out.println("CommonProcessingService.calculateAllUserLossesAndRemainingPicksPriorWeek method started");
        System.out.println("  Initial picks: " + initialPicks);
        System.out.println("  Calculating from week " + startWeek + " to week " + (currentWeek - 1));
        
        Map<String, Integer> userLosses = new HashMap<>();
        Map<String, Integer> remainingPicks = new HashMap<>(initialPicks);
        
        // Initialize losses and remaining picks for all users
        for (String username : initialPicks.keySet()) {
            userLosses.put(username, 0);
            remainingPicks.put(username, initialPicks.get(username));
        }

        // Only calculate up to the previous week
        int endWeek = Math.max(startWeek, currentWeek - 1);

        // Process each week's picks
        for (int week = startWeek; week <= endWeek; week++) {
            Map<String, List<Map<String, Object>>> weekData = optimizedData.get(week);
//            System.out.println("\nProcessing week " + week);
            
            if (weekData != null && !weekData.isEmpty()) {
//                System.out.println("  Found " + weekData.size() + " games for week " + week);
                
                // Process each game's picks
                for (Map.Entry<String, List<Map<String, Object>>> gameEntry : weekData.entrySet()) {
                    String gameId = gameEntry.getKey();
                    List<Map<String, Object>> gamePicks = gameEntry.getValue();
                    
//                    System.out.println("  Game " + gameId + ": " + gamePicks.size() + " picks");
                    
                    // Process each pick for this game
                    for (Map<String, Object> pick : gamePicks) {
                        String username = (String) pick.get("username");
                        String selectedTeam = (String) pick.get("selectedTeam");
                        String status = (String) pick.get("status");
                        
                        if (username == null || selectedTeam == null) {
                            continue;
                        }

                        // Calculate pick result
                        Boolean isWin = isWinningPick(pick, teamNameToAbbrev);
                        
//                        System.out.println("    User: " + username + 
//                                         ", Team: " + selectedTeam + 
//                                         ", Status: " + status + 
//                                         ", Result: " + (isWin == null ? "Pending" : (isWin ? "Win" : "Loss")));
//                        
                        // Update losses and remaining picks if game is final
                        if (isWin != null && !isWin) {
                            int currentLosses = userLosses.getOrDefault(username, 0);
                            int currentRemaining = remainingPicks.getOrDefault(username, 0);
                            
                            userLosses.put(username, currentLosses + 1);
                            remainingPicks.put(username, Math.max(0, currentRemaining - 1));
                            
                            System.out.println("      Updated " + username + " - Losses: " + (currentLosses + 1) + 
                                             ", Remaining: " + Math.max(0, currentRemaining - 1));
                        }
                    }
                }
            } else {
                System.out.println("  No data found for week " + week);
            }
        }

        // Validate final results
        for (String username : initialPicks.keySet()) {
            int initial = initialPicks.get(username);
            int losses = userLosses.get(username);
            int remaining = remainingPicks.get(username);
            
            System.out.println("\nFinal results for " + username + ":");
            System.out.println("  Initial picks: " + initial);
            System.out.println("  Losses: " + losses);
            System.out.println("  Remaining: " + remaining);
            
            // Verify calculations
            if (losses + remaining != initial) {
                System.out.println("  WARNING: Calculation mismatch for " + username + 
                                 " - Initial: " + initial + 
                                 ", Losses: " + losses + 
                                 ", Remaining: " + remaining);
            }
        }

        System.out.println("\nFinal tallies for prior week:");
        System.out.println("User losses: " + userLosses);
        System.out.println("Remaining picks: " + remainingPicks);

        return new PicksCalculationResult(userLosses, remainingPicks);
    }
    public void ensureSessionData(HttpSession session, ServletContext servletContext) {
        System.out.println("CommonProcessingService.ensureSessionData() called");

        updateSeasonAndWeek(servletContext);
        updateTeamData(servletContext);
        updateUserData(servletContext);
        updatePicksData(servletContext);
        updateGameData(servletContext);
        updateGameWinners(servletContext);

        @SuppressWarnings("unchecked")
        Map<String, String> teamNameToAbbrev = (Map<String, String>) servletContext.getAttribute("teamNameToAbbrev");
        if (teamNameToAbbrev == null) {
            System.out.println("WARNING: teamNameToAbbrev is null after update. Initializing empty map.");
            teamNameToAbbrev = new HashMap<>();
            servletContext.setAttribute("teamNameToAbbrev", teamNameToAbbrev);
        }

        @SuppressWarnings("unchecked")
        List<User> allSeasonUsers = (List<User>) servletContext.getAttribute("allSeasonUsers");
        @SuppressWarnings("unchecked")
        Map<String, Integer> initialPicks = (Map<String, Integer>) servletContext.getAttribute("initialPicks");
        @SuppressWarnings("unchecked")
        Map<String, Integer> userRemainingPicks = (Map<String, Integer>) servletContext.getAttribute("userRemainingPicks");

        if (allSeasonUsers == null || initialPicks == null || userRemainingPicks == null) {
            throw new IllegalStateException("Critical session data missing after refresh.");
        }

        session.setAttribute("allUsers", allSeasonUsers.stream().map(User::getUsername).toList());
        session.setAttribute("initialPicks", initialPicks);
        session.setAttribute("userRemainingPicks", userRemainingPicks);
        session.setAttribute("teamNameToAbbrev", teamNameToAbbrev); // ✅ Ensure session always has this
    }
}