package helpers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.springframework.stereotype.Service;

import services.NFLGameType;
import services.NFLGameWeek;

@Service
public class ApiFetchers {
 
    private static final String ESPN_FULL_SEASON_URL = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?limit=1000&dates=";
    private static final String ESPN_WEEKLY_SCOREBOARD_URL = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?limit=1000";
    // ESPN's site API started returning 403 (Akamai) in Sep 2026, which silently froze
    // score/status refreshes. The CDN "xhr" endpoint serves the identical scoreboard
    // object under content.sbData, so it is now the primary source (site API = fallback).
    private static final String ESPN_CDN_SCOREBOARD_URL = "https://cdn.espn.com/core/nfl/scoreboard?xhr=1&limit=1000";
    private static final String BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36";
    private static final String ESPN_TEAMS_URL = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams";
    private static final String ESPN_ODDS_BASE_URL = "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/events/%s/competitions/%s/odds";
    
    public enum NFLSeasonType {
        REGULAR_SEASON(2),
        PLAYOFFS(3);

        private final int value;

        NFLSeasonType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    											
    // Method to fetch ESPN full season schedule
    public static String FetchESPNFullSeasonSchedule(int season) throws IOException {
        System.out.println("ApiFetchers.FetchESPNFullSeasonSchedule method called with season: " + season);
        String urlString = ESPN_FULL_SEASON_URL + season;
        return fetchDataFromApi(urlString);
    }

    public static String FetchESPNWeeklyScoreboard(NFLSeasonType seasonType, NFLGameWeek gameWeek) throws IOException {
        System.out.println("ApiFetchers.FetchESPNWeeklyScoreboard method called for season type: " + seasonType + ", game week: " + gameWeek);
        
        // Convert internal week number to ESPN API week number
        int espnWeek = convertToESPNWeek(seasonType, gameWeek);

        // Primary: CDN xhr endpoint (unwrap content.sbData → same shape as the site API)
        int season = new services.NFLSeasonCalculator().getCurrentNFLSeason();
        String cdnUrl = ESPN_CDN_SCOREBOARD_URL +
                        "&year=" + season +
                        "&seasontype=" + seasonType.getValue() +
                        "&week=" + espnWeek;
        System.out.println("Requesting URL: " + cdnUrl);
        String sbData = unwrapSbData(fetchDataFromApi(cdnUrl));
        if (sbData != null) {
            return sbData;
        }
        System.err.println("ApiFetchers.FetchESPNWeeklyScoreboard: CDN scoreboard unavailable — falling back to site API");

        String urlString = ESPN_WEEKLY_SCOREBOARD_URL +
                          "&seasontype=" + seasonType.getValue() +
                          "&week=" + espnWeek;

        System.out.println("Requesting URL: " + urlString);
        return fetchDataFromApi(urlString);
    }

    /** cdn.espn.com wraps the scoreboard as {"content":{"sbData":{...}}}; return sbData as JSON text. */
    private static String unwrapSbData(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            org.json.JSONObject root = new org.json.JSONObject(body);
            org.json.JSONObject content = root.optJSONObject("content");
            org.json.JSONObject sb = content == null ? null : content.optJSONObject("sbData");
            if (sb == null || !sb.has("events")) return null;
            return sb.toString();
        } catch (Exception e) {
            System.err.println("ApiFetchers.unwrapSbData: " + e.getMessage());
            return null;
        }
    }

    private static int convertToESPNWeek(NFLSeasonType seasonType, NFLGameWeek gameWeek) {
        if (seasonType == NFLSeasonType.REGULAR_SEASON) {
            return gameWeek.getWeekNumber();
        }
        
        // Convert playoff weeks (19-22) to ESPN playoff weeks (1,2,3,5 - 4 is Pro Bowl)
        switch (gameWeek.getWeekNumber()) {
            case 19: return 1; // Wild Card
            case 20: return 2; // Divisional
            case 21: return 3; // Conference Championships
            case 22: return 5; // Super Bowl
            default: 
                System.out.println("Warning: Unknown playoff week " + gameWeek.getWeekNumber() + ", defaulting to week 1");
                return 1;
        }
    }

    // Legacy method to maintain backward compatibility - defaults to regular season
    public static String FetchESPNWeeklyScoreboard() throws IOException {
        System.out.println("ApiFetchers.FetchESPNWeeklyScoreboard method called (default regular season)");
        return FetchESPNWeeklyScoreboard(NFLSeasonType.REGULAR_SEASON, new NFLGameWeek(1, NFLGameType.REGULAR_SEASON));
    }
    
    // Method to fetch ESPN Teams
    public static String FetchESPNTeams() throws IOException {
        System.out.println("ApiFetchers.FetchESPNTeams method called");
        return fetchDataFromApi(ESPN_TEAMS_URL);
    }

    // Method to fetch ESPN odds for a specific game
    public static String FetchESPNGameOdds(String gameId) throws IOException {
        System.out.println("\nApiFetchers.FetchESPNGameOdds method called for GameID: " + gameId);
        String urlString = String.format(ESPN_ODDS_BASE_URL, gameId, gameId);
        System.out.println("Attempting to fetch odds from URL: " + urlString);
        
        try {
            String response = fetchDataFromApi(urlString);
            if (response != null) {
                System.out.println("Successful odds fetch for GameID: " + gameId);
                System.out.println("Response length: " + response.length());
                if (response.length() < 1000) {  // Log full response if it's small
                    System.out.println("Response content: " + response);
                } else {
                    System.out.println("Response preview: " + response.substring(0, 500));
                }
            } else {
                System.out.println("No response received for GameID: " + gameId);
            }
            return response;
        } catch (Exception e) {
            System.err.println("Error fetching odds for GameID: " + gameId);
            System.err.println("Error details: " + e.getMessage());
            throw e;
        }
    }
    
    // Method to fetch data with URLs prepared above
    @SuppressWarnings("deprecation")
    private static String fetchDataFromApi(String urlString) {
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // Browser-like headers: ESPN's edge rejects the default Java user agent.
            connection.setRequestProperty("User-Agent", BROWSER_UA);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            // Check if the response code is successful (200 OK)
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("API request failed with response code: " + connection.getResponseCode()
                        + " for " + urlString);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
            return null; 
        }
        return result.toString();
    }
}



