package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One pickable team for a given week: the team, its game, the three source
 * win-probabilities (oriented to THIS team, not home), the blended probability,
 * the upset flags raised against it, the final safety score, and how many of the
 * user's lives the allocator assigned to it.
 *
 * Sorted by safetyScore descending to form the weekly "safe list".
 */
public class EdgeCandidate implements Serializable {
    private static final long serialVersionUID = 1L;

    private long   espnEventId;
    private int    teamId;
    private String teamName;        // display name (from KOTH.Teams / getGamesForWeek)
    private int    opponentTeamId;
    private String opponentName;
    private boolean isHome;
    private String kickoffUtc;

    // probabilities oriented to THIS team (so a road favorite reads > 0.5)
    private Double marketProb;
    private Double fpiProb;
    private Double eloProb;
    private Double blendedProb;

    private List<String> upsetFlags = new ArrayList<>();
    private double divergencePenalty;   // from model/market disagreement
    private double upsetPenalty;        // sum of situational penalties
    private double safetyScore;         // blendedProb - divergencePenalty - upsetPenalty

    private int allocatedLives;         // set by the allocator (Spread/Stack)
    private boolean recommended;        // top of the safe list / chosen by allocator

    // ── Claude triage (M3) ──
    private Double claudeConfidence;    // 0..1, or null if triage not run
    private String claudeUpsetRisk;     // low | med | high
    private String claudeRationale;     // one-line explanation
    private Boolean claudeRecommend;    // Claude's own thumbs up/down

    public EdgeCandidate() {}

    public void addFlag(String flag) { this.upsetFlags.add(flag); }

    // ── getters / setters ─────────────────────────────────────
    public long getEspnEventId() { return espnEventId; }
    public void setEspnEventId(long v) { this.espnEventId = v; }

    public int getTeamId() { return teamId; }
    public void setTeamId(int v) { this.teamId = v; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String v) { this.teamName = v; }

    public int getOpponentTeamId() { return opponentTeamId; }
    public void setOpponentTeamId(int v) { this.opponentTeamId = v; }

    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String v) { this.opponentName = v; }

    public boolean isHome() { return isHome; }
    public void setHome(boolean v) { this.isHome = v; }

    public String getKickoffUtc() { return kickoffUtc; }
    public void setKickoffUtc(String v) { this.kickoffUtc = v; }

    public Double getMarketProb() { return marketProb; }
    public void setMarketProb(Double v) { this.marketProb = v; }

    public Double getFpiProb() { return fpiProb; }
    public void setFpiProb(Double v) { this.fpiProb = v; }

    public Double getEloProb() { return eloProb; }
    public void setEloProb(Double v) { this.eloProb = v; }

    public Double getBlendedProb() { return blendedProb; }
    public void setBlendedProb(Double v) { this.blendedProb = v; }

    public List<String> getUpsetFlags() { return upsetFlags; }
    public void setUpsetFlags(List<String> v) { this.upsetFlags = v; }

    public double getDivergencePenalty() { return divergencePenalty; }
    public void setDivergencePenalty(double v) { this.divergencePenalty = v; }

    public double getUpsetPenalty() { return upsetPenalty; }
    public void setUpsetPenalty(double v) { this.upsetPenalty = v; }

    public double getSafetyScore() { return safetyScore; }
    public void setSafetyScore(double v) { this.safetyScore = v; }

    public int getAllocatedLives() { return allocatedLives; }
    public void setAllocatedLives(int v) { this.allocatedLives = v; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean v) { this.recommended = v; }

    public Double getClaudeConfidence() { return claudeConfidence; }
    public void setClaudeConfidence(Double v) { this.claudeConfidence = v; }

    public String getClaudeUpsetRisk() { return claudeUpsetRisk; }
    public void setClaudeUpsetRisk(String v) { this.claudeUpsetRisk = v; }

    public String getClaudeRationale() { return claudeRationale; }
    public void setClaudeRationale(String v) { this.claudeRationale = v; }

    public Boolean getClaudeRecommend() { return claudeRecommend; }
    public void setClaudeRecommend(Boolean v) { this.claudeRecommend = v; }

    public boolean hasClaude() { return claudeConfidence != null || claudeRationale != null; }

    // convenience for the JSP
    public boolean hasFlags() { return upsetFlags != null && !upsetFlags.isEmpty(); }
    public String getFlagsDisplay() {
        return (upsetFlags == null || upsetFlags.isEmpty()) ? "" : String.join(", ", upsetFlags);
    }

    @Override
    public String toString() {
        return "EdgeCandidate{" + teamName + " vs " + opponentName +
               ", blended=" + blendedProb + ", safety=" + String.format("%.3f", safetyScore) +
               ", flags=" + upsetFlags + ", lives=" + allocatedLives + "}";
    }
}