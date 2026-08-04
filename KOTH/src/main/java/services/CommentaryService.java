package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import helpers.SqlConnectorCommentaryTable;
import helpers.SqlConnectorPicksPriceTable;
import model.Commentary;
import model.PicksPrice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Commentary engine for KOTH (NFL survivor pool). Parallels GolferFest's
 * CommentaryService but reshaped for survivor's binary, weekly, elimination
 * dynamics — see KOTH-Commentary-Design.md.
 *
 * M1 scope (Foundation): the shared system-prompt builder (production-ready,
 * with the ELIMINATION sympathetic-tone calibration baked in), the Anthropic
 * /v1/messages client (HTTP plumbing mirrors services.KothTriageClient, but
 * also returns token counts so they can be persisted), persistence to the
 * Commentary table, and the daily cost cap. Event/stream generation and the
 * CommentaryScheduler land in M2+.
 */
@Service
public class CommentaryService {

    // ── Configuration ──────────────────────────────────────────
    // Real key is supplied via the gitignored secrets file (spring.config.import);
    // application.properties only carries a placeholder default.

    @Value("${commentary.api.key:}")
    private String apiKey;

    @Value("${commentary.api.model:claude-sonnet-4-6}")
    private String apiModel;

    @Value("${commentary.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${commentary.api.max-tokens:1024}")
    private int maxTokens;

    @Value("${commentary.api.dailyCapUsd:5.00}")
    private double dailyCapUsd;

    @Value("${app.base.url:http://localhost:8081/KOTH}")
    private String appBaseUrl;

    // Sonnet 4.6 pricing per the design doc / Claude API reference (verified):
    // $3.00 / Mtok input, $15.00 / Mtok output. Update these together with
    // commentary.api.model if the model ever changes.
    private static final double INPUT_USD_PER_MTOK = 3.00;
    private static final double OUTPUT_USD_PER_MTOK = 15.00;

    // ── Dependencies (field @Autowired — matches the KOTH convention) ──

    @Autowired
    private SqlConnectorCommentaryTable commentaryTable;

    @Autowired
    private SqlConnectorPicksPriceTable picksPriceTable;

    @Autowired
    private SmsService smsService; // commentary → SMS push

    @Autowired
    private helpers.SmsPreferencesDAO smsPreferencesDAO; // claim() dedupe

    @Autowired
    private helpers.SqlConnectorDossierTable dossierTable; // M5 personality layer

    // ════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════

    /** Result of the admin "Fire Test Commentary" path — serialized to JSON by the servlet. */
    public static class TestResult {
        public final boolean success;
        public final String message;
        public final String body;          // generated blurb (null on failure)
        public final Integer commentaryId; // null on failure

        public TestResult(boolean success, String message, String body, Integer commentaryId) {
            this.success = success;
            this.message = message;
            this.body = body;
            this.commentaryId = commentaryId;
        }

        static TestResult fail(String message) {
            return new TestResult(false, message, null, null);
        }
    }

    /**
     * Run a hardcoded prompt end-to-end and persist the result with streamType=TEST.
     * Used by the commissioner "Fire Test Commentary" button (M1 only).
     *
     * Guards, in order: config exists → commentary enabled → API key configured →
     * daily cost cap not exceeded. Only then is the API called.
     */
    public TestResult generateTestCommentary(int season, int week) {
        System.out.println("CommentaryService.generateTestCommentary called for season=" + season + ", week=" + week);

        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        if (prices.isEmpty()) {
            return TestResult.fail("No pick price configuration found for season " + season);
        }
        PicksPrice cfg = prices.get(0);

        if (!cfg.isCommentaryEnabled()) {
            // Per the verification checklist: do NOT call the API when disabled.
            return TestResult.fail("Commentary is disabled");
        }

        if (!isConfigured()) {
            return TestResult.fail("Commentary API key not configured (set commentary.api.key in the secrets file)");
        }

        if (dailyCostCapExceeded()) {
            return TestResult.fail("Daily commentary cost cap ($" + String.format("%.2f", dailyCapUsd) + ") reached");
        }

        int snarkLevel = cfg.getSnarkLevel();
        String systemPrompt = buildSystemPrompt(snarkLevel, "TEST");
        // Dossiers ride along so the commissioner's test button demonstrates
        // the personality layer (edit a dossier -> fire test -> hear it).
        String userPrompt = poolContextBlock(season) + dossierBlock(season, null)
                + buildTestPrompt(season, week, snarkLevel);

        ClaudeResult result = callClaudeApi(systemPrompt, userPrompt);
        if (result == null || result.text == null || result.text.isEmpty()) {
            return TestResult.fail("Empty or failed response from the Claude API (check server logs)");
        }

        Commentary commentary = new Commentary();
        commentary.setSeason(season);
        commentary.setKothSeason(cfg.getKothSeason());
        commentary.setWeek(week);
        commentary.setStreamType("TEST");
        commentary.setEventType(null);
        commentary.setAffectedUserIds(null);
        commentary.setGameId(null);
        commentary.setSnarkLevel(snarkLevel);
        commentary.setPromptTokens(result.promptTokens);
        commentary.setResponseTokens(result.responseTokens);
        commentary.setBody(result.text);

        boolean inserted = commentaryTable.insert(commentary);
        if (!inserted) {
            return TestResult.fail("Generated commentary but failed to persist it (check server logs)");
        }

        // SMS push (no-op for TEST, but proves the wiring all streams flow through)
        sendCommentarySms(commentary, cfg);

        System.out.println("CommentaryService.generateTestCommentary - persisted commentaryId=" + commentary.getCommentaryId()
                + " (" + result.promptTokens + " in / " + result.responseTokens + " out tokens)");
        return new TestResult(true, "Test commentary generated", result.text, commentary.getCommentaryId());
    }

    // ════════════════════════════════════════════════════════════
    // COMMENTARY → SMS (mirrors GolferFest's CommentaryService SMS wiring)
    // ════════════════════════════════════════════════════════════

    /**
     * Push a just-persisted commentary to opted-in players by text. Every
     * generation path (M2+ recaps/events included) should call this after
     * inserting the row. Behavior:
     *  - gated on the season's commentaryNotifications toggle (commissioner card)
     *  - RECAP  → WEEK_RECAP recipients (once per week, claim()-deduped)
     *  - EVENT  → COMMENTARY_EVENT recipients (deduped per event/game)
     *  - TEST / PREVIEW / REVEAL → no SMS
     *  - non-fatal: SMS failure never breaks commentary generation
     */
    private void sendCommentarySms(Commentary commentary, PicksPrice cfg) {
        try {
            if (cfg == null || !cfg.isCommentaryNotifications() || !smsService.isConfigured()) {
                return;
            }
            int season = commentary.getSeason();
            int week = commentary.getWeek();

            switch (commentary.getStreamType()) {
                case "RECAP": {
                    if (smsPreferencesDAO.claim(season, week, "WEEK_RECAP", "recap")) {
                        String body = "[KOTH] Week " + week + " recap: "
                                + smsSnippet(commentary.getBody(), 360)
                                + " Full story: " + appBaseUrl + "/CommentaryServlet";
                        smsService.broadcastToSeason(model.SmsNotificationType.WEEK_RECAP,
                                season, false, body);
                    }
                    break;
                }
                case "EVENT": {
                    String refKey = (commentary.getEventType() != null ? commentary.getEventType() : "EVENT")
                            + ":" + (commentary.getGameId() != null ? commentary.getGameId() : 0)
                            + ":" + (commentary.getAffectedUserIds() != null ? commentary.getAffectedUserIds() : "");
                    if (smsPreferencesDAO.claim(season, week, "COMMENTARY_EVENT", refKey)) {
                        String body = "[KOTH] " + smsSnippet(commentary.getBody(), 360)
                                + " Full story: " + appBaseUrl + "/CommentaryServlet";
                        smsService.broadcastToSeason(model.SmsNotificationType.COMMENTARY_EVENT,
                                season, false, body);
                    }
                    break;
                }
                default:
                    // TEST, PREVIEW, REVEAL: page-only, no texts
                    break;
            }
        } catch (Exception smsEx) {
            System.err.println("CommentaryService.sendCommentarySms - SMS failed (non-fatal): " + smsEx.getMessage());
        }
    }

    /**
     * Collapse whitespace and truncate commentary at a word boundary so it fits
     * in a reasonable SMS (a few segments at most).
     */
    private String smsSnippet(String text, int maxChars) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= maxChars) return flat;
        int cut = flat.lastIndexOf(' ', maxChars);
        if (cut < maxChars / 2) cut = maxChars;
        return flat.substring(0, cut) + "...";
    }

    /**
     * Generate the Week Recap (M2): looks back at the week's outcomes, who
     * survived and who went home, then persists it (streamType=RECAP). The
     * persisted row flows through sendCommentarySms → WEEK_RECAP texts when
     * notifications are enabled. Returns true if a recap was generated.
     *
     * Caller (CommentaryScheduler) is responsible for the once-per-week dedupe;
     * this method re-checks the cheap guards so it is safe to call directly.
     */
    public boolean generateWeekRecap(int season, int week) {
        System.out.println("CommentaryService.generateWeekRecap called for season=" + season + ", week=" + week);

        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        if (prices.isEmpty()) return false;
        PicksPrice cfg = prices.get(0);
        if (!cfg.isCommentaryEnabled() || !isConfigured() || dailyCostCapExceeded()) {
            return false;
        }

        List<java.util.Map<String, Object>> outcomes = commentaryTable.getWeekPickOutcomes(season, week);
        List<java.util.Map<String, Object>> standings = commentaryTable.getSeasonStandings(season);
        if (outcomes.isEmpty()) {
            System.out.println("CommentaryService.generateWeekRecap - no picks for week " + week + ", skipping");
            return false;
        }

        long aliveCount = standings.stream()
                .filter(s -> ((Integer) s.get("remaining")) > 0)
                .count();
        boolean seasonFinale = week >= 22 || aliveCount <= 1;

        int snarkLevel = cfg.getSnarkLevel();
        String systemPrompt = buildSystemPrompt(snarkLevel, "RECAP");
        String userPrompt = poolContextBlock(season) + dossierBlock(season, null)
                + buildRecapPrompt(season, week, outcomes, standings, aliveCount, seasonFinale);

        ClaudeResult result = callClaudeApi(systemPrompt, userPrompt);
        if (result == null || result.text == null || result.text.isEmpty()) {
            System.err.println("CommentaryService.generateWeekRecap - empty/failed API response");
            return false;
        }

        Commentary commentary = new Commentary();
        commentary.setSeason(season);
        commentary.setKothSeason(cfg.getKothSeason());
        commentary.setWeek(week);
        commentary.setStreamType("RECAP");
        commentary.setSnarkLevel(snarkLevel);
        commentary.setPromptTokens(result.promptTokens);
        commentary.setResponseTokens(result.responseTokens);
        commentary.setBody(result.text);

        if (!commentaryTable.insert(commentary)) {
            System.err.println("CommentaryService.generateWeekRecap - persist failed");
            return false;
        }

        sendCommentarySms(commentary, cfg);
        System.out.println("CommentaryService.generateWeekRecap - persisted commentaryId=" + commentary.getCommentaryId()
                + (seasonFinale ? " (SEASON FINALE)" : ""));
        return true;
    }

    /**
     * Generate live event commentary for one detected RaceEvent (M3) and
     * persist it (streamType=EVENT). Dedupe against idx_dedupe is the
     * scheduler's job (findByDedupeKey before calling). The persisted row
     * flows through sendCommentarySms → COMMENTARY_EVENT texts.
     */
    public boolean generateEventCommentary(int season, int week, model.RaceEvent event) {
        System.out.println("CommentaryService.generateEventCommentary: " + event);

        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        if (prices.isEmpty()) return false;
        PicksPrice cfg = prices.get(0);
        if (!cfg.isCommentaryEnabled() || !isConfigured() || dailyCostCapExceeded()) {
            return false;
        }

        int snarkLevel = cfg.getSnarkLevel();
        String systemPrompt = buildSystemPrompt(snarkLevel, "EVENT");
        // Inject only the affected users' dossiers — sensitivities here are
        // load-bearing (ELIMINATION tone modulation, design §M5)
        String userPrompt = poolContextBlock(season)
                + dossierBlock(season, event.getAffectedUserIds())
                + buildEventPrompt(season, week, event);

        ClaudeResult result = callClaudeApi(systemPrompt, userPrompt);
        if (result == null || result.text == null || result.text.isEmpty()) {
            System.err.println("CommentaryService.generateEventCommentary - empty/failed API response");
            return false;
        }

        StringBuilder ids = new StringBuilder();
        for (Integer id : event.getAffectedUserIds()) {
            if (ids.length() > 0) ids.append(",");
            ids.append(id);
        }

        Commentary commentary = new Commentary();
        commentary.setSeason(season);
        commentary.setKothSeason(cfg.getKothSeason());
        commentary.setWeek(week);
        commentary.setStreamType("EVENT");
        commentary.setEventType(event.getType().name());
        commentary.setAffectedUserIds(ids.toString());
        commentary.setGameId(event.getGameId());
        commentary.setSnarkLevel(snarkLevel);
        commentary.setPromptTokens(result.promptTokens);
        commentary.setResponseTokens(result.responseTokens);
        commentary.setBody(result.text);

        if (!commentaryTable.insert(commentary)) {
            System.err.println("CommentaryService.generateEventCommentary - persist failed");
            return false;
        }

        sendCommentarySms(commentary, cfg);
        System.out.println("CommentaryService.generateEventCommentary - persisted commentaryId="
                + commentary.getCommentaryId() + " (" + event.getType() + ")");
        return true;
    }

    /**
     * Generate the Weekly Preview (M4): sets the stage for the coming week.
     * Masking-aware — when picksprice.maskPicks is on, the prompt carries no
     * pick details. Persists streamType=PREVIEW (page-only; no SMS stream).
     * Caller handles the once-per-week dedupe.
     */
    public boolean generateWeeklyPreview(int season, int week) {
        System.out.println("CommentaryService.generateWeeklyPreview called for season=" + season + ", week=" + week);

        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        if (prices.isEmpty()) return false;
        PicksPrice cfg = prices.get(0);
        if (!cfg.isCommentaryEnabled() || !isConfigured() || dailyCostCapExceeded()) {
            return false;
        }

        List<java.util.Map<String, Object>> games = commentaryTable.getWeekGameStates(season, week);
        if (games.isEmpty()) {
            System.out.println("CommentaryService.generateWeeklyPreview - no games for week " + week + ", skipping");
            return false;
        }
        List<java.util.Map<String, Object>> standings = commentaryTable.getSeasonStandings(season);
        boolean masked = cfg.isMaskPicks();
        List<java.util.Map<String, Object>> picks =
                masked ? null : commentaryTable.getWeekPicksWithGameState(season, week);

        int snarkLevel = cfg.getSnarkLevel();
        String systemPrompt = buildSystemPrompt(snarkLevel, "PREVIEW");
        String userPrompt = poolContextBlock(season) + dossierBlock(season, null)
                + buildPreviewPrompt(season, week, masked, games, standings, picks);

        ClaudeResult result = callClaudeApi(systemPrompt, userPrompt);
        if (result == null || result.text == null || result.text.isEmpty()) {
            System.err.println("CommentaryService.generateWeeklyPreview - empty/failed API response");
            return false;
        }

        Commentary commentary = new Commentary();
        commentary.setSeason(season);
        commentary.setKothSeason(cfg.getKothSeason());
        commentary.setWeek(week);
        commentary.setStreamType("PREVIEW");
        commentary.setSnarkLevel(snarkLevel);
        commentary.setPromptTokens(result.promptTokens);
        commentary.setResponseTokens(result.responseTokens);
        commentary.setBody(result.text);

        if (!commentaryTable.insert(commentary)) return false;
        System.out.println("CommentaryService.generateWeeklyPreview - persisted commentaryId=" + commentary.getCommentaryId());
        return true;
    }

    /**
     * Generate the Kickoff Reveal (M4) for one kickoff window — only meaningful
     * when picks are masked (the caller gates on cfg.isMaskPicks()). Persists
     * streamType=REVEAL with gameId = the window's representative (minimum)
     * gameId, which is also the dedupe key the scheduler checks.
     */
    public boolean generateKickoffReveal(int season, int week, String windowLabel,
                                         List<java.util.Map<String, Object>> windowGames) {
        System.out.println("CommentaryService.generateKickoffReveal called for season=" + season
                + ", week=" + week + ", window=" + windowLabel);

        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        if (prices.isEmpty()) return false;
        PicksPrice cfg = prices.get(0);
        if (!cfg.isCommentaryEnabled() || !cfg.isMaskPicks() || !isConfigured() || dailyCostCapExceeded()) {
            return false;
        }
        if (windowGames == null || windowGames.isEmpty()) return false;

        // Picks riding on this window's games
        java.util.Set<Integer> windowGameIds = new java.util.HashSet<>();
        int minGameId = Integer.MAX_VALUE;
        for (java.util.Map<String, Object> g : windowGames) {
            int id = (Integer) g.get("gameId");
            windowGameIds.add(id);
            if (id < minGameId) minGameId = id;
        }
        List<java.util.Map<String, Object>> windowPicks = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> p : commentaryTable.getWeekPicksWithGameState(season, week)) {
            if (windowGameIds.contains((Integer) p.get("gameId"))) {
                windowPicks.add(p);
            }
        }

        java.util.Set<Integer> revealUserIds = new java.util.HashSet<>();
        for (java.util.Map<String, Object> p : windowPicks) {
            revealUserIds.add((Integer) p.get("idUser"));
        }

        int snarkLevel = cfg.getSnarkLevel();
        String systemPrompt = buildSystemPrompt(snarkLevel, "REVEAL");
        String userPrompt = poolContextBlock(season)
                + dossierBlock(season, revealUserIds.isEmpty() ? null : revealUserIds)
                + buildKickoffRevealPrompt(season, week, windowLabel, windowGames, windowPicks);

        ClaudeResult result = callClaudeApi(systemPrompt, userPrompt);
        if (result == null || result.text == null || result.text.isEmpty()) {
            System.err.println("CommentaryService.generateKickoffReveal - empty/failed API response");
            return false;
        }

        Commentary commentary = new Commentary();
        commentary.setSeason(season);
        commentary.setKothSeason(cfg.getKothSeason());
        commentary.setWeek(week);
        commentary.setStreamType("REVEAL");
        commentary.setGameId(minGameId);
        commentary.setSnarkLevel(snarkLevel);
        commentary.setPromptTokens(result.promptTokens);
        commentary.setResponseTokens(result.responseTokens);
        commentary.setBody(result.text);

        if (!commentaryTable.insert(commentary)) return false;
        System.out.println("CommentaryService.generateKickoffReveal - persisted commentaryId=" + commentary.getCommentaryId());
        return true;
    }

    /**
     * True if today's accumulated token spend (across all streams) is at or above
     * the configured daily cap, applying current Sonnet 4.6 pricing. Used as a
     * circuit-breaker before any generation call. Fails open (returns false) only
     * via the DAO's safe-zero behavior on a DB error.
     */
    public boolean dailyCostCapExceeded() {
        SqlConnectorCommentaryTable.TokenTotals totals = commentaryTable.sumTokensToday();
        double cost = (totals.promptTokens / 1_000_000.0) * INPUT_USD_PER_MTOK
                + (totals.responseTokens / 1_000_000.0) * OUTPUT_USD_PER_MTOK;
        boolean exceeded = cost >= dailyCapUsd;
        System.out.printf("CommentaryService.dailyCostCapExceeded - today: %d in / %d out tokens = $%.4f (cap $%.2f) -> %b%n",
                totals.promptTokens, totals.responseTokens, cost, dailyCapUsd, exceeded);
        return exceeded;
    }

    /** True if an API key is present (and not the placeholder). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !"placeholder".equalsIgnoreCase(apiKey);
    }

    // ════════════════════════════════════════════════════════════
    // PROMPT ENGINEERING
    // ════════════════════════════════════════════════════════════

    /**
     * The shared system prompt: commentator persona, snark calibration (examples
     * at 0/5/10), the tie-equals-loss rule, and — load-bearing — the ELIMINATION
     * sympathetic-tone rule with the snark 3/6/9 calibration from design §4.3
     * baked in. Production-ready from M1 even though M1 only fires the TEST stream;
     * the per-stream output formatting (preview/reveal/event/recap) is layered on
     * in later milestones.
     */
    private String buildSystemPrompt(int snarkLevel, String streamType) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are the AI color commentator for KOTH, a private NFL \"King of the Hill\" survivor pool ");
        sb.append("run by Griff for a group of family and friends. Each week, every surviving player picks ONE ");
        sb.append("NFL team to win; if that team wins, the pick survives, and if it loses OR ties, the pick is ");
        sb.append("eliminated. The last survivor(s) take the pot. You know the players and bring the room to life ");
        sb.append("with a running, snark-calibrated narrative.\n\n");

        // Snark calibration
        sb.append("SNARK LEVEL: ").append(snarkLevel).append("/10. Calibrate your voice on this scale:\n");
        sb.append("- 0:  Straight ESPN broadcast. Professional, encouraging, measured. No teasing.\n");
        sb.append("- 5:  Your witty friend who knows football. Clever callbacks, playful ribbing of questionable picks, ");
        sb.append("mixed with genuine read of the matchups.\n");
        sb.append("- 10: Full roast. Savage, brutally funny, every shaky pick is a punchline — but never mean-spirited ");
        sb.append("or personal beyond the football. The humor comes from the absurdity of the situation.\n");
        sb.append("Scale your tone smoothly between these anchors for the level above.\n\n");

        // The hard rules
        sb.append("ABSOLUTE RULES:\n");
        sb.append("1. A TIE IS A LOSS. Never frame a tie as a hopeful draw or a near-miss — a tied pick is eliminated, ");
        sb.append("full stop.\n");
        sb.append("2. ELIMINATION IS SYMPATHETIC AT EVERY SNARK LEVEL. When a player is eliminated, their pool run is ");
        sb.append("over for the year — treat them with affection. Teasing flavor may scale up with snark, but the ");
        sb.append("underlying tone stays gentle and warm. NEVER use a \"Woof!\" cadence or mockery on an elimination ");
        sb.append("(that signature belongs to GolferFest, not KOTH). Calibration for an eliminated player named Mike:\n");
        sb.append("   - Snark 3: \"Tough one for Mike — the Cowboys couldn't pull it out. That ends his run for the year. ");
        sb.append("Hard luck.\"\n");
        sb.append("   - Snark 6: \"Mike's Cowboys came up short, and that's the season for him. Sting's gonna last a while, ");
        sb.append("but there's always next year.\"\n");
        sb.append("   - Snark 9: \"Mike, my friend — the Cowboys did Mike dirty. Season over, head held high. We'll see you ");
        sb.append("at the draft party.\"\n");
        sb.append("   At every level Mike is treated with affection. (TROUBLE and other still-alive moments carry no such ");
        sb.append("restriction — a struggling-but-alive player is fair game for full snark.)\n");
        sb.append("3. Refer to players by their display name / first name. Never invent stats, scores, or outcomes beyond ");
        sb.append("the data you are given.\n");
        sb.append("4. If a PLAYER DOSSIERS section is provided, weave personality, running jokes, and rivalries in ");
        sb.append("naturally — like a sportscaster who knows the backstory, not a form letter. Don't force every ");
        sb.append("detail into every blurb. A player's SENSITIVITIES are a CEILING that overrides the snark level ");
        sb.append("for that player: pull punches exactly as flagged, especially on eliminations.\n");
        sb.append("5. If a POOL CONTEXT section is provided, treat its lore and tone guidance as house style.\n\n");

        // Per-stream output format
        switch (streamType) {
            case "PREVIEW":
                sb.append("FORMAT: Weekly Preview — set the stage for the week ahead. State of the field, ");
                sb.append("the betting landscape (spreads/totals), who needs what. If picks are provided, ");
                sb.append("tease notable choices; if the data says picks are MASKED, talk matchups and the ");
                sb.append("betting board only — NO specific pick callouts (historical tendencies are fine). ");
                sb.append("3-5 sentences.\n\n");
                break;
            case "REVEAL":
                sb.append("FORMAT: Kickoff Reveal — the masked picks for the games just kicking off are now ");
                sb.append("public. This is the unmask moment: who took what, where the herd went, who went ");
                sb.append("rogue. React with delight or alarm as warranted. 2-3 sentences.\n\n");
                break;
            case "EVENT":
                sb.append("FORMAT: Breaking-news reactive commentary. Something just happened in a live ");
                sb.append("game (or one just went final) and survivor fates moved. React to THIS MOMENT — ");
                sb.append("do not summarize the whole week. Think of a sportscaster calling a highlight: ");
                sb.append("short, punchy, vivid. 1-2 sentences.\n\n");
                break;
            case "RECAP":
                sb.append("FORMAT: Week Recap — look back at the week that just ended and set up the next one. ");
                sb.append("Cover who survived, who lost picks, and any eliminations (sympathetically, per the rules). ");
                sb.append("If the data marks this as the SEASON FINALE, this is the season-ending sendoff: crown the ");
                sb.append("champion (or mourn the wipeout), recap the season arc in a line or two, and close the year out. ");
                sb.append("4-6 sentences.\n\n");
                break;
            case "TEST":
            default:
                // Test/sample blurbs carry their format in the user prompt.
                break;
        }

        sb.append("OUTPUT: Natural prose. No markdown, no bullet points, no headers. Keep it tight and broadcast-ready.\n");

        return sb.toString();
    }

    /**
     * Week Recap user prompt (M2): real outcomes for the week, pick by pick,
     * plus season standings so the model knows who's alive.
     */
    private String buildRecapPrompt(int season, int week,
                                    List<java.util.Map<String, Object>> outcomes,
                                    List<java.util.Map<String, Object>> standings,
                                    long aliveCount, boolean seasonFinale) {
        StringBuilder sb = new StringBuilder();
        sb.append("Season ").append(season).append(", Week ").append(week)
          .append(" is complete. Write the Week ").append(week).append(" recap.\n");
        if (seasonFinale) {
            sb.append("THIS IS THE SEASON FINALE — ");
            sb.append(aliveCount == 1 ? "exactly one survivor remains. Crown the champion.\n"
                    : aliveCount == 0 ? "nobody survived. A full wipeout — mourn accordingly.\n"
                    : "the Super Bowl week recap closes the season.\n");
        }

        sb.append("\n=== THIS WEEK'S PICKS & RESULTS ===\n");
        for (java.util.Map<String, Object> o : outcomes) {
            String selected = (String) o.get("selectedTeam");
            String home = (String) o.get("homeTeamName");
            String away = (String) o.get("awayTeamName");
            int homeScore = (Integer) o.get("homeScore");
            int awayScore = (Integer) o.get("awayScore");
            String status = (String) o.get("status");
            boolean isFinal = "STATUS_FINAL".equals(status) || "Final".equals(status) || "F/OT".equals(status);

            String result;
            if (!isFinal) {
                result = "NOT FINAL";
            } else if (homeScore == awayScore) {
                result = "TIE (counts as a LOSS)";
            } else {
                boolean pickedHome = selected != null && selected.equals(home);
                boolean homeWon = homeScore > awayScore;
                result = (pickedHome == homeWon) ? "WIN" : "LOSS";
            }
            sb.append(o.get("firstName")).append(" (").append(o.get("username")).append(") took ")
              .append(selected).append(" — ").append(away).append(" ").append(awayScore)
              .append(" @ ").append(home).append(" ").append(homeScore)
              .append(" -> ").append(result).append("\n");
        }

        sb.append("\n=== SEASON STANDINGS (after this week) ===\n");
        for (java.util.Map<String, Object> s : standings) {
            int remaining = (Integer) s.get("remaining");
            sb.append(s.get("firstName")).append(" (").append(s.get("username")).append("): ")
              .append(remaining).append(" of ").append(s.get("initialPicks"))
              .append(" picks left").append(remaining == 0 ? " — ELIMINATED" : "").append("\n");
        }
        sb.append("\nSurvivors still alive: ").append(aliveCount).append("\n");
        sb.append("\nWrite the recap now.");
        return sb.toString();
    }

    // ── M5: dossier injection ──────────────────────────────────

    /** Pool-level context (identity/lore/tone), or "" if none saved. Layer 2 of the prompt. */
    private String poolContextBlock(int season) {
        model.PoolDossier pool = dossierTable.getPoolDossier(season);
        if (pool == null || !pool.hasContent()) return "";
        StringBuilder sb = new StringBuilder("=== POOL CONTEXT ===\n");
        if (nz(pool.getPoolIdentity()))      sb.append("Identity: ").append(pool.getPoolIdentity().trim()).append("\n");
        if (nz(pool.getPoolHistory()))       sb.append("History: ").append(pool.getPoolHistory().trim()).append("\n");
        if (nz(pool.getPoolLore()))          sb.append("Lore: ").append(pool.getPoolLore().trim()).append("\n");
        if (nz(pool.getCommissionerNotes())) sb.append("Commissioner notes: ").append(pool.getCommissionerNotes().trim()).append("\n");
        if (nz(pool.getToneGuidance()))      sb.append("Tone guidance: ").append(pool.getToneGuidance().trim()).append("\n");
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Player dossier block for the given users (null userIds = everyone with
     * content). Sensitivities ride along and are enforced by the system prompt.
     */
    private String dossierBlock(int season, java.util.Collection<Integer> userIds) {
        List<model.UserDossier> all = dossierTable.getUserDossiersForSeason(season);
        StringBuilder sb = new StringBuilder();
        for (model.UserDossier d : all) {
            if (!d.hasContent()) continue;
            if (userIds != null && !userIds.contains(d.getUserId())) continue;
            if (sb.length() == 0) sb.append("=== PLAYER DOSSIERS ===\n");
            sb.append(d.commentaryName()).append(" (").append(d.getUsername()).append(")");
            if (nz(d.getDisplayName())) sb.append(" — always call them \"").append(d.getDisplayName().trim()).append("\"");
            sb.append(":");
            if (nz(d.getPersonality()))   sb.append(" ").append(d.getPersonality().trim());
            if (nz(d.getRivalries()))     sb.append(" Rivalries: ").append(d.getRivalries().trim()).append(".");
            if (nz(d.getSensitivities())) sb.append(" SENSITIVITIES (ceiling): ").append(d.getSensitivities().trim()).append(".");
            sb.append("\n");
        }
        if (sb.length() > 0) sb.append("\n");
        return sb.toString();
    }

    private boolean nz(String s) { return s != null && !s.trim().isEmpty(); }

    /** Weekly Preview user prompt (M4): the slate + field state; masking-aware. */
    private String buildPreviewPrompt(int season, int week, boolean masked,
                                      List<java.util.Map<String, Object>> games,
                                      List<java.util.Map<String, Object>> standings,
                                      List<java.util.Map<String, Object>> picks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Season ").append(season).append(", Week ").append(week)
          .append(". Write the Weekly Preview.\n");
        if (week >= 19) {
            sb.append("PLAYOFF CONTEXT: this is ")
              .append(week == 19 ? "Wild Card weekend" : week == 20 ? "the Divisional round"
                      : week == 21 ? "Championship weekend" : "the Super Bowl")
              .append(" — stakes and slate are playoff-sized.\n");
        }

        sb.append("\n=== THE FIELD ===\n");
        long alive = 0;
        for (java.util.Map<String, Object> s : standings) {
            int remaining = (Integer) s.get("remaining");
            if (remaining > 0) {
                alive++;
                sb.append(s.get("firstName")).append(" (").append(s.get("username")).append("): ")
                  .append(remaining).append(" pick").append(remaining == 1 ? "" : "s").append(" left\n");
            }
        }
        sb.append("Survivors alive: ").append(alive).append("\n");

        sb.append("\n=== THIS WEEK'S SLATE (spread is home-relative; negative = home favored) ===\n");
        for (java.util.Map<String, Object> g : games) {
            sb.append(g.get("awayTeamName")).append(" @ ").append(g.get("homeTeamName"));
            Double sp = (Double) g.get("pointSpread");
            Double ou = (Double) g.get("overUnder");
            if (sp != null) sb.append(" | spread ").append(sp);
            if (ou != null) sb.append(" | o/u ").append(ou);
            sb.append("\n");
        }

        if (masked) {
            sb.append("\nPICKS ARE MASKED this week — players cannot see each other's picks until ");
            sb.append("kickoff. Do NOT reveal or speculate on specific picks; preview the matchups ");
            sb.append("and the pressure instead.\n");
        } else if (picks != null && !picks.isEmpty()) {
            sb.append("\n=== PICKS SO FAR (public this week) ===\n");
            for (java.util.Map<String, Object> p : picks) {
                sb.append(p.get("firstName")).append(" (").append(p.get("username")).append(") -> ")
                  .append(p.get("selectedTeam")).append("\n");
            }
        } else {
            sb.append("\nNo picks are in yet.\n");
        }

        sb.append("\nWrite the preview now.");
        return sb.toString();
    }

    /** Kickoff Reveal user prompt (M4): the unmask moment for one kickoff window. */
    private String buildKickoffRevealPrompt(int season, int week, String windowLabel,
                                            List<java.util.Map<String, Object>> windowGames,
                                            List<java.util.Map<String, Object>> windowPicks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Season ").append(season).append(", Week ").append(week)
          .append(". KICKOFF REVEAL — the ").append(windowLabel)
          .append(" games are kicking off and the masked picks on them are now public.\n");

        sb.append("\n=== GAMES KICKING OFF ===\n");
        for (java.util.Map<String, Object> g : windowGames) {
            sb.append(g.get("awayTeamName")).append(" @ ").append(g.get("homeTeamName"));
            Double sp = (Double) g.get("pointSpread");
            if (sp != null) sb.append(" | spread ").append(sp);
            sb.append("\n");
        }

        sb.append("\n=== PICKS JUST REVEALED ===\n");
        if (windowPicks.isEmpty()) {
            sb.append("(no survivor picks ride on these games)\n");
        } else {
            for (java.util.Map<String, Object> p : windowPicks) {
                sb.append(p.get("firstName")).append(" (").append(p.get("username")).append(") -> ")
                  .append(p.get("selectedTeam")).append("\n");
            }
        }

        sb.append("\nWrite the reveal now (2-3 sentences).");
        return sb.toString();
    }

    /** EVENT user prompt (M3): the detected moment, focused — not a race summary. */
    private String buildEventPrompt(int season, int week, model.RaceEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Season ").append(season).append(", Week ").append(week)
          .append(". LIVE EVENT — type: ").append(event.getType().name()).append("\n\n");
        sb.append("WHAT JUST HAPPENED:\n").append(event.getDescription()).append("\n\n");
        if (event.getType() == model.RaceEvent.EventType.ELIMINATION) {
            sb.append("Remember: ELIMINATION is sympathetic at every snark level. ");
            sb.append("Warm sendoff, no mockery, no Woof.\n\n");
        }
        sb.append("Write the ").append(event.getType().name()).append(" commentary now (1-2 sentences).");
        return sb.toString();
    }

    /** Self-contained hardcoded prompt for the admin test button — needs no DB/game data. */
    private String buildTestPrompt(int season, int week, int snarkLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("This is a TEST of the KOTH commentary system (no live game data).\n");
        sb.append("Season ").append(season).append(", Week ").append(week)
          .append(", configured snark level ").append(snarkLevel).append("/10.\n\n");
        sb.append("Write a short sample blurb (2-3 sentences) in your commentator voice, previewing an imaginary ");
        sb.append("Week ").append(week).append(" survivor slate — set the stage for a room of nervous survivors. ");
        sb.append("Demonstrate the configured snark level so the commissioner can hear what the voice sounds like.");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════
    // ANTHROPIC API CLIENT
    // (HTTP plumbing mirrors services.KothTriageClient.callClaude; this variant
    //  also returns input/output token counts so they can be persisted.)
    // ════════════════════════════════════════════════════════════

    private static final ObjectMapper mapper = new ObjectMapper();

    /** Text plus the token usage from a single /v1/messages call. */
    private static class ClaudeResult {
        final String text;
        final int promptTokens;
        final int responseTokens;

        ClaudeResult(String text, int promptTokens, int responseTokens) {
            this.text = text;
            this.promptTokens = promptTokens;
            this.responseTokens = responseTokens;
        }
    }

    @SuppressWarnings("deprecation")
    private ClaudeResult callClaudeApi(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", apiModel);
            body.put("max_tokens", maxTokens);
            body.put("system", systemPrompt);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            body.put("messages", messages);

            String json = mapper.writeValueAsString(body);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = mapper.readTree(resp);

                StringBuilder text = new StringBuilder();
                JsonNode content = root.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode block : content) {
                        if ("text".equals(block.path("type").asText())) {
                            text.append(block.path("text").asText());
                        }
                    }
                }

                int promptTokens = 0;
                int responseTokens = 0;
                JsonNode usage = root.get("usage");
                if (usage != null) {
                    promptTokens = usage.path("input_tokens").asInt();
                    responseTokens = usage.path("output_tokens").asInt();
                }
                System.out.println("CommentaryService.callClaudeApi - Claude " + promptTokens + " in / "
                        + responseTokens + " out tokens");

                return new ClaudeResult(text.toString(), promptTokens, responseTokens);
            } else {
                String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.err.println("CommentaryService.callClaudeApi - Claude API error " + code + ": " + truncate(err, 500));
                return null;
            }
        } catch (IOException e) {
            System.err.println("CommentaryService.callClaudeApi - API call failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String truncate(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
