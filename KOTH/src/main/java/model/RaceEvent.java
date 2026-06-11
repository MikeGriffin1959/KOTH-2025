package model;

import java.util.List;

/**
 * A dramatic survivor-pool moment worth commentating on (Commentary M3).
 * Produced by services.EventDetector, consumed by
 * CommentaryService.generateEventCommentary. Parallels GolferFest's
 * CommentaryService.RaceEvent, with KOTH's survivor event taxonomy
 * (design doc §4.3).
 */
public class RaceEvent {

    public enum EventType {
        TROUBLE,           // user's team trailing by 10+ in Q4 — still alive, still drama
        UPSET_ALERT,       // underdog leading the favorite users picked, after halftime
        LATE_DRAMA,        // tied/one-score game inside 2:00 of Q4/OT with fates riding on it
        NARROW_SURVIVAL,   // user's team wins by <=3 — affectionate teasing territory
        ELIMINATION,       // a final loss/tie that ends a user's season — SYMPATHETIC, no Woof
        GAME_FINAL_WIN,    // clean win (optional, high snark only)
        LAST_STAND         // multiple alive users, diverging picks, final game of the week
    }

    private final EventType type;
    private final int gameId;
    private final List<Integer> affectedUserIds;
    private final String description; // human-readable game-state summary for the prompt

    public RaceEvent(EventType type, int gameId, List<Integer> affectedUserIds, String description) {
        this.type = type;
        this.gameId = gameId;
        this.affectedUserIds = affectedUserIds;
        this.description = description;
    }

    public EventType getType() { return type; }
    public int getGameId() { return gameId; }
    public List<Integer> getAffectedUserIds() { return affectedUserIds; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return "RaceEvent{" + type + ", gameId=" + gameId + ", users=" + affectedUserIds + "}";
    }
}
