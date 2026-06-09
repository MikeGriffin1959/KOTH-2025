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
        String systemPrompt = buildSystemPrompt(snarkLevel);
        String userPrompt = buildTestPrompt(season, week, snarkLevel);

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

        System.out.println("CommentaryService.generateTestCommentary - persisted commentaryId=" + commentary.getCommentaryId()
                + " (" + result.promptTokens + " in / " + result.responseTokens + " out tokens)");
        return new TestResult(true, "Test commentary generated", result.text, commentary.getCommentaryId());
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
    private String buildSystemPrompt(int snarkLevel) {
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
        sb.append("the data you are given.\n\n");

        sb.append("OUTPUT: Natural prose. No markdown, no bullet points, no headers. Keep it tight and broadcast-ready.\n");

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
