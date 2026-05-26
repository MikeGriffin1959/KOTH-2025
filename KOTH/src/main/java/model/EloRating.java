package model;

import java.io.Serializable;

/** A single team's ELO rating as of the end of a given week. Maps to KOTH.EloRating. */
public class EloRating implements Serializable {
    private static final long serialVersionUID = 1L;

    private int    season;
    private int    throughWeek;
    private int    teamId;       // = KOTH.Teams.apiTeamID
    private double rating;

    public EloRating() {}

    public EloRating(int season, int throughWeek, int teamId, double rating) {
        this.season = season;
        this.throughWeek = throughWeek;
        this.teamId = teamId;
        this.rating = rating;
    }

    public int getSeason() { return season; }
    public void setSeason(int v) { this.season = v; }

    public int getThroughWeek() { return throughWeek; }
    public void setThroughWeek(int v) { this.throughWeek = v; }

    public int getTeamId() { return teamId; }
    public void setTeamId(int v) { this.teamId = v; }

    public double getRating() { return rating; }
    public void setRating(double v) { this.rating = v; }

    @Override
    public String toString() {
        return "EloRating{team=" + teamId + ", rating=" + String.format("%.1f", rating) +
               ", through " + season + "/wk" + throughWeek + "}";
    }
}