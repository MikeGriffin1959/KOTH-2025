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
        // ESPN uses weeks 1-4 for playoffs, we use weeks 19-22
        int espnWeekNumber = (seasonType == NFLSeasonType.PLAYOFFS) 
            ? internalWeekNumber - 18 
            : internalWeekNumber;

        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: ESPN week (for filtering): " + espnWeekNumber);
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Internal week (for storage): " + internalWeekNumber);

        return ApiParsers.ParseESPNAPIMinimal(apiResponse, currentSeason, espnWeekNumber, internalWeekNumber, seasonType);
    }
}