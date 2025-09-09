package controllers;

import helpers.ApiFetchers;
import helpers.SqlConnectorGameTable;
import model.Game;
import services.NFLGameFetcherService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GamesApiController {

    private final SqlConnectorGameTable sqlConnectorGameTable;
    private final NFLGameFetcherService nflGameFetcherService;

    private static final String STATUS_FINAL = "Final";
    private static final String STATUS_SCHEDULED = "Scheduled";
    private static final String STATUS_IN_PROGRESS = "In Progress";

    @Autowired
    public GamesApiController(SqlConnectorGameTable sqlConnectorGameTable,
                             NFLGameFetcherService nflGameFetcherService) {
        this.sqlConnectorGameTable = sqlConnectorGameTable;
        this.nflGameFetcherService = nflGameFetcherService;
        System.out.println("GamesApiController constructor called");
    }

    @PostConstruct
    public void init() {
        System.out.println("=== GamesApiController has been initialized and loaded by Spring! ===");
        System.out.println("API endpoint available at: /api/games/refresh");
    }

    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshGames(
            @RequestParam String season,
            @RequestParam String week) {
        
        System.out.println("=== API ENDPOINT HIT: /api/games/refresh ===");
        System.out.println("Parameters - season: " + season + ", week: " + week);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int seasonInt = Integer.parseInt(season);
            int weekInt = Integer.parseInt(week);
            
            System.out.println("API: Refreshing games for season " + seasonInt + ", week " + weekInt);
            
            // Fetch fresh data from ESPN and update database
            System.out.println("API: Fetching games from NFL service...");
            List<Game> espnGames = nflGameFetcherService.fetchCurrentWeekGames();
            System.out.println("API: Fetched " + (espnGames != null ? espnGames.size() : 0) + " games from ESPN");
            
            System.out.println("API: Updating database with fresh game data...");
            sqlConnectorGameTable.updateGameTableMinimal(espnGames);
            System.out.println("API: Database updated successfully");
            
            // Get updated games from database
            System.out.println("API: Retrieving updated games from database...");
            List<Game> games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            System.out.println("API: Retrieved " + (games != null ? games.size() : 0) + " games from database");
            
            // Update odds for scheduled games (optional - can be commented out for faster response)
            updateOddsForScheduledGames(games);
            
            // Re-fetch games after odds update
            games = sqlConnectorGameTable.getGamesForWeek(seasonInt, weekInt);
            
            // Process game status and convert to Eastern time
            processGameStatus(games);
            
            // Convert games to simplified format for JSON response
            List<Map<String, Object>> gameData = new ArrayList<>();
            for (Game game : games) {
                Map<String, Object> gameMap = new HashMap<>();
                gameMap.put("gameID", game.getGameID());
                gameMap.put("awayScore", game.getAwayScore());
                gameMap.put("homeScore", game.getHomeScore());
                gameMap.put("status", game.getStatus());
                gameMap.put("displayClock", game.getDisplayClock());
                gameMap.put("period", game.getPeriod());
                gameMap.put("pointSpread", game.getPointSpread());
                gameMap.put("overUnder", game.getOverUnder());
                gameData.add(gameMap);
                
                System.out.println("API: Game " + game.getGameID() + " - Status: " + game.getStatus() + 
                                 ", Away: " + game.getAwayScore() + ", Home: " + game.getHomeScore());
            }
            
            response.put("success", true);
            response.put("games", gameData);
            response.put("message", "Games refreshed successfully");
            response.put("gameCount", gameData.size());
            
            System.out.println("API: Successfully refreshed " + games.size() + " games");
            System.out.println("=== API RESPONSE SUCCESS ===");
            
        } catch (NumberFormatException e) {
            System.err.println("API: Invalid season/week parameters: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Invalid season or week parameter");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            System.err.println("API: Error refreshing games: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error refreshing games: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        System.out.println("=== TEST ENDPOINT HIT: /api/games/test ===");
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "GamesApiController is working!");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    private void updateOddsForScheduledGames(List<Game> games) {
        System.out.println("API: Updating odds for scheduled games...");
        int oddsUpdated = 0;
        
        for (Game game : games) {
            if ("STATUS_SCHEDULED".equals(game.getStatus()) || "Scheduled".equals(game.getStatus())) {
                try {
                    String gameId = String.valueOf(game.getGameID());
                    System.out.println("API: Fetching odds for game " + gameId);
                    String oddsResponse = ApiFetchers.FetchESPNGameOdds(gameId);
                    
                    if (oddsResponse != null && !oddsResponse.isEmpty()) {
                        // You'll need to import your ApiParsers class here
                        // Game updatedGame = ApiParsers.ParseESPNOdds(oddsResponse, game);
                        // sqlConnectorGameTable.updateGameOdds(updatedGame);
                        // oddsUpdated++;
                        System.out.println("API: Odds response received for game " + gameId);
                    } else {
                        System.out.println("API: No odds data available for game " + gameId);
                    }
                } catch (Exception e) {
                    System.err.println("API: Error updating odds for GameID " + game.getGameID() + ": " + e.getMessage());
                }
            }
        }
        
        System.out.println("API: Updated odds for " + oddsUpdated + " games");
    }

    private void processGameStatus(List<Game> games) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        System.out.println("API: Processing game status and time zones...");
        
        for (Game game : games) {
            try {
                // Convert UTC time to Eastern
                String utcDate = game.getDate();
                if (utcDate != null && !utcDate.isEmpty()) {
                    LocalDateTime utcDateTime = LocalDateTime.parse(utcDate.replace("Z", ""), formatter);
                    ZonedDateTime easternZoned = utcDateTime.atZone(ZoneId.of("UTC"))
                            .withZoneSameInstant(ZoneId.of("America/New_York"));
                    game.setDate(easternZoned.toString());
                }

                // Convert database status to display status
                String dbStatus = game.getStatus();
                String convertedStatus = convertStatus(dbStatus);
                game.setStatus(convertedStatus);

            } catch (Exception e) {
                System.err.println("API: Error processing game " + game.getGameID() + ": " + e.getMessage());
            }
        }
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
}