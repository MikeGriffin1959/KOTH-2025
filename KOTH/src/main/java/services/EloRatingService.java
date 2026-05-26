package services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import helpers.SqlConnectorEdgeTable;
import model.Game;

/**
 * Maintains an ELO rating per team, bootstrapped by replaying KOTH.Game finals
 * (your own historical data — no external source). FiveThirtyEight-style:
 *   pHome   = 1 / (1 + 10^(-(rH - rA + HFA)/400))
 *   update  = K * movMult * (actual - expected),  actual in {1, 0.5, 0}
 *   movMult = ln(|margin|+1) * (2.2 / (|eloDiffWinner|*0.001 + 2.2))   (autocorr. correction)
 * Each new season regresses every team 1/3 toward the league mean (1505).
 *
 * M1 limitation: neutral-site flags are not yet stored on historical games, so the
 * bootstrap applies HFA to every past game. The live pHome() call honors a neutral
 * flag (HFA=0) once the orchestrator supplies it. Backtest (M5) refines this.
 */
@Service
public class EloRatingService {

    @Autowired
    private SqlConnectorEdgeTable edgeTable;

    @Value("${edge.elo.k:20.0}")
    private double k;

    @Value("${edge.elo.hfa:48.0}")
    private double hfa;

    @Value("${edge.elo.baseline:1505.0}")
    private double baseline;

    @Value("${edge.elo.seasonRegress:0.3333}")
    private double seasonRegress;

    private final Map<Integer, Double> ratings = new HashMap<>();
    // the (season, week) the current ratings are valid as-of; null = not bootstrapped
    private Integer bootSeason = null;
    private Integer bootWeek = null;

    /**
     * Bootstrap ratings using only finals strictly BEFORE (season, internalWeek),
     * preventing lookahead leakage. Re-runs if the requested cutoff differs from the
     * cutoff the current ratings were built for. Pass MAX_VALUE/MAX_VALUE for "all games".
     */
    public synchronized void bootstrapAsOf(int season, int internalWeek) {
        if (bootSeason != null && bootSeason == season && bootWeek != null && bootWeek == internalWeek) {
            return; // already built for this exact cutoff
        }
        long t0 = System.currentTimeMillis();
        ratings.clear();

        List<Game> finals = edgeTable.getFinalsBefore(season, internalWeek);
        int prevSeason = Integer.MIN_VALUE;
        int applied = 0;

        for (Game g : finals) {
            if (prevSeason != Integer.MIN_VALUE && g.getSeason() != prevSeason) {
                regressAllTowardBaseline();
            }
            prevSeason = g.getSeason();
            applyGame(g.getHomeTeamId(), g.getAwayTeamId(),
                      g.getHomeScore(), g.getAwayScore(), false);
            applied++;
        }

        bootSeason = season;
        bootWeek = internalWeek;
        System.out.println("DEBUG[edge-elo]: bootstrapped as-of " + season + "/wk" + internalWeek +
                           " from " + applied + " finals, " + ratings.size() + " teams, " +
                           (System.currentTimeMillis() - t0) + " ms");
    }

    /** Bootstrap from ALL finals, but ONLY if nothing has been bootstrapped yet.
     *  Never overrides an existing cutoff bootstrap. */
    public synchronized void ensureBootstrapped() {
        if (bootSeason != null && bootWeek != null) return;
        bootstrapAsOf(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Force a re-bootstrap on next call. */
    public synchronized void invalidate() {
        bootSeason = null;
        bootWeek = null;
    }

    /** True once any bootstrap (cutoff or all-games) has populated ratings. */
    private boolean isBootstrapped() {
        return bootSeason != null && bootWeek != null;
    }

    /** P(home win) for an upcoming matchup. Pass neutralSite=true to zero out HFA. */
    public double homeWinProb(int homeTeamId, int awayTeamId, boolean neutralSite) {
        if (!isBootstrapped()) ensureBootstrapped();
        double rH = ratings.getOrDefault(homeTeamId, baseline);
        double rA = ratings.getOrDefault(awayTeamId, baseline);
        double appliedHfa = neutralSite ? 0.0 : hfa;
        return expected(rH + appliedHfa, rA);
    }

    /** Current rating for a team (baseline if unseen). */
    public double getRating(int teamId) {
        if (!isBootstrapped()) ensureBootstrapped();
        return ratings.getOrDefault(teamId, baseline);
    }

    /** Snapshot of all current ratings (for persistence). */
    public Map<Integer, Double> snapshotRatings() {
        if (!isBootstrapped()) ensureBootstrapped();
        return new HashMap<>(ratings);
    }

    // ── internals ─────────────────────────────────────────────

    private void applyGame(int home, int away, int homeScore, int awayScore, boolean neutral) {
        double rH = ratings.getOrDefault(home, baseline);
        double rA = ratings.getOrDefault(away, baseline);
        double appliedHfa = neutral ? 0.0 : hfa;

        double expectedHome = expected(rH + appliedHfa, rA);
        double actualHome = homeScore > awayScore ? 1.0 : (homeScore < awayScore ? 0.0 : 0.5);
        int margin = Math.abs(homeScore - awayScore);

        // winner's pre-game elo edge (for the MOV autocorrelation correction)
        double eloDiffWinner;
        if (actualHome == 1.0)      eloDiffWinner = (rH + appliedHfa) - rA;
        else if (actualHome == 0.0) eloDiffWinner = rA - (rH + appliedHfa);
        else                        eloDiffWinner = 0.0; // tie

        double movMult = (margin == 0)
                ? 1.0
                : Math.log(margin + 1.0) * (2.2 / (Math.abs(eloDiffWinner) * 0.001 + 2.2));

        double delta = k * movMult * (actualHome - expectedHome);
        ratings.put(home, rH + delta);
        ratings.put(away, rA - delta);
    }

    private void regressAllTowardBaseline() {
        for (Map.Entry<Integer, Double> e : ratings.entrySet()) {
            double r = e.getValue();
            e.setValue(r + seasonRegress * (baseline - r));
        }
    }

    private double expected(double a, double b) {
        return 1.0 / (1.0 + Math.pow(10.0, -(a - b) / 400.0));
    }
}