package services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import helpers.SqlConnectorEdgeTable;
import helpers.SqlConnectorGameTable;
import model.EdgeCandidate;
import model.Game;
import model.GameEdge;
import model.TeamContext;
import model.TriageResult;
import services.UpsetDetectionService.UpsetResult;

/**
 * Builds the weekly "safe list": for each game, the FAVORED side becomes one
 * candidate (you'd never survivor-pick the underdog), scored by:
 *
 *   safetyScore = blendedProb(team) - divergencePenalty - situationalPenalty
 *
 * Candidates are ranked by safetyScore desc. Lives are then allocated either
 * SPREAD (1 per game across the top-N distinct games — survivor-correct default)
 * or STACK (all on the single safest team).
 */
@Service
public class EdgeCandidateService {

    public enum Allocation { SPREAD, STACK }

    @Autowired private SqlConnectorEdgeTable edgeTable;
    @Autowired private SqlConnectorGameTable gameTable;
    @Autowired private UpsetDetectionService upsetService;
    @Autowired private KothTriageClient triageClient;

    /**
     * Build the ranked candidate list for a week and allocate the given number of lives.
     *
     * @param season       NFL season
     * @param internalWeek your 1..22 week
     * @param lives        remaining lives to allocate (>=1)
     * @param allocation   SPREAD (default) or STACK
     */
    public List<EdgeCandidate> buildRankedCandidates(int season, int internalWeek,
                                                     int lives, Allocation allocation) {
        List<GameEdge> snapshots = edgeTable.getSnapshotsForWeek(season, internalWeek);
        Map<Integer, TeamContext> ctx = edgeTable.getTeamContexts();
        Map<Long, Game> gamesById = indexGames(gameTable.getGamesForWeek(season, internalWeek));

        List<EdgeCandidate> candidates = new ArrayList<>();
        for (GameEdge edge : snapshots) {
            EdgeCandidate c = toCandidate(edge, ctx, gamesById);
            if (c != null) candidates.add(c);
        }

        // rank by safety score descending
        candidates.sort(Comparator.comparingDouble(EdgeCandidate::getSafetyScore).reversed());

        allocate(candidates, Math.max(0, lives), allocation == null ? Allocation.SPREAD : allocation);
        return candidates;
    }

    /** Persist the ranked list as this week's recommendations. */
    public void persistRecommendations(int season, int internalWeek, List<EdgeCandidate> ranked) {
        edgeTable.replaceRecommendations(season, internalWeek, ranked);
    }

    /** True if the Claude triage client has an API key configured. */
    public boolean isTriageConfigured() {
        return triageClient.isConfigured();
    }

    /**
     * Run Claude Haiku triage over the ranked list and merge verdicts back onto the
     * matching candidates by teamId. No-op (leaves candidates untouched) if triage
     * is unconfigured or returns nothing. Returns the (possibly enriched) list.
     */
    public List<EdgeCandidate> applyTriage(List<EdgeCandidate> ranked, int season, int internalWeek) {
        if (ranked == null || ranked.isEmpty()) return ranked;
        if (!triageClient.isConfigured()) return ranked;

        List<TriageResult> results = triageClient.triage(ranked, season, internalWeek);
        if (results.isEmpty()) return ranked;

        Map<Integer, TriageResult> byTeam = new HashMap<>();
        for (TriageResult t : results) {
            if (t.getTeamId() != 0) byTeam.put(t.getTeamId(), t);
        }
        for (EdgeCandidate c : ranked) {
            TriageResult t = byTeam.get(c.getTeamId());
            if (t != null) {
                c.setClaudeConfidence(t.getConfidence());
                c.setClaudeUpsetRisk(t.getUpsetRisk());
                c.setClaudeRationale(t.getRationale());
                c.setClaudeRecommend(t.isRecommend());
            }
        }
        return ranked;
    }

    // ── internals ─────────────────────────────────────────────

    private EdgeCandidate toCandidate(GameEdge edge, Map<Integer, TeamContext> ctx,
                                      Map<Long, Game> gamesById) {
        Double blendedHome = edge.getBlendedHome();
        if (blendedHome == null) {
            // no usable probability — skip (can happen in deep offseason)
            return null;
        }

        // the FAVORED side is the candidate (>= 0.5 home means home favored)
        boolean candidateIsHome = blendedHome >= 0.5;

        int candId = candidateIsHome ? edge.getHomeTeamId() : edge.getAwayTeamId();
        int oppId  = candidateIsHome ? edge.getAwayTeamId() : edge.getHomeTeamId();

        EdgeCandidate c = new EdgeCandidate();
        c.setEspnEventId(edge.getEspnEventId());
        c.setTeamId(candId);
        c.setOpponentTeamId(oppId);
        c.setHome(candidateIsHome);
        c.setKickoffUtc(edge.getKickoffUtc());

        // probabilities oriented to the candidate
        c.setMarketProb(orient(edge.getMarketHome(), candidateIsHome));
        c.setFpiProb(orient(edge.getFpiHome(), candidateIsHome));
        c.setEloProb(orient(edge.getEloHome(), candidateIsHome));
        c.setBlendedProb(orient(blendedHome, candidateIsHome));

        // names: prefer the getGamesForWeek display names, fall back to team short name
        Game g = gamesById.get(edge.getEspnEventId());
        c.setTeamName(displayName(candId, candidateIsHome, g, ctx));
        c.setOpponentName(displayName(oppId, !candidateIsHome, g, ctx));

        // upset evaluation
        UpsetResult ur = upsetService.evaluate(edge, candidateIsHome, ctx);
        c.setUpsetFlags(ur.flags);
        c.setDivergencePenalty(ur.divergencePenalty);
        c.setUpsetPenalty(ur.situationalPenalty);

        double safety = c.getBlendedProb() - ur.divergencePenalty - ur.situationalPenalty;
        c.setSafetyScore(safety);
        return c;
    }

    /** SPREAD: 1 life per game across the top distinct games. STACK: all on #1. */
    private void allocate(List<EdgeCandidate> ranked, int lives, Allocation alloc) {
        if (ranked.isEmpty() || lives <= 0) return;

        if (alloc == Allocation.STACK) {
            ranked.get(0).setAllocatedLives(lives);
            ranked.get(0).setRecommended(true);
            return;
        }

        // SPREAD: one life each down the ranked list (each candidate is a distinct game,
        // since we emit exactly one candidate per game). If lives exceed games, the
        // remainder wraps back to the safest (you can't have more lives than games be
        // truly independent, so extra lives double up on the safest first).
        int n = ranked.size();
        int i = 0;
        int remaining = lives;
        // first pass: one per game, top-down
        while (remaining > 0 && i < n) {
            ranked.get(i).setAllocatedLives(ranked.get(i).getAllocatedLives() + 1);
            ranked.get(i).setRecommended(true);
            i++;
            remaining--;
        }
        // overflow: stack extras onto the safest
        int j = 0;
        while (remaining > 0) {
            ranked.get(j % n).setAllocatedLives(ranked.get(j % n).getAllocatedLives() + 1);
            j++;
            remaining--;
        }
    }

    private Map<Long, Game> indexGames(List<Game> games) {
        Map<Long, Game> m = new HashMap<>();
        if (games != null) for (Game g : games) m.put(g.getGameID(), g);
        return m;
    }

    private Double orient(Double homeProb, boolean candidateIsHome) {
        if (homeProb == null) return null;
        return candidateIsHome ? homeProb : 1.0 - homeProb;
    }

    private String displayName(int teamId, boolean isHome, Game g, Map<Integer, TeamContext> ctx) {
        if (g != null) {
            if (isHome && g.getHomeTeamName() != null) return g.getHomeTeamName();
            if (!isHome && g.getAwayTeamName() != null) return g.getAwayTeamName();
        }
        TeamContext tc = ctx.get(teamId);
        if (tc != null && tc.getShortName() != null) return tc.getShortName();
        return "Team " + teamId;
    }
}