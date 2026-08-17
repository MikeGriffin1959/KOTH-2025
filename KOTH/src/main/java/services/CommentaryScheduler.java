package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import helpers.SmsPreferencesDAO;
import helpers.SqlConnectorCommentaryTable;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksPriceTable;
import model.Game;
import model.PicksPrice;
import model.RaceEvent;
import model.SmsNotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Commentary scheduler — the first @Scheduled background job in KOTH
 * (@EnableScheduling already lives in config/AppConfig).
 *
 * M2: every commentary.scheduler.tickMs (60s default), fire the Week Recap
 *     once when the week's last game goes final.
 * M3: during the game window, refresh live scores from ESPN and run the
 *     EventDetector; a second 30s tick adds fidelity during two-minute drills
 *     (Q4/OT, clock <= 2:00) for LATE_DRAMA, per design §7.
 *
 * Idle ticks are cheap: one picksprice read + one game-table read. All
 * generation is deduped against the commentary table, so overlapping ticks
 * (60s + 30s share Spring's single-threaded scheduler anyway) cannot
 * double-generate.
 */
@Service
public class CommentaryScheduler {

    @Autowired
    private SqlConnectorPicksPriceTable picksPriceTable;

    @Autowired
    private SqlConnectorGameTable gameTable;

    @Autowired
    private SqlConnectorCommentaryTable commentaryTable;

    @Autowired
    private CommentaryService commentaryService;

    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;

    @Autowired
    private EventDetector eventDetector;

    @Autowired
    private NFLGameFetcherService nflGameFetcherService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private SmsPreferencesDAO smsPreferencesDAO;

    @org.springframework.beans.factory.annotation.Value("${app.base.url:}")
    private String appBaseUrl;

    @Scheduled(fixedRateString = "${commentary.scheduler.tickMs:60000}")
    public void tick() {
        try {
            int season = nflSeasonCalculator.getCurrentNFLSeason();

            // Guard 1: commentary must be enabled for the season (cheap read)
            List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
            if (prices.isEmpty() || !prices.get(0).isCommentaryEnabled()) {
                return;
            }
            PicksPrice cfg = prices.get(0);

            int currentWeek = nflSeasonCalculator.getCurrentNFLWeekNumber();
            if (currentWeek < 1) {
                return; // offseason / pre-week-1
            }

            // M3: live events — refresh scores + detect during the game window.
            List<Map<String, Object>> gameStates = commentaryTable.getWeekGameStates(season, currentWeek);
            if (isGameWindow(gameStates)) {
                refreshLiveScores();
                runEventDetection(season, currentWeek, cfg);
            }

            // M4: Weekly Preview — once, on the configured preview day (>= 9am ET),
            // while the week still has unplayed games.
            if (isPreviewTime(cfg) && !gameStates.isEmpty()
                    && !gameTable.isWeekComplete(season, currentWeek)
                    && !commentaryTable.hasCommentary(season, currentWeek, "PREVIEW")) {
                System.out.println("CommentaryScheduler.tick - preview day, generating weekly preview");
                commentaryService.generateWeeklyPreview(season, currentWeek);
            }

            // M4: Kickoff Reveal — per kickoff window, only when picks are masked.
            if (cfg.isMaskPicks()) {
                runKickoffReveals(season, currentWeek, cfg, gameStates);
            }

            // M2: Week Recap — once, after the last game of the week is final.
            // Also look at the previous week so a recap is never missed if the
            // calculator rolls right after MNF (hasCommentary = idempotent).
            for (int week = Math.max(1, currentWeek - 1); week <= currentWeek; week++) {
                if (!gameTable.isWeekComplete(season, week)) {
                    continue;
                }
                if (commentaryTable.hasCommentary(season, week, "RECAP")) {
                    continue; // already generated
                }
                System.out.println("CommentaryScheduler.tick - week " + week + " is complete, generating recap");
                boolean generated = commentaryService.generateWeekRecap(season, week);
                System.out.println("CommentaryScheduler.tick - recap " + (generated ? "generated" : "skipped/failed")
                        + " for season " + season + " week " + week);
            }

        } catch (Exception e) {
            // Never let one bad tick kill the schedule
            System.err.println("CommentaryScheduler.tick - error (will retry next tick): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tighter 30s cadence for LATE_DRAMA fidelity (design §7): no-ops unless a
     * live game is inside the two-minute warning of Q4/OT.
     */
    @Scheduled(fixedRate = 30000)
    public void tickLateDrama() {
        try {
            int season = nflSeasonCalculator.getCurrentNFLSeason();
            List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
            if (prices.isEmpty() || !prices.get(0).isCommentaryEnabled()) {
                return;
            }
            int week = nflSeasonCalculator.getCurrentNFLWeekNumber();
            if (week < 1) return;

            List<Map<String, Object>> gameStates = commentaryTable.getWeekGameStates(season, week);
            if (!anyTwoMinuteDrill(gameStates)) {
                return;
            }
            System.out.println("CommentaryScheduler.tickLateDrama - two-minute drill in progress, tight check");
            refreshLiveScores();
            runEventDetection(season, week, prices.get(0));
        } catch (Exception e) {
            System.err.println("CommentaryScheduler.tickLateDrama - error: " + e.getMessage());
        }
    }

    /**
     * Picks reminders — texts players who are still alive but have no picks
     * for the current week, before the week's first kickoff. Two windows:
     * morning-of (9am ET on kickoff day) and last call (2 hours before
     * kickoff). Each window fires once per week via claim(); recipients are
     * phone-verified, opted in to Picks Reminder, and have zero picks rows.
     * Runs independently of commentaryEnabled — this is an SMS feature.
     */
    @Scheduled(fixedRate = 300000)
    public void tickPicksReminders() {
        try {
            int season = nflSeasonCalculator.getCurrentNFLSeason();
            int week = nflSeasonCalculator.getCurrentNFLWeekNumber();
            if (week < 1) return;

            List<Map<String, Object>> gameStates = commentaryTable.getWeekGameStates(season, week);
            Instant firstKickoff = earliestKickoff(gameStates);
            if (firstKickoff == null) return;

            Instant now = Instant.now();
            if (!now.isBefore(firstKickoff)) {
                return; // week already underway — reminder windows are closed
            }

            java.time.ZoneId et = java.time.ZoneId.of("America/New_York");
            Instant morningStart = firstKickoff.atZone(et).toLocalDate()
                    .atTime(9, 0).atZone(et).toInstant();
            Instant lastCallStart = firstKickoff.minusSeconds(2 * 60 * 60);

            if (!now.isBefore(lastCallStart)) {
                if (smsPreferencesDAO.claim(season, week, "PICKS_REMINDER", "lastcall")) {
                    sendPicksReminders(season, week,
                        "KOTH: Last call! Week " + week + " kicks off at "
                        + kickoffTimeEt(firstKickoff) + " ET (about 2 hours) and you haven't made your picks."
                        + appLinkSuffix());
                }
            } else if (!now.isBefore(morningStart)) {
                if (smsPreferencesDAO.claim(season, week, "PICKS_REMINDER", "morning")) {
                    sendPicksReminders(season, week,
                        "KOTH: Week " + week + " kicks off " + kickoffLabel(firstKickoff)
                        + " ET and you haven't made your picks yet."
                        + appLinkSuffix());
                }
            }
        } catch (Exception e) {
            System.err.println("CommentaryScheduler.tickPicksReminders - error (will retry next tick): "
                    + e.getMessage());
        }
    }

    private void sendPicksReminders(int season, int week, String message) {
        List<SmsPreferencesDAO.UserPhone> recipients =
                smsPreferencesDAO.getPicksReminderRecipients(season, week);
        int sent = 0;
        for (SmsPreferencesDAO.UserPhone r : recipients) {
            if (smsService.sendNotification(r.userId, r.phoneNumber,
                    SmsNotificationType.PICKS_REMINDER, message)) {
                sent++;
            }
        }
        System.out.println("CommentaryScheduler.tickPicksReminders - week " + week
                + ": " + sent + "/" + recipients.size() + " reminder(s) sent");
    }

    private Instant earliestKickoff(List<Map<String, Object>> gameStates) {
        Instant earliest = null;
        for (Map<String, Object> g : gameStates) {
            Instant kickoff = parseKickoff((String) g.get("date"));
            if (kickoff != null && (earliest == null || kickoff.isBefore(earliest))) {
                earliest = kickoff;
            }
        }
        return earliest;
    }

    /** Kickoff time-of-day in ET, e.g. "8:20 PM". */
    private String kickoffTimeEt(Instant kickoff) {
        return kickoff.atZone(java.time.ZoneId.of("America/New_York"))
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
    }

    private String appLinkSuffix() {
        return (appBaseUrl == null || appBaseUrl.isEmpty()) ? "" : " " + appBaseUrl;
    }

    // ── M4 internals ───────────────────────────────────────────

    /** Preview fires on picksprice.previewDayOfWeek (java.time convention,
     *  1=Mon..7=Sun), any tick from 9am ET onward. */
    private boolean isPreviewTime(PicksPrice cfg) {
        java.time.ZonedDateTime nowEt = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York"));
        return nowEt.getDayOfWeek().getValue() == cfg.getPreviewDayOfWeek()
                && nowEt.getHour() >= 9;
    }

    /**
     * Kickoff windows are derived from the actual schedule: games sharing the
     * same kickoff instant form a window (Thu night, Sun 1:00, Sun 4:05/4:25,
     * SNF, MNF fall out naturally). A reveal fires once per window when its
     * kickoff time has passed — but only within 90 minutes of kickoff, so a
     * mid-season deploy doesn't backfill reveals for long-past windows.
     * Dedupe: streamType=REVEAL rows carry gameId = min gameId of the window
     * (eventType null), checked via idx_dedupe.
     */
    private void runKickoffReveals(int season, int week, PicksPrice cfg, List<Map<String, Object>> gameStates) {
        Instant now = Instant.now();
        Map<String, List<Map<String, Object>>> windows = new java.util.TreeMap<>();
        for (Map<String, Object> g : gameStates) {
            String date = (String) g.get("date");
            if (date == null) continue;
            windows.computeIfAbsent(date, k -> new java.util.ArrayList<>()).add(g);
        }
        for (Map.Entry<String, List<Map<String, Object>>> w : windows.entrySet()) {
            Instant kickoff = parseKickoff(w.getKey());
            if (kickoff == null || kickoff.isAfter(now)) continue;               // not kicked off yet
            if (kickoff.isBefore(now.minusSeconds(90 * 60))) continue;           // too old — no backfill
            int minGameId = Integer.MAX_VALUE;
            for (Map<String, Object> g : w.getValue()) {
                minGameId = Math.min(minGameId, (Integer) g.get("gameId"));
            }
            if (commentaryTable.findByDedupeKey(season, cfg.getKothSeason(), week, minGameId, null)) {
                continue; // this window's reveal already generated
            }
            String label = kickoffLabel(kickoff);
            System.out.println("CommentaryScheduler - kickoff window " + w.getKey() + " opened, generating reveal");
            boolean generated = commentaryService.generateKickoffReveal(season, week, label, w.getValue());
            System.out.println("CommentaryScheduler - reveal " + (generated ? "generated" : "skipped/failed")
                    + " for window " + w.getKey());
        }
    }

    /** Friendly window label in ET, e.g. "Sunday 1:00 PM". */
    private String kickoffLabel(Instant kickoff) {
        java.time.ZonedDateTime et = kickoff.atZone(java.time.ZoneId.of("America/New_York"));
        return et.format(java.time.format.DateTimeFormatter.ofPattern("EEEE h:mm a"));
    }

    // ── M3 internals ───────────────────────────────────────────

    /** Detect events and generate commentary for any not yet covered (idx_dedupe). */
    private void runEventDetection(int season, int week, PicksPrice cfg) {
        List<RaceEvent> events = eventDetector.detect(season, week, cfg.getSnarkLevel());
        for (RaceEvent ev : events) {
            // Once per (week, game, eventType)
            if (commentaryTable.findByDedupeKey(season, cfg.getKothSeason(), week, ev.getGameId(), ev.getType().name())) {
                continue;
            }
            // GAME_FINAL_WIN is suppressed if LATE_DRAMA already covered the game
            if (ev.getType() == RaceEvent.EventType.GAME_FINAL_WIN
                    && commentaryTable.findByDedupeKey(season, cfg.getKothSeason(), week, ev.getGameId(),
                            RaceEvent.EventType.LATE_DRAMA.name())) {
                continue;
            }
            boolean generated = commentaryService.generateEventCommentary(season, week, ev);
            System.out.println("CommentaryScheduler - event " + ev.getType() + " gameId=" + ev.getGameId()
                    + " -> " + (generated ? "generated" : "skipped/failed"));
        }
    }

    /** Pull fresh scores from ESPN into the game table (same path HomeServlet uses). */
    private void refreshLiveScores() {
        try {
            List<Game> games = nflGameFetcherService.fetchCurrentWeekGames();
            gameTable.updateGameTableMinimal(games);
        } catch (Exception e) {
            // Non-fatal: detection still runs against the last-known DB state
            System.err.println("CommentaryScheduler.refreshLiveScores - ESPN refresh failed: " + e.getMessage());
        }
    }

    /**
     * The game window is open when any game is live, or a scheduled game's
     * kickoff time (ISO-8601 UTC string) has passed but the DB hasn't seen it
     * start yet (i.e. scores need refreshing).
     */
    private boolean isGameWindow(List<Map<String, Object>> gameStates) {
        Instant now = Instant.now();
        for (Map<String, Object> g : gameStates) {
            String status = (String) g.get("status");
            if (EventDetector.isLive(status)) {
                return true;
            }
            if ("STATUS_SCHEDULED".equals(status) || "Scheduled".equals(status)) {
                Instant kickoff = parseKickoff((String) g.get("date"));
                if (kickoff != null && !kickoff.isAfter(now)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Any live game inside 2:00 of Q4 or OT? */
    private boolean anyTwoMinuteDrill(List<Map<String, Object>> gameStates) {
        for (Map<String, Object> g : gameStates) {
            if (!EventDetector.isLive((String) g.get("status"))) continue;
            int periodNum = EventDetector.parseIntSafe((String) g.get("period"));
            int clock = EventDetector.parseClockSeconds((String) g.get("displayClock"));
            if (periodNum >= 4 && clock >= 0 && clock <= 120) {
                return true;
            }
        }
        return false;
    }

    /**
     * game.date is an ISO-8601 UTC string like "2026-09-10T00:20Z" — note it
     * carries minutes only. Instant.parse requires seconds, so normalize the
     * minutes-only form to "...T00:20:00Z" before parsing.
     */
    private Instant parseKickoff(String date) {
        if (date == null || date.isEmpty()) return null;
        String d = date.trim();
        if (d.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z")) {
            d = d.substring(0, d.length() - 1) + ":00Z";
        }
        try {
            return Instant.parse(d);
        } catch (Exception e) {
            System.err.println("CommentaryScheduler.parseKickoff - unparseable date '" + date + "'");
            return null;
        }
    }
}
