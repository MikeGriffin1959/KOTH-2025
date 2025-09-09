package helpers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import services.NFLSeasonCalculator;

import model.Game;
import model.Team;

@Service
public class ApiParsers {
    
    public static List<Game> ParseESPNAPI(String apiResponse) {
        System.out.println("ApiParsers.ParseESPNAPI method started");
        List<Game> gamesList = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(apiResponse);
        JSONArray eventsArray = jsonResponse.getJSONArray("events");
        
        NFLSeasonCalculator calculator = new NFLSeasonCalculator();
        int gamesSkipped = 0;

        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject eventObject = eventsArray.getJSONObject(i);
            
            try {
                // Extract basic game data first
                String gameDate = eventObject.getString("date");
                JSONObject seasonObject = eventObject.getJSONObject("season");
                int gameSeason = seasonObject.getInt("year");
                JSONObject weekObject = eventObject.getJSONObject("week");
                int gameWeek = weekObject.getInt("number");
                
                // Validate the game date against expected NFL week
                if (!isValidGameDate(gameDate, gameSeason, gameWeek, calculator)) {
                    System.out.println("Skipping invalid game: Season " + gameSeason + 
                                     ", Week " + gameWeek + ", Date " + gameDate);
                    gamesSkipped++;
                    continue;
                }
                
                // If validation passes, create the game object
                Game game = new Game();
                game.setGameID(Integer.parseInt(eventObject.getString("id")));
                game.setDate(gameDate);
                game.setSeason(gameSeason);
                game.setWeek(gameWeek);

                JSONArray competitionsArray = eventObject.getJSONArray("competitions");
                if (competitionsArray.length() > 0) {
                    JSONObject competitionObject = competitionsArray.getJSONObject(0);
                    
                    JSONObject statusObject = competitionObject.getJSONObject("status");
                    game.setStatus(statusObject.getJSONObject("type").getString("name"));

                    JSONArray competitorsArray = competitionObject.getJSONArray("competitors");
                    for (int j = 0; j < competitorsArray.length(); j++) {
                        JSONObject competitorObject = competitorsArray.getJSONObject(j);
                        String homeAway = competitorObject.getString("homeAway");
                        JSONObject teamObject = competitorObject.getJSONObject("team");

                        if (homeAway.equals("home")) {
                            game.setHomeTeamId(teamObject.getInt("id"));
                            game.setHomeScore(competitorObject.getInt("score"));
                            game.setHomeTeamName(teamObject.getString("abbreviation"));
                        } else {
                            game.setAwayTeamId(teamObject.getInt("id"));
                            game.setAwayScore(competitorObject.getInt("score"));
                            game.setAwayTeamName(teamObject.getString("abbreviation"));
                        }
                    }
                }
                gamesList.add(game);
                
            } catch (Exception e) {
                System.err.println("Error parsing game at index " + i + ": " + e.getMessage());
                gamesSkipped++;
                continue;
            }
        }
        
        System.out.println("ApiParsers.ParseESPNAPI completed. Valid games: " + gamesList.size() + 
                          ", Skipped games: " + gamesSkipped);
        return gamesList;
    }
    
    /**
     * Validates if a game date aligns with the expected NFL week timeframe
     */
    private static boolean isValidGameDate(String gameDate, int gameSeason, int gameWeek, NFLSeasonCalculator calculator) {
        try {
            // Parse the game date (ESPN format: "2024-09-10T00:30Z")
            LocalDateTime gameDateTime = LocalDateTime.parse(gameDate, DateTimeFormatter.ISO_DATE_TIME);
            
            // Calculate expected date range for this NFL week
            LocalDateTime expectedWeekStart = getExpectedWeekStart(gameSeason, gameWeek, calculator);
            LocalDateTime expectedWeekEnd = expectedWeekStart.plusDays(7); // One week window
            
            // Allow some flexibility (±2 days) for edge cases
            LocalDateTime validStart = expectedWeekStart.minusDays(2);
            LocalDateTime validEnd = expectedWeekEnd.plusDays(2);
            
            boolean isValid = !gameDateTime.isBefore(validStart) && !gameDateTime.isAfter(validEnd);
            
            if (!isValid) {
                System.out.println("Date validation failed for Season " + gameSeason + ", Week " + gameWeek + 
                                 ": Game date " + gameDate + " outside expected range " + 
                                 validStart.toLocalDate() + " to " + validEnd.toLocalDate());
            }
            
            return isValid;
            
        } catch (Exception e) {
            System.err.println("Error validating game date: " + gameDate + " - " + e.getMessage());
            return false; // If we can't parse the date, reject the game
        }
    }
    
    /**
     * Calculate the expected start date for a given NFL week
     */
    private static LocalDateTime getExpectedWeekStart(int season, int week, NFLSeasonCalculator calculator) {
        // Get the season start date (this should match your NFLSeasonCalculator logic)
        LocalDateTime seasonStart = getSeasonStartDateTime(season);
        
        // Calculate weeks since season start
        // Week 1 starts at seasonStart, Week 2 starts 7 days later, etc.
        return seasonStart.plusWeeks(week - 1);
    }
    
    /**
     * Get season start date/time (matches NFLSeasonCalculator logic exactly)
     */
    private static LocalDateTime getSeasonStartDateTime(int year) {
        // Match the exact logic from NFLSeasonCalculator
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        LocalDate seasonStartDate = laborDay.plusDays(2); // Wednesday after Labor Day
        LocalDateTime seasonStartDateTime = seasonStartDate.atTime(6, 0); // 6 AM on season start date
        
        // Adjust to previous Tuesday 6 AM
        while (seasonStartDateTime.getDayOfWeek() != DayOfWeek.TUESDAY) {
            seasonStartDateTime = seasonStartDateTime.minusDays(1);
        }
        return seasonStartDateTime;
    }

    public static List<Game> ParseESPNAPIMinimal(String apiResponse, int currentSeason, int currentWeek) {
        System.out.println("ApiParsers.ParseESPNAPIMinimal method started");
        List<Game> games = new ArrayList<>();
        NFLSeasonCalculator calculator = new NFLSeasonCalculator();
        
        try {
            JSONObject jsonObject = new JSONObject(apiResponse);
            JSONArray events = jsonObject.getJSONArray("events");

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);

                // Extract season and week
                int seasonYear = event.getJSONObject("season").getInt("year");
                int weekNumber = event.getJSONObject("week").getInt("number");

                // Filter out games from other seasons or weeks
                if (seasonYear != currentSeason || weekNumber != currentWeek) {
                    System.out.println("Skipping game from season " + seasonYear + ", week " + weekNumber);
                    continue;
                }
                
                // Add date validation for minimal parsing too
                String gameDate = event.getString("date");
                if (!isValidGameDate(gameDate, seasonYear, weekNumber, calculator)) {
                    System.out.println("Skipping invalid game in minimal parsing: Season " + seasonYear + 
                                     ", Week " + weekNumber + ", Date " + gameDate);
                    continue;
                }

                JSONObject competition = event.getJSONArray("competitions").getJSONObject(0);
                JSONArray competitors = competition.getJSONArray("competitors");
                JSONObject status = competition.getJSONObject("status");

                Game game = new Game();
                game.setGameID(Long.parseLong(event.getString("id")));
                game.setSeason(seasonYear);
                game.setWeek(weekNumber);
                game.setDate(gameDate);

                game.setStatus(status.getJSONObject("type").getString("name"));
                if (status.has("displayClock")) {
                    game.setDisplayClock(status.getString("displayClock"));
                }
                if (status.has("period")) {
                    game.setPeriod(String.valueOf(status.getInt("period")));
                }

                for (int j = 0; j < competitors.length(); j++) {
                    JSONObject competitor = competitors.getJSONObject(j);
                    String homeAway = competitor.getString("homeAway");
                    int score = competitor.getInt("score");
                    JSONObject teamObj = competitor.getJSONObject("team");

                    int teamId = teamObj.getInt("id");
                    String teamName = teamObj.getString("name");
                    String location = teamObj.getString("location");
                    String fullName = location + " " + teamName;

                    if ("home".equals(homeAway)) {
                        game.setHomeTeamId(teamId);
                        game.setHomeScore(score);
                        game.setHomeTeamName(fullName);
                    } else {
                        game.setAwayTeamId(teamId);
                        game.setAwayScore(score);
                        game.setAwayTeamName(fullName);
                    }
                }

                System.out.println("\nParsed valid game " + game.getGameID() + ":");
                System.out.println("Season: " + game.getSeason() + ", Week: " + game.getWeek());
                System.out.println("Teams: " + game.getAwayTeamName() + " @ " + game.getHomeTeamName());
                System.out.println("Score: " + game.getAwayScore() + "-" + game.getHomeScore());

                games.add(game);
            }
        } catch (Exception e) {
            System.err.println("Error parsing ESPN API response: " + e.getMessage());
            e.printStackTrace();
        }
        return games;
    }

    
    public static List<Team> ParseESPNTeams(String apiResponse) {
        List<Team> teamsList = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(apiResponse);
        JSONArray sportsArray = jsonResponse.getJSONArray("sports");

        if (sportsArray.length() > 0) {
            JSONObject sportObject = sportsArray.getJSONObject(0);
            JSONArray leaguesArray = sportObject.getJSONArray("leagues");

            if (leaguesArray.length() > 0) {
                JSONObject leagueObject = leaguesArray.getJSONObject(0);
                JSONArray teamsArray = leagueObject.getJSONArray("teams");

                for (int i = 0; i < teamsArray.length(); i++) {
                    JSONObject teamObject = teamsArray.getJSONObject(i);
                    JSONObject teamData = teamObject.getJSONObject("team");

                    Team team = new Team();
                    team.setApiTeamID(teamData.getInt("id"));
                    team.setApiTeamName(teamData.getString("shortDisplayName"));
                    team.setApiTeamShortName(teamData.getString("abbreviation"));
                    team.setApiTeamFullName(teamData.getString("displayName"));

                    teamsList.add(team);
                }
            }
        }

        return teamsList;
    }
    
    public static Game ParseESPNOdds(String apiResponse, Game game) {
        System.out.println("ApiParsers.ParseESPNOdds method started for GameID: " + game.getGameID());
        try {
            JSONObject jsonResponse = new JSONObject(apiResponse);
            JSONArray itemsArray = jsonResponse.getJSONArray("items");
            
            if (itemsArray.length() > 0) {
                JSONObject oddsObject = itemsArray.getJSONObject(0);
                
                Double overUnder = oddsObject.has("overUnder") ? oddsObject.getDouble("overUnder") : null;
                Double spread = oddsObject.has("spread") ? oddsObject.getDouble("spread") : null;
                
                System.out.println("Parsed odds data for GameID " + game.getGameID() + 
                                 " - Over/Under: " + overUnder + 
                                 ", Spread: " + spread);
                
                game.setOverUnder(overUnder);
                game.setPointSpread(spread);
            } else {
                System.out.println("No odds items found in response for GameID: " + game.getGameID());
            }
        } catch (Exception e) {
            System.err.println("Error parsing odds for GameID " + game.getGameID() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return game;
    }
}





