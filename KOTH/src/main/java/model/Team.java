package model;

public class Team {
    private int apiTeamID;
    private String apiTeamName;
    private String apiTeamShortName;
    private String apiTeamFullName;

    // Default constructor
    public Team() {
    }

    // Parameterized constructor
    public Team(int apiTeamID, String apiTeamName, String apiTeamShortName, String apiTeamFullName) {
        this.apiTeamID = apiTeamID;
        this.apiTeamName = apiTeamName;
        this.apiTeamShortName = apiTeamShortName;
        this.apiTeamFullName = apiTeamFullName;
    }

    // Getters and Setters
    public int getApiTeamID() {
        return apiTeamID;  // Fixed: return the field, not calling the method again
    }

    public void setApiTeamID(int apiTeamID) {
        this.apiTeamID = apiTeamID;
    }

    public String getApiTeamName() {
        return apiTeamName;
    }

    public void setApiTeamName(String apiTeamName) {
        this.apiTeamName = apiTeamName;
    }

    public String getApiTeamShortName() {
        return apiTeamShortName;
    }

    public void setApiTeamShortName(String apiTeamShortName) {
        this.apiTeamShortName = apiTeamShortName;
    }

    public String getApiTeamFullName() {
        return apiTeamFullName;
    }

    public void setApiTeamFullName(String apiTeamFullName) {
        this.apiTeamFullName = apiTeamFullName;
    }

    @Override
    public String toString() {
        return "Team{" +
               "apiTeamID=" + apiTeamID +
               ", apiTeamName='" + apiTeamName + '\'' +
               ", apiTeamShortName='" + apiTeamShortName + '\'' +
               ", apiTeamFullName='" + apiTeamFullName + '\'' +
               '}';
    }

    // Method to get Display name for scoreboard
    public String getApiDisplayNameTeams() {
        return apiTeamFullName;
    }
}

