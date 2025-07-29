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

	    // ✅ Parse and filter games
	    return ApiParsers.ParseESPNAPIMinimal(apiResponse, currentSeason, currentWeek.getWeekNumber());
	}

}