package model;

import java.io.Serializable;

/**
 * Per-game edge evidence: market / FPI / ELO win probabilities and their blend,
 * plus the inputs used to derive them. One instance maps to one KOTH.EdgeSnapshot row.
 *
 * All probabilities are home-team P(win outright) in [0,1]; the away value is the mirror.
 */
public class GameEdge implements Serializable {
    private static final long serialVersionUID = 1L;

    private long   espnEventId;     // = KOTH.Game.GameID
    private int    season;
    private int    internalWeek;    // your 1..22 scheme
    private int    homeTeamId;      // = KOTH.Teams.apiTeamID
    private int    awayTeamId;
    private String kickoffUtc;      // SQL datetime string 'yyyy-MM-dd HH:mm:ss'
    private boolean neutralSite;

    private Double spread;          // Vegas spread magnitude actually used (>= 0), or null
    private Boolean favoriteIsHome; // which side the spread favors (from FPI orientation)

    private Double marketHome;      // P(home win) from spread
    private Double fpiHome;         // P(home win) from ESPN predictor gameProjection
    private Double eloHome;         // P(home win) from our ELO
    private Double blendedHome;     // weighted blend
    private Double predPtDiffHome;  // FPI teamPredPtDiff for home (sanity/cross-check)

    public GameEdge() {}

    // Convenience: away = 1 - home (probabilities are two-way, tie folded into loss)
    public Double getMarketAway()  { return marketHome  == null ? null : 1.0 - marketHome;  }
    public Double getFpiAway()     { return fpiHome     == null ? null : 1.0 - fpiHome;     }
    public Double getEloAway()     { return eloHome     == null ? null : 1.0 - eloHome;     }
    public Double getBlendedAway() { return blendedHome == null ? null : 1.0 - blendedHome; }

    // ── getters / setters ─────────────────────────────────────
    public long getEspnEventId() { return espnEventId; }
    public void setEspnEventId(long v) { this.espnEventId = v; }

    public int getSeason() { return season; }
    public void setSeason(int v) { this.season = v; }

    public int getInternalWeek() { return internalWeek; }
    public void setInternalWeek(int v) { this.internalWeek = v; }

    public int getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(int v) { this.homeTeamId = v; }

    public int getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(int v) { this.awayTeamId = v; }

    public String getKickoffUtc() { return kickoffUtc; }
    public void setKickoffUtc(String v) { this.kickoffUtc = v; }

    public boolean isNeutralSite() { return neutralSite; }
    public void setNeutralSite(boolean v) { this.neutralSite = v; }

    public Double getSpread() { return spread; }
    public void setSpread(Double v) { this.spread = v; }

    public Boolean getFavoriteIsHome() { return favoriteIsHome; }
    public void setFavoriteIsHome(Boolean v) { this.favoriteIsHome = v; }

    public Double getMarketHome() { return marketHome; }
    public void setMarketHome(Double v) { this.marketHome = v; }

    public Double getFpiHome() { return fpiHome; }
    public void setFpiHome(Double v) { this.fpiHome = v; }

    public Double getEloHome() { return eloHome; }
    public void setEloHome(Double v) { this.eloHome = v; }

    public Double getBlendedHome() { return blendedHome; }
    public void setBlendedHome(Double v) { this.blendedHome = v; }

    public Double getPredPtDiffHome() { return predPtDiffHome; }
    public void setPredPtDiffHome(Double v) { this.predPtDiffHome = v; }

    @Override
    public String toString() {
        return "GameEdge{event=" + espnEventId + ", " + awayTeamId + "@" + homeTeamId +
               ", mkt=" + marketHome + ", fpi=" + fpiHome + ", elo=" + eloHome +
               ", blended=" + blendedHome + ", neutral=" + neutralSite + "}";
    }
}