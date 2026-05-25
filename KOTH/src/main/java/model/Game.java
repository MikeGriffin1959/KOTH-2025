package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.Serializable;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    private long gameID;
    private int season;
    private int week;
    private String date;
    private int homeTeamId;
    private String homeTeamName;
    private int homeScore;
    private int awayTeamId;
    private String awayTeamName;
    private int awayScore;
    private String status;
    private Double pointSpread;
    private Double overUnder;
    private Map<Integer, List<String>> userPicks;
    private int userPicksCount;
    private String HomeTeamAbbreviation;
    private String AwayTeamAbbreviation;  
    private boolean showOdds;    
    private boolean showScore;   
    private String displayClock; 
    private String period; 

    public Game() {
        this.userPicks = new HashMap<>();
    }
    
    // Getters and Setters
    public void addUserPick(int userId, String teamPicked) {
        this.userPicks.computeIfAbsent(userId, k -> new ArrayList<>()).add(teamPicked);
    }

    public long getGameID() { return gameID; }
    public void setGameID(long gameID) { this.gameID = gameID; }

    public int getSeason() {
        return season;
    }

    public void setSeason(int season) {
        this.season = season;
    }

    public int getWeek() {
        return week;
    }

    public void setWeek(int week) {
        this.week = week;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getHomeTeamId() {
        return homeTeamId;
    }

    public void setHomeTeamId(int homeTeamId) {
        this.homeTeamId = homeTeamId;
    }

    public int getHomeScore() {
        return homeScore;
    }

    public void setHomeScore(int homeScore) {
        this.homeScore = homeScore;
    }

    public int getAwayTeamId() {
        return awayTeamId;
    }

    public void setAwayTeamId(int awayTeamId) {
        this.awayTeamId = awayTeamId;
    }

    public int getAwayScore() {
        return awayScore;
    }

    public void setAwayScore(int awayScore) {
        this.awayScore = awayScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getPointSpread() {
        return pointSpread;
    }
    
    public void setPointSpread(Double pointSpread) {
        this.pointSpread = pointSpread;
    }
    
    public Double getOverUnder() {
        return overUnder;
    }
    
    public void setOverUnder(Double overUnder) {
        this.overUnder = overUnder;
    }

    public String getHomeTeamName() {
        return homeTeamName;
    }

    public void setHomeTeamName(String homeTeamName) {
        this.homeTeamName = homeTeamName;
    }

    public String getAwayTeamName() {
        return awayTeamName;
    }

    public void setAwayTeamName(String awayTeamName) {
        this.awayTeamName = awayTeamName;
    }

    public Map<Integer, List<String>> getUserPicks() {
        return userPicks;
    }

    public void setUserPicks(Map<Integer, List<String>> userPicks) {
        this.userPicks = userPicks;
    }

    public void initializeUserPicks() {
        if (this.userPicks == null) {
            this.userPicks = new HashMap<>();
        }
    }
    
    public int getUserPicksCount() {
        return userPicksCount;
    }

    public void setUserPicksCount(int userPicksCount) {
        this.userPicksCount = userPicksCount;
    }
    
    public String getHomeTeamAbbreviation() {
        return HomeTeamAbbreviation;
    }
    
    public void setHomeTeamAbbreviation(String HomeTeamAbbreviation) {
        this.HomeTeamAbbreviation = HomeTeamAbbreviation;
    }
    
    public String getAwayTeamAbbreviation() {
        return AwayTeamAbbreviation;
    }

    public void setAwayTeamAbbreviation(String AwayTeamAbbreviation) {
        this.AwayTeamAbbreviation = AwayTeamAbbreviation;
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeUserPicks();
    }

    public void setUserPick(int userId, String teamPicked) {
        initializeUserPicks();
        this.userPicks.computeIfAbsent(userId, k -> new ArrayList<>()).add(teamPicked);
    }
    public boolean isShowOdds() {
        return showOdds;
    }

    public void setShowOdds(boolean showOdds) {
        this.showOdds = showOdds;
    }

    public boolean isShowScore() {
        return showScore;
    }

    public void setShowScore(boolean showScore) {
        this.showScore = showScore;
    }
    
    public String getDisplayClock() {
        return displayClock;
    }

    public void setDisplayClock(String displayClock) {
        this.displayClock = displayClock;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }


    @Override
    public String toString() {
        return "Game{" +gameID + '\'' +
                ", season=" + season +
                ", week=" + week +
                ", date='" + date + '\'' +
                ", status='" + status + '\'' +
                ", homeTeamId=" + homeTeamId +
                ", homeScore=" + homeScore +
                ", awayTeamId=" + awayTeamId +
                ", awayScore=" + awayScore +
                ", pointSpread=" + pointSpread +
                ", overUnder=" + overUnder +
                ", homeTeamName='" + homeTeamName + '\'' +
                ", awayTeamName='" + awayTeamName + '\'' +
                ", displayClock='" + displayClock + '\'' +
                ", period='" + period + '\'' +
                '}';
    }
}