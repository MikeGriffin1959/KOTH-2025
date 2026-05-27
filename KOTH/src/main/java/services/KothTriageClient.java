package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import model.EdgeCandidate;
import model.TriageResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Claude Haiku triage for the KOTH Edge advisor (M3).
 *
 * Reuses the exact HTTP plumbing pattern from CommentaryService.callClaudeApi
 * (HttpURLConnection → /v1/messages, x-api-key + anthropic-version headers,
 * iterate content[] for type==text), but:
 *   - runs Haiku via its own model property (cheap, weekly)
 *   - prompts for JSON-ONLY output
 *   - strips ```json fences defensively before parsing
 *
 * Input: the top-N ranked EdgeCandidates (with their probs + flags).
 * Output: a TriageResult per team (recommend / confidence / upsetRisk / rationale),
 * which the caller merges back onto the candidates by teamId.
 *
 * If the API key is not configured, triage is a no-op (returns empty list) so
 * the rest of the page still works.
 */
@Service
public class KothTriageClient {

    private static final Logger logger = Logger.getLogger(KothTriageClient.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${edge.api.key:}")
    private String apiKey;

    @Value("${edge.api.model:claude-haiku-4-5-20251001}")
    private String apiModel;

    @Value("${edge.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${edge.api.max-tokens:1024}")
    private int maxTokens;

    @Value("${edge.api.triage-top-n:6}")
    private int triageTopN;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Run triage over the ranked candidate list. Sends only the top-N.
     * Returns TriageResults keyed-able by teamId (each has teamId resolved).
     * Empty list if not configured or on any failure (caller degrades gracefully).
     */
    public List<TriageResult> triage(List<EdgeCandidate> ranked, int season, int internalWeek) {
        List<TriageResult> out = new ArrayList<>();
        if (!isConfigured()) {
            logger.info("DEBUG[edge-triage]: API key not configured — skipping triage");
            return out;
        }
        if (ranked == null || ranked.isEmpty()) return out;

        List<EdgeCandidate> top = ranked.subList(0, Math.min(triageTopN, ranked.size()));

        String system = buildSystemPrompt();
        String user = buildUserPrompt(top, season, internalWeek);

        String raw = callClaude(system, user);
        if (raw == null || raw.isEmpty()) {
            logger.warning("DEBUG[edge-triage]: empty response from Claude");
            return out;
        }

        // map teamName (as sent) → teamId, to resolve Claude's echoed team strings
        Map<String, Integer> nameToId = new LinkedHashMap<>();
        for (EdgeCandidate c : top) nameToId.put(c.getTeamName().toUpperCase(), c.getTeamId());

        try {
            String clean = stripFences(raw);
            JsonNode root = mapper.readTree(clean);
            JsonNode picks = root.get("picks");
            if (picks != null && picks.isArray()) {
                for (JsonNode p : picks) {
                    TriageResult t = new TriageResult();
                    t.setTeam(p.path("team").asText(null));
                    t.setRecommend(p.path("recommend").asBoolean(false));
                    if (p.has("confidence") && !p.get("confidence").isNull())
                        t.setConfidence(p.get("confidence").asDouble());
                    t.setUpsetRisk(p.path("upsetRisk").asText(null));
                    t.setRationale(p.path("rationale").asText(null));
                    if (t.getTeam() != null) {
                        Integer id = nameToId.get(t.getTeam().toUpperCase());
                        if (id != null) t.setTeamId(id);
                    }
                    out.add(t);
                    logger.info("DEBUG[edge-triage]: " + t);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "DEBUG[edge-triage]: parse error: " + e.getMessage() +
                    " — raw was: " + truncate(raw, 400), e);
        }
        return out;
    }

    // ── prompts ───────────────────────────────────────────────

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a survivor-pool (King of the Hill) pick analyst. ");
        sb.append("In this pool there is NO restriction on reusing teams, so each week is ");
        sb.append("independent and the only goal is to pick teams that WIN OUTRIGHT this week ");
        sb.append("(a tie counts as a loss). You are given several candidate favorites with ");
        sb.append("their market-implied win probability, two model probabilities (ESPN FPI and ");
        sb.append("an internal ELO), a blended probability, and any situational flags already ");
        sb.append("detected (divisional game, short rest, travel, model/market divergence, thin edge).\n\n");
        sb.append("Your job: judge which candidates are the SAFEST survivor picks — i.e., least ");
        sb.append("likely to lose — and explicitly call out upset risk the numbers may understate ");
        sb.append("(key injuries, weather, letdown/lookahead spots, coaching, recent form). ");
        sb.append("Favor teams with high, agreed-upon win probability and clean situations. ");
        sb.append("Be skeptical of favorites the models doubt or that carry flags.\n\n");
        sb.append("Respond with ONLY a JSON object, no prose, no markdown, no code fences. ");
        sb.append("Schema:\n");
        sb.append("{\"picks\":[{\"team\":\"<exact team name as given>\",\"recommend\":true|false,");
        sb.append("\"confidence\":0.0-1.0,\"upsetRisk\":\"low|med|high\",");
        sb.append("\"rationale\":\"one concise sentence\"}]}\n");
        sb.append("Include every candidate you were given, in safest-first order.");
        return sb.toString();
    }

    private String buildUserPrompt(List<EdgeCandidate> top, int season, int internalWeek) {
        StringBuilder sb = new StringBuilder();
        sb.append("Season ").append(season).append(", Week ").append(internalWeek).append(".\n");
        sb.append("Candidate favorites (probabilities are this team's chance to win outright):\n\n");
        for (EdgeCandidate c : top) {
            sb.append("- ").append(c.getTeamName())
              .append(c.isHome() ? " (home vs " : " (away @ ").append(c.getOpponentName()).append("): ");
            sb.append("market=").append(pct(c.getMarketProb()))
              .append(", FPI=").append(pct(c.getFpiProb()))
              .append(", ELO=").append(pct(c.getEloProb()))
              .append(", blended=").append(pct(c.getBlendedProb()));
            if (c.hasFlags()) sb.append("; flags: ").append(c.getFlagsDisplay());
            sb.append("\n");
        }
        sb.append("\nReturn the JSON now.");
        return sb.toString();
    }

    private String pct(Double p) {
        return p == null ? "n/a" : String.format("%.0f%%", p * 100);
    }

    // ── HTTP (mirrors CommentaryService.callClaudeApi) ─────────

    @SuppressWarnings("deprecation")
    private String callClaude(String systemPrompt, String userPrompt) {
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
                JsonNode content = root.get("content");
                if (content != null && content.isArray()) {
                    StringBuilder text = new StringBuilder();
                    for (JsonNode block : content) {
                        if ("text".equals(block.path("type").asText())) {
                            text.append(block.path("text").asText());
                        }
                    }
                    JsonNode usage = root.get("usage");
                    if (usage != null) {
                        logger.info(String.format("DEBUG[edge-triage]: Claude %d in / %d out tokens",
                                usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt()));
                    }
                    return text.toString();
                }
                logger.warning("DEBUG[edge-triage]: unexpected response structure");
                return null;
            } else {
                String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                logger.severe("DEBUG[edge-triage]: Claude API error " + code + ": " + truncate(err, 400));
                return null;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "DEBUG[edge-triage]: API call failed: " + e.getMessage(), e);
            return null;
        }
    }

    /** Strip ```json ... ``` or ``` ... ``` fences if the model wrapped its output. */
    private String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private String truncate(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}