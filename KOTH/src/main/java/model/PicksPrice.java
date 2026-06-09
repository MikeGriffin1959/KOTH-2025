package model;

import java.math.BigDecimal;

public class PicksPrice {
    private int picksPriceSeason;
    private int maxPicks;
    private BigDecimal pickPrice1;
    private BigDecimal pickPrice2;
    private BigDecimal pickPrice3;
    private BigDecimal pickPrice4;
    private BigDecimal pickPrice5;
    private boolean allowSignUp;
    private String kothSeason;
    private boolean maskPicks;
    // Commentary settings (M1) — live here alongside maskPicks/allowSignUp.
    // Defaults match the picksprice column defaults (snark 5, preview day 5=Friday)
    // so a fresh PicksPrice (new season / price update) doesn't reset them to 0.
    private int snarkLevel = 5;
    private boolean commentaryEnabled = false;
    private boolean commentaryNotifications = false;
    private int previewDayOfWeek = 5;

    // Default constructor
    public PicksPrice() {
    }

    // Constructor with all fields
    public PicksPrice(int picksPriceSeason, int maxPicks, BigDecimal pickPrice1, BigDecimal pickPrice2,
            BigDecimal pickPrice3, BigDecimal pickPrice4, BigDecimal pickPrice5,
            boolean allowSignUp, String kothSeason, boolean maskPicks) {
		this.picksPriceSeason = picksPriceSeason;
		this.maxPicks = maxPicks;
		this.pickPrice1 = pickPrice1;
		this.pickPrice2 = pickPrice2;
		this.pickPrice3 = pickPrice3;
		this.pickPrice4 = pickPrice4;
		this.pickPrice5 = pickPrice5;
		this.allowSignUp = allowSignUp;
		this.kothSeason = kothSeason;
	    this.maskPicks = maskPicks;
	}

    // Getters and setters
    public int getPicksPriceSeason() {
        return picksPriceSeason;
    }

    public void setPicksPriceSeason(int picksPriceSeason) {
        this.picksPriceSeason = picksPriceSeason;
    }

    public BigDecimal getPickPrice1() {
        return pickPrice1;
    }

    public void setPickPrice1(BigDecimal pickPrice1) {
        this.pickPrice1 = pickPrice1;
    }

    public BigDecimal getPickPrice2() {
        return pickPrice2;
    }

    public void setPickPrice2(BigDecimal pickPrice2) {
        this.pickPrice2 = pickPrice2;
    }

    public BigDecimal getPickPrice3() {
        return pickPrice3;
    }

    public void setPickPrice3(BigDecimal pickPrice3) {
        this.pickPrice3 = pickPrice3;
    }

    public BigDecimal getPickPrice4() {
        return pickPrice4;
    }

    public void setPickPrice4(BigDecimal pickPrice4) {
        this.pickPrice4 = pickPrice4;
    }

    public BigDecimal getPickPrice5() {
        return pickPrice5;
    }

    public void setPickPrice5(BigDecimal pickPrice5) {
        this.pickPrice5 = pickPrice5;
    }
    
    public int getMaxPicks() {
        return maxPicks;
    }

    public void setMaxPicks(int maxPicks) {
        this.maxPicks = maxPicks;
    }
    
    public boolean isAllowSignUp() {
        return allowSignUp;
    }

    public void setAllowSignUp(boolean allowSignUp) {
        this.allowSignUp = allowSignUp;
    }
    
    public String getKothSeason() {
        return kothSeason;
    }

    public void setKothSeason(String kothSeason) {
        this.kothSeason = kothSeason;
    }
    
    public boolean isMaskPicks() {
        return maskPicks;
    }

    public void setMaskPicks(boolean maskPicks) {
        this.maskPicks = maskPicks;
    }

    public int getSnarkLevel() {
        return snarkLevel;
    }

    public void setSnarkLevel(int snarkLevel) {
        this.snarkLevel = snarkLevel;
    }

    public boolean isCommentaryEnabled() {
        return commentaryEnabled;
    }

    public void setCommentaryEnabled(boolean commentaryEnabled) {
        this.commentaryEnabled = commentaryEnabled;
    }

    public boolean isCommentaryNotifications() {
        return commentaryNotifications;
    }

    public void setCommentaryNotifications(boolean commentaryNotifications) {
        this.commentaryNotifications = commentaryNotifications;
    }

    public int getPreviewDayOfWeek() {
        return previewDayOfWeek;
    }

    public void setPreviewDayOfWeek(int previewDayOfWeek) {
        this.previewDayOfWeek = previewDayOfWeek;
    }

    @Override
    public String toString() {
        return "PicksPrice{" +
                "picksPriceSeason=" + picksPriceSeason +
                ", maxPicks=" + maxPicks +
                ", pickPrice1=" + pickPrice1 +
                ", pickPrice2=" + pickPrice2 +
                ", pickPrice3=" + pickPrice3 +
                ", pickPrice4=" + pickPrice4 +
                ", pickPrice5=" + pickPrice5 +
                ", allowSignUp=" + allowSignUp +
                ", kothSeason='" + kothSeason + '\'' +
                ", maskPicks=" + maskPicks +
                ", snarkLevel=" + snarkLevel +
                ", commentaryEnabled=" + commentaryEnabled +
                ", commentaryNotifications=" + commentaryNotifications +
                ", previewDayOfWeek=" + previewDayOfWeek +
                '}';
    }
}