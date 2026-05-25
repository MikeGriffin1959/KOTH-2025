package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import helpers.ApiFetchers;
import helpers.ApiFetchers.NFLSeasonType;
import helpers.ApiParsers;
import model.Game;
import java.io.IOException;
import java.util.List;

@Service
public class NFLGameFetcherService {

    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;

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

        return ApiParsers.ParseESPNAPIMinimal(apiResponse, currentSeason, espnWeekNumber, internalWeekNumber, seasonType);
    }
}