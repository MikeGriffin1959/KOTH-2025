package model;

import java.io.Serializable;

/**
 * Claude Haiku's triage verdict for a single candidate team.
 * Parsed from the JSON the model returns; merged onto the matching EdgeCandidate
 * and persisted to KOTH.EdgeRecommendation (claudeConf / upsetRisk / claudeRationale).
 */
public class TriageResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private int     teamId;        // resolved from the team string Claude echoes back
    private String  team;          // team string as sent/returned (abbrev or name)
    private boolean recommend;
    private Double  confidence;    // 0..1
    private String  upsetRisk;     // low | med | high
    private String  rationale;     // one-line explanation

    public TriageResult() {}

    public int getTeamId() { return teamId; }
    public void setTeamId(int v) { this.teamId = v; }

    public String getTeam() { return team; }
    public void setTeam(String v) { this.team = v; }

    public boolean isRecommend() { return recommend; }
    public void setRecommend(boolean v) { this.recommend = v; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double v) { this.confidence = v; }

    public String getUpsetRisk() { return upsetRisk; }
    public void setUpsetRisk(String v) { this.upsetRisk = v; }

    public String getRationale() { return rationale; }
    public void setRationale(String v) { this.rationale = v; }

    @Override
    public String toString() {
        return "TriageResult{" + team + " rec=" + recommend + " conf=" + confidence +
               " risk=" + upsetRisk + " :: " + rationale + "}";
    }
}