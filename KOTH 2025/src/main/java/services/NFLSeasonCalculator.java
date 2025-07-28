package services;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Service;
import helpers.ApiFetchers.NFLSeasonType;

@Service
public class NFLSeasonCalculator {
    private static final int REGULAR_SEASON_WEEKS = 18;
    
    public int getCurrentNFLSeason() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();

        // If we're in January or February, still consider previous season (Super Bowl period)
        if (now.getMonthValue() <= 2) {
            return year - 1;
        }

        // From March onward, use the upcoming/current year season
        return year;
    }


    public int getCurrentNFLWeekNumber() {
        return getCurrentNFLWeek().getWeekNumber();
    }

    public NFLGameWeek getCurrentNFLWeek() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        int currentSeason = getCurrentNFLSeason();
        LocalDateTime seasonStartDateTime = getSeasonStartDateTime(currentSeason);
        
        if (currentDateTime.isBefore(seasonStartDateTime)) {
            return new NFLGameWeek(1, NFLGameType.REGULAR_SEASON);
        }

        // Calculate total weeks since season start
        long hoursSinceSeasonStart = ChronoUnit.HOURS.between(seasonStartDateTime, currentDateTime);
        int weeksSinceStart = (int) (hoursSinceSeasonStart / (7 * 24)) + 1;
        
        return determineGameWeek(weeksSinceStart);
    }

    private NFLGameWeek determineGameWeek(int weeksSinceStart) {
        // Regular season
        if (weeksSinceStart <= REGULAR_SEASON_WEEKS) {
            return new NFLGameWeek(weeksSinceStart, NFLGameType.REGULAR_SEASON);
        }
        
        // Playoff weeks
        int playoffWeek = weeksSinceStart - REGULAR_SEASON_WEEKS;
        switch (playoffWeek) {
            case 1:
                return new NFLGameWeek(19, NFLGameType.WILD_CARD);
            case 2:
                return new NFLGameWeek(20, NFLGameType.DIVISIONAL);
            case 3:
                return new NFLGameWeek(21, NFLGameType.CONFERENCE);
            default:
                return new NFLGameWeek(22, NFLGameType.SUPER_BOWL);
        }
    }

    public NFLSeasonType getCurrentSeasonType() {
        NFLGameType currentGameType = getCurrentNFLWeek().getGameType();
        return (currentGameType == NFLGameType.REGULAR_SEASON) 
            ? NFLSeasonType.REGULAR_SEASON 
            : NFLSeasonType.PLAYOFFS;
    }

    private LocalDate getSeasonStartDate(int year) {
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        return laborDay.plusDays(2); // Wednesday after Labor Day
    }

    private LocalDateTime getSeasonStartDateTime(int year) {
        LocalDate seasonStartDate = getSeasonStartDate(year);
        LocalDateTime seasonStartDateTime = seasonStartDate.atTime(6, 0); // 6 AM on season start date
        // Adjust to previous Tuesday 6 AM
        while (seasonStartDateTime.getDayOfWeek() != DayOfWeek.TUESDAY) {
            seasonStartDateTime = seasonStartDateTime.minusDays(1);
        }
        return seasonStartDateTime;
    }
}