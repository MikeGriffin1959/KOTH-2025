package model;

import java.sql.Timestamp;

/**
 * POJO for the KOTH.Commentary table (Commentary feature, M1).
 * One row per generated commentary blurb. See KOTH-Commentary-Design.md §5.2.
 *
 * Nullable DB columns are modeled as boxed types (Integer) so an unset value
 * is null rather than 0 — gameId/promptTokens/responseTokens are NULL for the
 * M1 TEST stream until a real value is recorded.
 */
public class Commentary {
    private int commentaryId;
    private int season;
    private String kothSeason;           // multi-pool insurance; matches picksprice.kothSeason
    private int week;
    private String streamType;           // PREVIEW, REVEAL, EVENT, RECAP, TEST
    private String eventType;            // TROUBLE, ELIMINATION, etc. (null for non-event streams)
    private String affectedUserIds;      // comma-separated idUser values
    private Integer gameId;              // references game.GameID (nullable)
    private int snarkLevel;
    private Integer promptTokens;        // Anthropic usage.input_tokens (nullable)
    private Integer responseTokens;      // Anthropic usage.output_tokens (nullable)
    private String body;
    private Timestamp createdAt;         // DB-populated (DEFAULT CURRENT_TIMESTAMP)

    public Commentary() {
    }

    public int getCommentaryId() {
        return commentaryId;
    }

    public void setCommentaryId(int commentaryId) {
        this.commentaryId = commentaryId;
    }

    public int getSeason() {
        return season;
    }

    public void setSeason(int season) {
        this.season = season;
    }

    public String getKothSeason() {
        return kothSeason;
    }

    public void setKothSeason(String kothSeason) {
        this.kothSeason = kothSeason;
    }

    public int getWeek() {
        return week;
    }

    public void setWeek(int week) {
        this.week = week;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAffectedUserIds() {
        return affectedUserIds;
    }

    public void setAffectedUserIds(String affectedUserIds) {
        this.affectedUserIds = affectedUserIds;
    }

    public Integer getGameId() {
        return gameId;
    }

    public void setGameId(Integer gameId) {
        this.gameId = gameId;
    }

    public int getSnarkLevel() {
        return snarkLevel;
    }

    public void setSnarkLevel(int snarkLevel) {
        this.snarkLevel = snarkLevel;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getResponseTokens() {
        return responseTokens;
    }

    public void setResponseTokens(Integer responseTokens) {
        this.responseTokens = responseTokens;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Commentary{" +
                "commentaryId=" + commentaryId +
                ", season=" + season +
                ", kothSeason='" + kothSeason + '\'' +
                ", week=" + week +
                ", streamType='" + streamType + '\'' +
                ", eventType='" + eventType + '\'' +
                ", affectedUserIds='" + affectedUserIds + '\'' +
                ", gameId=" + gameId +
                ", snarkLevel=" + snarkLevel +
                ", promptTokens=" + promptTokens +
                ", responseTokens=" + responseTokens +
                ", body='" + (body != null && body.length() > 60 ? body.substring(0, 60) + "..." : body) + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
