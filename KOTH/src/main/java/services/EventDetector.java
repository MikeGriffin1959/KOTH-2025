package services;

import helpers.SqlConnectorCommentaryTable;
import model.RaceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live survivor-event detection (Commentary M3, design doc §4.3). Reads the
 * current game + picks state from the DB and emits RaceEvents. Stateless —
 * the scheduler dedupes against the commentary table (idx_dedupe) so the same
 * (week, game, eventType) never fires twice no matter how often this runs.
 *
 * DB conventions honored here:
 *  - game.status carries raw ESPN values (STATUS_*); finals also accept the
 *    legacy 'Final'/'F/OT' forms (same forgiveness as isWinningPick)
 *  - game.period is a numeric string ("1".."4", "5"+ = OT)
 *  - game.displayClock is "M:SS"
 *  - pointSpread is home-relative (negative = home favored); the sign is not
 *    fully trusted upstream, so UPSET_ALERT requires a clear |spread| >= 3
 *  - a tie is a loss
 */
@Service
public class EventDetector {

    @Autowired
    private SqlConnectorCommentaryTable commentaryTable;

    /**
     * Detect events for the week. snarkLevel gates GAME_FINAL_WIN (design:
     * "optional at high snark only").
     */
    public List<RaceEvent> detect(int season, int week, int snarkLevel) {
        List<Map<String, Object>> picks = commentaryTable.getWeekPicksWithGameState(season, week);
        if (picks.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> standings = commentaryTable.getSeasonStandings(season);
        Map<String, Integer> remainingByUsername = new LinkedHashMap<>();
        for (Map<String, Object> s : standings) {
            remainingByUsername.put((String) s.get("username"), (Integer) s.get("remaining"));
        }

        // Aggregate affected users per (eventType, gameId) so one game emits one
        // event per type with all affected users (affectedUserIds is a list).
        Map<String, EventDraft> drafts = new LinkedHashMap<>();

        for (Map<String, Object> p : picks) {
            String status = (String) p.get("status");
            boolean isFinal = isFinal(status);
            boolean isLive = isLive(status);
            if (!isFinal && !isLive) continue;

            int gameId = (Integer) p.get("gameId");
            String selected = (String) p.get("selectedTeam");
            String home = (String) p.get("homeTeamName");
            String away = (String) p.get("awayTeamName");
            int homeScore = (Integer) p.get("homeScore");
            int awayScore = (Integer) p.get("awayScore");
            int periodNum = parseIntSafe((String) p.get("period"));
            int clockSeconds = parseClockSeconds((String) p.get("displayClock"));
            Double spread = (Double) p.get("pointSpread");

            boolean pickedHome = selected != null && selected.equals(home);
            int pickScore = pickedHome ? homeScore : awayScore;
            int oppScore = pickedHome ? awayScore : homeScore;
            int margin = pickScore - oppScore;

            int userId = (Integer) p.get("idUser");
            String firstName = (String) p.get("firstName");
            String username = (String) p.get("username");
            String gameLine = away + " " + awayScore + " @ " + home + " " + homeScore;

            if (isLive) {
                // TROUBLE: trailing by 10+ in Q4
                if (periodNum == 4 && margin <= -10) {
                    add(drafts, RaceEvent.EventType.TROUBLE, gameId, userId, firstName,
                        gameLine + ", Q4 " + p.get("displayClock") + ". " + firstName
                        + " picked " + selected + ", trailing by " + (-margin) + ".");
                }
                // UPSET_ALERT: clear favorite (|spread| >= 3) that users picked is
                // trailing after halftime
                if (periodNum >= 3 && spread != null && Math.abs(spread) >= 3) {
                    String favorite = spread < 0 ? home : away;
                    boolean pickedFavorite = selected != null && selected.equals(favorite);
                    boolean favoriteTrailing = (spread < 0) ? homeScore < awayScore : awayScore < homeScore;
                    if (pickedFavorite && favoriteTrailing) {
                        add(drafts, RaceEvent.EventType.UPSET_ALERT, gameId, userId, firstName,
                            gameLine + ", Q" + periodNum + ". " + selected + " were favored by "
                            + Math.abs(spread) + " and are trailing. " + firstName + " picked them.");
                    }
                }
                // LATE_DRAMA: one-score game inside 2:00 of Q4/OT with a fate riding on it
                if (periodNum >= 4 && clockSeconds >= 0 && clockSeconds <= 120
                        && Math.abs(homeScore - awayScore) <= 8) {
                    add(drafts, RaceEvent.EventType.LATE_DRAMA, gameId, userId, firstName,
                        gameLine + ", " + (periodNum > 4 ? "OT" : "Q4") + " " + p.get("displayClock")
                        + " — one-score game. " + firstName + "'s pick (" + selected + ") hangs on this.");
                }
            }

            if (isFinal) {
                if (margin > 0 && margin <= 3) {
                    // NARROW_SURVIVAL: won by a field goal or less
                    add(drafts, RaceEvent.EventType.NARROW_SURVIVAL, gameId, userId, firstName,
                        "FINAL: " + gameLine + ". " + firstName + "'s " + selected
                        + " survived by " + margin + ".");
                } else if (margin > 3 && snarkLevel >= 8) {
                    // GAME_FINAL_WIN: clean win — optional, high snark only
                    add(drafts, RaceEvent.EventType.GAME_FINAL_WIN, gameId, userId, firstName,
                        "FINAL: " + gameLine + ". " + firstName + "'s " + selected
                        + " won comfortably by " + margin + ".");
                } else if (margin <= 0) {
                    // Loss or tie (a tie is a loss). ELIMINATION only when it ends
                    // the user's season (remaining picks now 0).
                    Integer remaining = remainingByUsername.get(username);
                    if (remaining != null && remaining == 0) {
                        String how = (margin == 0) ? "TIED (a tie is a loss)" : "lost by " + (-margin);
                        add(drafts, RaceEvent.EventType.ELIMINATION, gameId, userId, firstName,
                            "FINAL: " + gameLine + ". " + firstName + "'s " + selected + " " + how
                            + " — that was their last pick. " + firstName + " is OUT of the pool for the season.");
                    }
                }
            }
        }

        // LAST_STAND: exactly one non-final game left this week, and 2+ alive
        // users have diverging picks riding on it.
        detectLastStand(season, week, picks, remainingByUsername, drafts);

        List<RaceEvent> events = new ArrayList<>();
        for (EventDraft d : drafts.values()) {
            events.add(new RaceEvent(d.type, d.gameId, d.userIds, d.description.toString()));
        }
        return events;
    }

    private void detectLastStand(int season, int week, List<Map<String, Object>> picks,
                                 Map<String, Integer> remainingByUsername,
                                 Map<String, EventDraft> drafts) {
        List<Map<String, Object>> games = commentaryTable.getWeekGameStates(season, week);
        if (games.isEmpty()) return;

        Integer lastGameId = null;
        int nonFinal = 0;
        for (Map<String, Object> g : games) {
            if (!isFinal((String) g.get("status"))) {
                nonFinal++;
                lastGameId = (Integer) g.get("gameId");
            }
        }
        if (nonFinal != 1 || lastGameId == null) return;

        // Alive users with picks on the last game, by team
        Map<String, List<String>> teamToNames = new LinkedHashMap<>();
        List<Integer> userIds = new ArrayList<>();
        for (Map<String, Object> p : picks) {
            if (!lastGameId.equals(p.get("gameId"))) continue;
            Integer remaining = remainingByUsername.get((String) p.get("username"));
            if (remaining == null || remaining <= 0) continue;
            teamToNames.computeIfAbsent((String) p.get("selectedTeam"), k -> new ArrayList<>())
                       .add((String) p.get("firstName"));
            int id = (Integer) p.get("idUser");
            if (!userIds.contains(id)) userIds.add(id);
        }
        if (userIds.size() < 2 || teamToNames.size() < 2) return;

        StringBuilder desc = new StringBuilder("Last game of week " + week + " and survivors' picks diverge: ");
        teamToNames.forEach((team, names) ->
            desc.append(String.join("/", names)).append(" on ").append(team).append("; "));
        String key = RaceEvent.EventType.LAST_STAND + ":" + lastGameId;
        EventDraft d = new EventDraft(RaceEvent.EventType.LAST_STAND, lastGameId, desc.toString());
        d.userIds.addAll(userIds);
        drafts.putIfAbsent(key, d);
    }

    // ── helpers ─────────────────────────────────────────────────

    private void add(Map<String, EventDraft> drafts, RaceEvent.EventType type, int gameId,
                     int userId, String firstName, String line) {
        String key = type + ":" + gameId;
        EventDraft d = drafts.computeIfAbsent(key, k -> new EventDraft(type, gameId, line));
        if (!d.userIds.contains(userId)) {
            d.userIds.add(userId);
            if (!d.description.toString().equals(line)) {
                d.description.append(" Also affected: ").append(firstName).append(".");
            }
        }
    }

    static boolean isFinal(String status) {
        return "STATUS_FINAL".equals(status) || "Final".equals(status) || "F/OT".equals(status);
    }

    static boolean isLive(String status) {
        return "STATUS_IN_PROGRESS".equals(status) || "STATUS_HALFTIME".equals(status)
                || "STATUS_END_PERIOD".equals(status) || "In Progress".equals(status);
    }

    /** game.period is a numeric string; 0 when null/unparseable. */
    static int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** displayClock is "M:SS"; -1 when unknown. */
    static int parseClockSeconds(String clock) {
        if (clock == null || !clock.contains(":")) return -1;
        try {
            String[] parts = clock.trim().split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Mutable accumulator while aggregating users per (type, game). */
    private static class EventDraft {
        final RaceEvent.EventType type;
        final int gameId;
        final List<Integer> userIds = new ArrayList<>();
        final StringBuilder description;

        EventDraft(RaceEvent.EventType type, int gameId, String description) {
            this.type = type;
            this.gameId = gameId;
            this.description = new StringBuilder(description);
        }
    }
}
