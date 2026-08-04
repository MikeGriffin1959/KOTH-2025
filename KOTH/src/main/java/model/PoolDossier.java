package model;

/**
 * Pool-level identity/lore, one row per season (Commentary M5, design §5.4).
 * Prepended to commentary prompts as the group-context layer.
 */
public class PoolDossier {
    private int season;
    private String kothSeason;
    private String poolIdentity;
    private String poolHistory;
    private String poolLore;
    private String commissionerNotes;
    private String toneGuidance;

    public PoolDossier() {
    }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }

    public String getKothSeason() { return kothSeason; }
    public void setKothSeason(String kothSeason) { this.kothSeason = kothSeason; }

    public String getPoolIdentity() { return poolIdentity; }
    public void setPoolIdentity(String poolIdentity) { this.poolIdentity = poolIdentity; }

    public String getPoolHistory() { return poolHistory; }
    public void setPoolHistory(String poolHistory) { this.poolHistory = poolHistory; }

    public String getPoolLore() { return poolLore; }
    public void setPoolLore(String poolLore) { this.poolLore = poolLore; }

    public String getCommissionerNotes() { return commissionerNotes; }
    public void setCommissionerNotes(String commissionerNotes) { this.commissionerNotes = commissionerNotes; }

    public String getToneGuidance() { return toneGuidance; }
    public void setToneGuidance(String toneGuidance) { this.toneGuidance = toneGuidance; }

    /** True if any field has content worth injecting into prompts. */
    public boolean hasContent() {
        return notEmpty(poolIdentity) || notEmpty(poolHistory) || notEmpty(poolLore)
                || notEmpty(commissionerNotes) || notEmpty(toneGuidance);
    }

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
}
