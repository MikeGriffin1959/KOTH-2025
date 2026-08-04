package model;

/**
 * Per-user, per-season commentary personality profile (Commentary M5,
 * design §5.3). sensitivities is load-bearing for ELIMINATION tone.
 */
public class UserDossier {
    private int dossierId;
    private int userId;
    private int season;
    private String kothSeason;
    private String displayName;    // commentary name; falls back to firstName
    private String personality;
    private String rivalries;
    private String sensitivities;

    // Convenience (joined from User for display/injection; not persisted here)
    private String username;
    private String firstName;

    public UserDossier() {
    }

    public int getDossierId() { return dossierId; }
    public void setDossierId(int dossierId) { this.dossierId = dossierId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }

    public String getKothSeason() { return kothSeason; }
    public void setKothSeason(String kothSeason) { this.kothSeason = kothSeason; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPersonality() { return personality; }
    public void setPersonality(String personality) { this.personality = personality; }

    public String getRivalries() { return rivalries; }
    public void setRivalries(String rivalries) { this.rivalries = rivalries; }

    public String getSensitivities() { return sensitivities; }
    public void setSensitivities(String sensitivities) { this.sensitivities = sensitivities; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    /** The name commentary should use: displayName if set, else firstName. */
    public String commentaryName() {
        if (displayName != null && !displayName.trim().isEmpty()) return displayName.trim();
        return firstName != null ? firstName : username;
    }

    /** True if any dossier content exists worth injecting. */
    public boolean hasContent() {
        return notEmpty(personality) || notEmpty(rivalries) || notEmpty(sensitivities) || notEmpty(displayName);
    }

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
}
