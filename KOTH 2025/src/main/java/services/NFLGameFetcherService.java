package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import helpers.ApiFetchers;
import helpers.ApiFetchers.NFLSeasonType;
import java.io.IOException;

@Service
public class NFLGameFetcherService {
    
    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;

    public String fetchCurrentWeekGames() throws IOException {
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames method started");
        
        NFLSeasonType seasonType = nflSeasonCalculator.getCurrentSeasonType();
        NFLGameWeek currentWeek = nflSeasonCalculator.getCurrentNFLWeek();

        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Fetching games for season type: " + seasonType);
        System.out.println("NFLGameFetcherService.fetchCurrentWeekGames: Current NFL Week: " + currentWeek);

        return ApiFetchers.FetchESPNWeeklyScoreboard(seasonType, currentWeek);
    }

    public String fetchGamesForType(NFLSeasonType seasonType, NFLGameWeek gameWeek) throws IOException {
        return ApiFetchers.FetchESPNWeeklyScoreboard(seasonType, gameWeek);
    }
}