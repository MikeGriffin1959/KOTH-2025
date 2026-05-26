package services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Parses the ESPN FPI predictor payload.
 *
 * Confirmed field path (live sample, event 401772635):
 *   homeTeam.statistics[name=="gameProjection"].value  → home win %  (0-100)
 *   awayTeam.statistics[name=="gameProjection"].value  → away win %
 *   homeTeam.team.$ref ".../teams/{id}?..."             → ESPN team id
 *   homeTeam.statistics[name=="teamPredPtDiff"].value   → predicted point diff (home)
 *
 * gameProjection already represents P(win outright): for the sample,
 * home 39.508 + away 60.175 + tie 0.317 ≈ 100, so it suits KOTH directly
 * (a tie is a loss; we do not add tie prob back in).
 */
@Service
public class FpiClient {

    private static final Pattern TEAM_ID_IN_REF = Pattern.compile("/teams/(\\d+)");

    /** Result of parsing one predictor payload. Probabilities are in [0,1]. */
    public static class FpiResult {
        public int    homeTeamId;
        public int    awayTeamId;
        public Double homeWinProb;     // [0,1] or null if unavailable
        public Double awayWinProb;
        public Double homePredPtDiff;  // points (home perspective); + = home favored
        public boolean valid;
    }

    /**
     * @param predictorJson raw JSON from EdgeEspnClient.fetchPredictor (may be null/empty)
     * @return parsed result; result.valid==false if the payload could not be read
     */
    public FpiResult parse(String predictorJson) {
        FpiResult r = new FpiResult();
        if (predictorJson == null || predictorJson.isEmpty()) {
            return r; // valid=false
        }
        try {
            JSONObject root = new JSONObject(predictorJson);
            JSONObject home = root.optJSONObject("homeTeam");
            JSONObject away = root.optJSONObject("awayTeam");
            if (home == null || away == null) {
                System.out.println("DEBUG[edge-fpi]: predictor missing homeTeam/awayTeam");
                return r;
            }

            r.homeTeamId = teamIdFromRef(home);
            r.awayTeamId = teamIdFromRef(away);

            Double homeGp = stat(home, "gameProjection");
            Double awayGp = stat(away, "gameProjection");
            r.homePredPtDiff = stat(home, "teamPredPtDiff");

            // Convert 0-100 → 0-1. Prefer both sides; if only one present, mirror it.
            if (homeGp != null) r.homeWinProb = clamp(homeGp / 100.0);
            if (awayGp != null) r.awayWinProb = clamp(awayGp / 100.0);
            if (r.homeWinProb == null && r.awayWinProb != null) r.homeWinProb = clamp(1.0 - r.awayWinProb);
            if (r.awayWinProb == null && r.homeWinProb != null) r.awayWinProb = clamp(1.0 - r.homeWinProb);

            r.valid = (r.homeWinProb != null && r.homeTeamId > 0 && r.awayTeamId > 0);
            if (r.valid) {
                System.out.printf("DEBUG[edge-fpi]: %d@%d  fpiHome=%.3f predPtDiffHome=%s%n",
                        r.awayTeamId, r.homeTeamId, r.homeWinProb,
                        r.homePredPtDiff == null ? "n/a" : String.format("%.2f", r.homePredPtDiff));
            }
            return r;

        } catch (Exception e) {
            System.err.println("DEBUG[edge-fpi]: parse error: " + e.getMessage());
            return r;
        }
    }

    /** Pull a named stat's numeric value from a homeTeam/awayTeam statistics array. */
    private Double stat(JSONObject teamSide, String name) {
        JSONArray stats = teamSide.optJSONArray("statistics");
        if (stats == null) return null;
        for (int i = 0; i < stats.length(); i++) {
            JSONObject s = stats.optJSONObject(i);
            if (s != null && name.equals(s.optString("name"))) {
                return s.has("value") ? s.optDouble("value") : null;
            }
        }
        return null;
    }

    /** Extract the ESPN team id from homeTeam/awayTeam.team.$ref (".../teams/30?..."). */
    private int teamIdFromRef(JSONObject teamSide) {
        JSONObject team = teamSide.optJSONObject("team");
        if (team == null) return 0;
        String ref = team.optString("$ref", "");
        Matcher m = TEAM_ID_IN_REF.matcher(ref);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private Double clamp(double p) {
        if (p < 0.0) return 0.0;
        if (p > 1.0) return 1.0;
        return p;
    }
}