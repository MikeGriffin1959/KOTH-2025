package model;

import java.io.Serializable;

/**
 * One game's projection from Nate Silver's ELWAY model (Silver Bulletin).
 * Home-oriented: homeWinProb is P(home wins outright); homeSpread follows
 * Elway's convention where NEGATIVE means the home team is favored.
 * Maps to one KOTH.ElwayProjection row.
 */
public class ElwayProjection implements Serializable {
    private static final long serialVersionUID = 1L;

    private int     season;
    private int     week;
    private int     homeTeamId;
    private int     awayTeamId;
    private double  homeWinProb;
    private Double  homeSpread;
    private Double  total;
    private boolean neutralSite;

    public ElwayProjection() {}

    public int getSeason() { return season; }
    public void setSeason(int v) { this.season = v; }

    public int getWeek() { return week; }
    public void setWeek(int v) { this.week = v; }

    public int getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(int v) { this.homeTeamId = v; }

    public int getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(int v) { this.awayTeamId = v; }

    public double getHomeWinProb() { return homeWinProb; }
    public void setHomeWinProb(double v) { this.homeWinProb = v; }

    public Double getHomeSpread() { return homeSpread; }
    public void setHomeSpread(Double v) { this.homeSpread = v; }

    public Double getTotal() { return total; }
    public void setTotal(Double v) { this.total = v; }

    public boolean isNeutralSite() { return neutralSite; }
    public void setNeutralSite(boolean v) { this.neutralSite = v; }

    @Override
    public String toString() {
        return "Elway{" + season + "/wk" + week + " " + awayTeamId + "@" + homeTeamId +
               " pHome=" + homeWinProb + " spread=" + homeSpread + " total=" + total +
               (neutralSite ? " (N)" : "") + "}";
    }
}
