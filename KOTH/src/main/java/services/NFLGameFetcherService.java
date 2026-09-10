package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import helpers.ApiFetchers;
import helpers.ApiFetchers.NFLSeasonType;
import helpers.ApiParsers;
import helpers.SqlConnectorGameTable;
import model.Game;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NFLGameFetcherService {

    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;

    @Autowired
    private SqlConnectorGameTable gameTable;

    public List<Game> fetchCurrentWeekGames() throws IOException {
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames method started");

        NFLSeasonType seasonType = nflSeasonCalculator.getCurrentSeasonType();
        NFLGameWeek currentWeek = nflSeasonCalculator.getCurrentNFLWeek();
        int currentSeason = nflSeasonCalculator.getCurrentNFLSeason();

        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Fetching games for season type: " + seasonType);
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Current NFL Week: " + currentWeek);
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Current NFL Season: " + currentSeason);

        String apiResponse = ApiFetchers.FetchESPNWeeklyScoreboard(seasonType, currentWeek);

        // Get internal week number (19-22 for playoffs)
        int internalWeekNumber = currentWeek.getWeekNumber();

     // Convert to ESPN week numbering for filtering API response
     // ESPN uses weeks 1,2,3,5 for playoffs (4 is Pro Bowl), we use weeks 19-22
     int espnWeekNumber;
     if (seasonType == NFLSeasonType.PLAYOFFS) {
         switch (internalWeekNumber) {
             case 19: espnWeekNumber = 1; break; // Wild Card
             case 20: espnWeekNumber = 2; break; // Divisional
             case 21: espnWeekNumber = 3; break; // Conference Championships
             case 22: espnWeekNumber = 5; break; // Super Bowl (skip 4 = Pro Bowl)
             default: espnWeekNumber = 1;
         }
     } else {
         espnWeekNumber = internalWeekNumber;
     }

        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: ESPN week (for filtering): " + espnWeekNumber);
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Internal week (for storage): " + internalWeekNumber);

        if (apiResponse == null || apiResponse.isEmpty()) {
            // Both scoreboard endpoints are being challenged/blocked (Sep 2026: CDN answers
            // 202-empty to the JVM, site API 403s). Refresh per game from the core API instead.
            System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: scoreboard unavailable — using core API per game");
            return fetchWeekViaCoreApi(currentSeason, internalWeekNumber);
        }

        return ApiParsers.ParseESPNAPIMinimal(apiResponse, currentSeason, espnWeekNumber, internalWeekNumber, seasonType);
    }

    /**
     * Fallback refresh: start from the week's games already in KOTH.Game (ids, kickoff,
     * teams) and pull status + scores per game from sports.core.api.espn.com. Only games
     * that have kicked off and aren't final are fetched (scheduled games can't have
     * changed; finals don't change), so a typical Sunday tick costs a handful of calls.
     * Returns Game objects ready for updateGameTableMinimal.
     */
    List<Game> fetchWeekViaCoreApi(int season, int internalWeek) {
        List<Game> out = new ArrayList<>();
        List<Game> current = gameTable.getGamesForWeek(season, internalWeek);
        if (current == null) return out;
        Instant now = Instant.now();
        int fetched = 0;

        for (Game g : current) {
            String st = g.getStatus() == null ? "" : g.getStatus();
            boolean isFinal = st.equals("STATUS_FINAL") || st.equals("Final") || st.equals("F/OT");
            Instant kickoff = parseKickoff(g.getDate());
            boolean kickedOff = kickoff != null && !kickoff.isAfter(now);
            if (isFinal || !kickedOff) {
                continue; // nothing to refresh
            }

            try {
                String statusJson = ApiFetchers.FetchESPNCoreStatus(g.getGameID());
                if (statusJson == null || statusJson.isEmpty()) continue;
                org.json.JSONObject status = new org.json.JSONObject(statusJson);
                org.json.JSONObject type = status.optJSONObject("type");
                String name = type == null ? null : type.optString("name", null);
                if (name == null || name.isEmpty()) continue;

                Game u = new Game();
                u.setGameID(g.getGameID());
                u.setSeason(season);
                u.setWeek(internalWeek);
                u.setDate(g.getDate());
                u.setHomeTeamId(g.getHomeTeamId());
                u.setAwayTeamId(g.getAwayTeamId());
                u.setHomeTeamName(g.getHomeTeamName());
                u.setAwayTeamName(g.getAwayTeamName());
                u.setStatus(name);
                u.setPeriod(String.valueOf(status.optInt("period", 0)));
                u.setDisplayClock(status.optString("displayClock", "0:00"));

                String state = type.optString("state", "");
                if (!"pre".equals(state)) {
                    u.setHomeScore(coreScore(g.getGameID(), g.getHomeTeamId()));
                    u.setAwayScore(coreScore(g.getGameID(), g.getAwayTeamId()));
                } else {
                    u.setHomeScore(0);
                    u.setAwayScore(0);
                }
                out.add(u);
                fetched++;
                System.out.println("NFLGameFetcherService.core: " + g.getGameID() + " " + g.getAwayTeamName() + "@"
                        + g.getHomeTeamName() + " " + name + " " + u.getAwayScore() + "-" + u.getHomeScore()
                        + " Q" + u.getPeriod() + " " + u.getDisplayClock());
            } catch (Exception e) {
                System.err.println("NFLGameFetcherService.core: failed for " + g.getGameID() + ": " + e.getMessage());
            }
        }
        System.out.println("NFLGameFetcherService.fetchWeekViaCoreApi: refreshed " + fetched + " game(s) for "
                + season + "/wk" + internalWeek);
        return out;
    }

    private int coreScore(long gameId, int teamId) {
        try {
            String json = ApiFetchers.FetchESPNCoreScore(gameId, teamId);
            if (json == null || json.isEmpty()) return 0;
            return (int) Math.round(new org.json.JSONObject(json).optDouble("value", 0));
        } catch (Exception e) {
            return 0;
        }
    }

    /** game.date is ISO UTC, sometimes minutes-only ("2026-09-10T00:20Z"). */
    private Instant parseKickoff(String date) {
        if (date == null || date.isEmpty()) return null;
        String d = date.trim();
        if (d.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z")) d = d.substring(0, d.length() - 1) + ":00Z";
        try {
            return java.time.ZonedDateTime.parse(d).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
