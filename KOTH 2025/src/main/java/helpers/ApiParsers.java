package helpers;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

//import model.ESPNGame;
import model.Game;
import model.Team;

@Service
public class ApiParsers {
    
    public static List<Game> ParseESPNAPI(String apiResponse) {
    	System.out.println("ApiParsers.ParseESPNAPI method started");
        List<Game> gamesList = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(apiResponse);
        JSONArray eventsArray = jsonResponse.getJSONArray("events");

        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject eventObject = eventsArray.getJSONObject(i);
            Game game = new Game();

            game.setGameID(Integer.parseInt(eventObject.getString("id")));
            game.setDate(eventObject.getString("date"));

            JSONObject seasonObject = eventObject.getJSONObject("season");
            game.setSeason(Integer.valueOf(seasonObject.getInt("year")));

            JSONObject weekObject = eventObject.getJSONObject("week");
            game.setWeek(Integer.valueOf(weekObject.getInt("number")));

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
        }

        return gamesList;
    }

    public static List<Game> ParseESPNAPIMinimal(String apiResponse, int currentSeason, int currentWeek) {
        System.out.println("ApiParsers.ParseESPNAPIMinimal method started");
        List<Game> games = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(apiResponse);
            JSONArray events = jsonObject.getJSONArray("events");

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);

                // Extract season and week
                int seasonYear = event.getJSONObject("season").getInt("year");
                int weekNumber = event.getJSONObject("week").getInt("number");

                // ✅ Filter out games from other seasons or weeks
                if (seasonYear != currentSeason || weekNumber != currentWeek) {
                    System.out.println("Skipping game from season " + seasonYear + ", week " + weekNumber);
                    continue;
                }

                JSONObject competition = event.getJSONArray("competitions").getJSONObject(0);
                JSONArray competitors = competition.getJSONArray("competitors");
                JSONObject status = competition.getJSONObject("status");

                Game game = new Game();
                game.setGameID(Long.parseLong(event.getString("id")));
                game.setSeason(seasonYear);
                game.setWeek(weekNumber);

                if (event.has("date")) {
                    game.setDate(event.getString("date"));
                }

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

                System.out.println("\nParsed game " + game.getGameID() + ":");
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





