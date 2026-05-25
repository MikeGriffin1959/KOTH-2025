package helpers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import helpers.ApiFetchers.NFLSeasonType;
import model.Game;
import model.Team;
import services.NFLSeasonCalculator;

@Service
public class ApiParsers {
    
    public static List<Game> ParseESPNAPI(String apiResponse, NFLSeasonType seasonType) {
        System.out.println("ApiParsers.ParseESPNAPI method started");
        List<Game> gamesList = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(apiResponse);
        JSONArray eventsArray = jsonResponse.getJSONArray("events");
        
        NFLSeasonCalculator calculator = new NFLSeasonCalculator();
        int gamesSkipped = 0;

        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject eventObject = eventsArray.getJSONObject(i);
            
            try {
                String gameDate = eventObject.getString("date");
                JSONObject seasonObject = eventObject.getJSONObject("season");
                int gameSeason = seasonObject.getInt("year");
                JSONObject weekObject = eventObject.getJSONObject("week");
                int gameWeek = weekObject.getInt("number");
                
                if (!isValidGameDate(gameDate, gameSeason, gameWeek, seasonType, calculator)) {
                    System.out.println("Skipping invalid game: Season " + gameSeason + 
                                     ", Week " + gameWeek + ", Date " + gameDate);
                    gamesSkipped++;
                    continue;
                }
                
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
    private static boolean isValidGameDate(String gameDate, int gameSeason, int gameWeek, 
                                           NFLSeasonType seasonType, NFLSeasonCalculator calculator) {
        try {
            LocalDateTime gameDateTime = LocalDateTime.parse(gameDate, DateTimeFormatter.ISO_DATE_TIME);
            
            LocalDateTime expectedWeekStart;
            if (seasonType == NFLSeasonType.PLAYOFFS) {
                // Playoffs occur in January of the year AFTER the season
                // Wild Card (week 1) typically starts around Jan 10-12
                LocalDateTime playoffStart = LocalDateTime.of(gameSeason + 1, Month.JANUARY, 8, 0, 0);
                expectedWeekStart = playoffStart.plusWeeks(gameWeek - 1);
            } else {
                expectedWeekStart = getExpectedWeekStart(gameSeason, gameWeek, calculator);
            }
            
            LocalDateTime expectedWeekEnd = expectedWeekStart.plusDays(7);
            
            // Allow flexibility (±3 days) for edge cases
            LocalDateTime validStart = expectedWeekStart.minusDays(3);
            LocalDateTime validEnd = expectedWeekEnd.plusDays(3);
            
            boolean isValid = !gameDateTime.isBefore(validStart) && !gameDateTime.isAfter(validEnd);
            
            if (!isValid) {
                System.out.println("Date validation failed for Season " + gameSeason + ", Week " + gameWeek + 
                                 " (" + seasonType + "): Game date " + gameDate + " outside expected range " + 
                                 validStart.toLocalDate() + " to " + validEnd.toLocalDate());
            }
            
            return isValid;
            
        } catch (Exception e) {
            System.err.println("Error validating game date: " + gameDate + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Calculate the expected start date for a given NFL regular season week
     */
    private static LocalDateTime getExpectedWeekStart(int season, int week, NFLSeasonCalculator calculator) {
        LocalDateTime seasonStart = getSeasonStartDateTime(season);
        return seasonStart.plusWeeks(week - 1);
    }
    
    /**
     * Get season start date/time (matches NFLSeasonCalculator logic exactly)
     */
    private static LocalDateTime getSeasonStartDateTime(int year) {
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

    /**
     * Parse ESPN API response with minimal data extraction.
     * 
     * @param apiResponse The raw JSON response from ESPN API
     * @param currentSeason The NFL season year
     * @param espnWeek The ESPN week number (used for filtering API response)
     * @param internalWeek Your internal week number (used for storage - e.g., 19 for Wild Card)
     * @param seasonType REGULAR_SEASON or PLAYOFFS
     * @return List of parsed Game objects
     */
    public static List<Game> ParseESPNAPIMinimal(String apiResponse, int currentSeason, int espnWeek, int internalWeek, NFLSeasonType seasonType) {
        System.out.println("ApiParsers.ParseESPNAPIMinimal method started");
        System.out.println("  ESPN Week (for filtering): " + espnWeek + ", Internal Week (for storage): " + internalWeek);
        List<Game> games = new ArrayList<>();
        NFLSeasonCalculator calculator = new NFLSeasonCalculator();
        
        try {
            JSONObject jsonObject = new JSONObject(apiResponse);
            JSONArray events = jsonObject.getJSONArray("events");

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);

                int seasonYear = event.getJSONObject("season").getInt("year");
                int weekNumber = event.getJSONObject("week").getInt("number");

                // Filter using ESPN's week number
                if (seasonYear != currentSeason || weekNumber != espnWeek) {
                    System.out.println("Skipping game from season " + seasonYear + ", week " + weekNumber);
                    continue;
                }
                
                String gameDate = event.getString("date");
                if (!isValidGameDate(gameDate, seasonYear, weekNumber, seasonType, calculator)) {
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
                // Store using internal week number (e.g., 19 for Wild Card, not ESPN's 1)
                game.setWeek(internalWeek);
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





