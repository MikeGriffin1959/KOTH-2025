package services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import model.GameEdge;
import model.TeamContext;

/**
 * Raises upset flags against a favorite and converts them into a numeric penalty
 * subtracted from blended win prob to form the safety score.
 *
 * "Balanced" calibration (v1, pre-backtest): flag clear traps AND moderate concerns.
 * Every threshold/penalty is externalized so the M5 backtest can tune them.
 *
 * Flags considered for THIS candidate team (the side we'd pick):
 *   DIVISIONAL      — both teams share a division (familiarity → more upsets)
 *   SHORT_REST      — this team has fewer rest days than its opponent (or a Thu game)
 *   BODY_CLOCK      — west-coast team playing an early-Eastern kickoff (large tz delta + early local time)
 *   LONG_TRAVEL     — great-circle travel beyond a threshold (road team only)
 *   MODEL_DIVERGENCE— market and models disagree materially on this team's win prob
 *   THIN_EDGE       — blended prob barely above a coin flip (not really "safe")
 *
 * Penalties are additive but capped, so a single game can't be driven absurdly negative.
 */
@Service
public class UpsetDetectionService {

    // ── flag thresholds ───────────────────────────────────────
    @Value("${edge.upset.divergenceFlag:0.10}")  private double divergenceFlagThreshold; // |market - modelAvg|
    @Value("${edge.upset.thinEdge:0.55}")         private double thinEdgeThreshold;       // blended below this = thin
    @Value("${edge.upset.travelMiles:1500}")      private double longTravelMiles;
    @Value("${edge.upset.bodyClockTzDelta:2}")    private int    bodyClockTzDelta;        // hours
    @Value("${edge.upset.bodyClockLocalHour:13}") private int    bodyClockEarlyLocalHour; // kickoff local hour <= this

    // ── penalties (probability points subtracted from blended) ──
    @Value("${edge.penalty.divisional:0.030}")   private double pDivisional;
    @Value("${edge.penalty.shortRest:0.030}")    private double pShortRest;
    @Value("${edge.penalty.bodyClock:0.040}")    private double pBodyClock;
    @Value("${edge.penalty.longTravel:0.020}")   private double pLongTravel;
    @Value("${edge.penalty.divergencePerPoint:0.50}") private double pDivergencePerPoint; // × the gap
    @Value("${edge.penalty.thinEdge:0.030}")     private double pThinEdge;
    @Value("${edge.penalty.cap:0.20}")           private double penaltyCap;

    /** Output bundle: flags + the split penalties. */
    public static class UpsetResult {
        public final java.util.List<String> flags = new java.util.ArrayList<>();
        public double divergencePenalty;
        public double situationalPenalty;
        public double totalPenalty() {
            double t = divergencePenalty + situationalPenalty;
            return t;
        }
    }

    /**
     * Evaluate the candidate side of a game.
     *
     * @param edge        the persisted GameEdge (home-oriented probs)
     * @param candidateIsHome whether the team we'd pick is the home team
     * @param ctx         teamId → TeamContext (divisions/geo); may be partially null
     * @return flags + penalties (penalty is a positive number to subtract)
     */
    public UpsetResult evaluate(GameEdge edge, boolean candidateIsHome, Map<Integer, TeamContext> ctx) {
        UpsetResult r = new UpsetResult();

        int candId = candidateIsHome ? edge.getHomeTeamId() : edge.getAwayTeamId();
        int oppId  = candidateIsHome ? edge.getAwayTeamId() : edge.getHomeTeamId();
        TeamContext cand = ctx.get(candId);
        TeamContext opp  = ctx.get(oppId);

        // candidate-oriented probabilities
        Double market = orient(edge.getMarketHome(), candidateIsHome);
        Double fpi    = orient(edge.getFpiHome(),    candidateIsHome);
        Double elo    = orient(edge.getEloHome(),    candidateIsHome);
        Double blended= orient(edge.getBlendedHome(),candidateIsHome);

        // ── MODEL_DIVERGENCE ──
        // gap between the market and the average of the two models, on this team
        if (market != null) {
            double modelSum = 0; int n = 0;
            if (fpi != null) { modelSum += fpi; n++; }
            if (elo != null) { modelSum += elo; n++; }
            if (n > 0) {
                double modelAvg = modelSum / n;
                double gap = Math.abs(market - modelAvg);
                if (gap >= divergenceFlagThreshold) {
                    String dir = (modelAvg < market) ? "models cooler than market" : "models hotter than market";
                    r.flags.add(String.format("DIVERGENCE %.0f%% (%s)", gap * 100, dir));
                    r.divergencePenalty += pDivergencePerPoint * gap;
                }
            }
        }

        // ── THIN_EDGE ──
        if (blended != null && blended < thinEdgeThreshold) {
            r.flags.add(String.format("THIN_EDGE %.0f%%", blended * 100));
            r.situationalPenalty += pThinEdge;
        }

        // ── DIVISIONAL ──
        if (!edge.isNeutralSite() && cand != null && opp != null
                && cand.getDivision() != null && cand.getDivision().equals(opp.getDivision())) {
            r.flags.add("DIVISIONAL");
            r.situationalPenalty += pDivisional;
        }

        // ── SHORT_REST / BODY_CLOCK / LONG_TRAVEL ──
        // Skip travel/body-clock if neutral site (home stadium geo doesn't apply).
        if (!edge.isNeutralSite()) {
            LocalDateTime kickoff = parseUtc(edge.getKickoffUtc());

            // BODY_CLOCK + LONG_TRAVEL apply to the road team only
            if (!candidateIsHome && cand != null && opp != null
                    && cand.getLat() != null && cand.getLng() != null
                    && opp.getLat() != null && opp.getLng() != null) {

                double miles = greatCircleMiles(cand.getLat(), cand.getLng(), opp.getLat(), opp.getLng());
                if (miles >= longTravelMiles) {
                    r.flags.add(String.format("LONG_TRAVEL %.0fmi", miles));
                    r.situationalPenalty += pLongTravel;
                }

                // crude body-clock: large tz delta west→east + early local kickoff
                Integer tzDelta = tzOffsetHours(opp.getTz()) != null && tzOffsetHours(cand.getTz()) != null
                        ? tzOffsetHours(opp.getTz()) - tzOffsetHours(cand.getTz()) : null;
                if (tzDelta != null && tzDelta >= bodyClockTzDelta && kickoff != null) {
                    // kickoff is UTC; convert to the venue (opponent home) local hour
                    Integer oppOff = tzOffsetHours(opp.getTz());
                    if (oppOff != null) {
                        int localHour = ((kickoff.getHour() + oppOff) % 24 + 24) % 24;
                        if (localHour <= bodyClockEarlyLocalHour) {
                            r.flags.add(String.format("BODY_CLOCK (≈%d:00 local, +%dh east)", localHour, tzDelta));
                            r.situationalPenalty += pBodyClock;
                        }
                    }
                }
            }

            // SHORT_REST: a Thursday kickoff is the common short-rest case in v1
            // (full per-team rest-day diff needs prior-game dates; deferred to a later pass).
            if (kickoff != null && kickoff.getDayOfWeek().getValue() == 4) { // Thursday
                r.flags.add("SHORT_REST (Thu)");
                r.situationalPenalty += pShortRest;
            }
        }

        // cap the situational penalty (divergence is separate, also capped)
        if (r.situationalPenalty > penaltyCap) r.situationalPenalty = penaltyCap;
        if (r.divergencePenalty > penaltyCap)  r.divergencePenalty = penaltyCap;

        return r;
    }

    // ── helpers ───────────────────────────────────────────────

    private Double orient(Double homeProb, boolean candidateIsHome) {
        if (homeProb == null) return null;
        return candidateIsHome ? homeProb : 1.0 - homeProb;
    }

    private LocalDateTime parseUtc(String sqlDt) {
        if (sqlDt == null || sqlDt.isEmpty()) return null;
        try {
            // accepts 'yyyy-MM-dd HH:mm:ss' or ISO 'yyyy-MM-ddTHH:mm:ss'
            String s = sqlDt.replace("T", " ");
            if (s.length() == 16) s = s + ":00";
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    private double greatCircleMiles(double lat1, double lon1, double lat2, double lon2) {
        double R = 3958.8; // miles
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Approx UTC offset (hours) for the IANA zones we seed. Negative = west of UTC. */
    private Integer tzOffsetHours(String tz) {
        if (tz == null) return null;
        switch (tz) {
            case "America/New_York":
            case "America/Indiana/Indianapolis": return -5;
            case "America/Chicago":              return -6;
            case "America/Denver":               return -7;
            case "America/Phoenix":              return -7; // no DST; fine for relative deltas
            case "America/Los_Angeles":          return -8;
            default:                             return null;
        }
    }
}