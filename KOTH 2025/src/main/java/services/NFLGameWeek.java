package services;

public class NFLGameWeek {
    private final int weekNumber;
    private final NFLGameType gameType;

    public NFLGameWeek(int weekNumber, NFLGameType gameType) {
        this.weekNumber = weekNumber;
        this.gameType = gameType;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public NFLGameType getGameType() {
        return gameType;
    }

    @Override
    public String toString() {
        return String.format("%s Week %d", gameType, weekNumber);
    }
}