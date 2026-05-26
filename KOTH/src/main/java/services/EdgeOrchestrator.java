package services;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import helpers.ApiFetchers;
import helpers.ApiParsers;
import helpers.EdgeEspnClient;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorEdgeTable;
import model.Game;
import model.GameEdge;
import services.FpiClient.FpiResult;

/**
 * M1 orchestrator: builds the weekly EdgeSnapshot for the current NFL week.
 *
 * Flow per game:
 *   1. ensure a fresh Vegas spread (reuse existing ApiFetchers/ApiParsers)
 *   2. fetch + parse FPI predictor (gameProjection + team ids + predPtDiff)
 *   3. orient the market spread by FPI (favorite = higher gameProjection)
 *   4. compute market / fpi / elo home win probs + a default blend
 *   5. upsert EdgeSnapshot
 * Then persist the current ELO ratings.
 *
 * No UI here (that's M2). Touches no existing class — only calls them.
 * The scheduled run is profile-gated: set edge.scheduler.enabled=true on the
 * prod (8080) instance only, so the dev (8081) instance never double-fires.
 */
@Service
public class EdgeOrchestrator {

    @Autowired private SqlConnectorGameTable gameTable;
    @Autowired private SqlConnectorEdgeTable edgeTable;
    @Autowired private EdgeEspnClient espnClient;
    @Autowired private FpiClient fpiClient;
    @Autowired private MarketProbService marketProb;
    @Autowired private EloRatingService eloService;
    @Autowired private NFLSeasonCalculator seasonCalculator;

    @Value("${edge.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    // default blend weights (renormalized over whatever sources are present)
    @Value("${edge.blend.wMarket:0.50}") private double wMarket;
    @Value("${edge.blend.wFpi:0.25}")    private double wFpi;
    @Value("${edge.blend.wElo:0.25}")    private double wElo;

    /** Weekly schedule: Wed 06:00 server time by default. Gated by edge.scheduler.enabled. */
    @Scheduled(cron = "${edge.cron:0 0 6 * * WED}")
    public void scheduledRun() {
        if (!schedulerEnabled) {
            System.out.println("DEBUG[edge]: scheduledRun skipped (edge.scheduler.enabled=false)");
            return;
        }
        runWeeklyEdge();
    }

    /** Public manual trigger (call from a temp admin route or a test). */
    public void runWeeklyEdge() {
        int season = seasonCalculator.getCurrentNFLSeason();
        int week   = seasonCalculator.getCurrentNFLWeekNumber();
        runWeeklyEdge(season, week);
    }

    /** Build the snapshot for an explicit season/internal-week. */
    public void runWeeklyEdge(int season, int internalWeek) {
        System.out.println("DEBUG[edge]: runWeeklyEdge season=" + season + " week=" + internalWeek);

        // Bootstrap ELO using ONLY games before this week (no lookahead leakage).
        // For a live current-week run this equals "all finals" anyway.
        eloService.bootstrapAsOf(season, internalWeek);

        List<Game> games = gameTable.getGamesForWeek(season, internalWeek);
        if (games == null || games.isEmpty()) {
            System.out.println("DEBUG[edge]: no games found for " + season + "/wk" + internalWeek);
            return;
        }

        int built = 0;
        for (Game game : games) {
            try {
                GameEdge edge = buildEdgeForGame(game, season, internalWeek);
                if (edge != null) {
                    edgeTable.upsertSnapshot(edge);
                    built++;
                }
            } catch (Exception e) {
                System.err.println("DEBUG[edge]: error on GameID " + game.getGameID() + ": " + e.getMessage());
            }
        }

        // Persist current ratings as of this week
        Map<Integer, Double> ratings = eloService.snapshotRatings();
        edgeTable.upsertEloRatings(season, internalWeek, ratings);

        System.out.println("DEBUG[edge]: runWeeklyEdge complete — " + built + "/" + games.size() + " snapshots");
    }

    private GameEdge buildEdgeForGame(Game game, int season, int internalWeek) {
        long gameId = game.getGameID();

        // 1) fresh Vegas spread via existing fetch/parse (null-aware, unlike the lossy getDouble)
        Double spread = null;
        try {
            String oddsJson = ApiFetchers.FetchESPNGameOdds(String.valueOf(gameId));
            if (oddsJson != null && !oddsJson.isEmpty()) {
                Game oddsCarrier = new Game();
                oddsCarrier.setGameID(gameId);   // so ParseESPNOdds logs the real id, not 0
                Game withOdds = ApiParsers.ParseESPNOdds(oddsJson, oddsCarrier);
                spread = withOdds.getPointSpread();   // magnitude-ish; sign not trusted
            }
        } catch (Exception e) {
            System.out.println("DEBUG[edge]: odds fetch failed for " + gameId + ": " + e.getMessage());
        }

        // 2) FPI predictor
        FpiResult fpi = fpiClient.parse(espnClient.fetchPredictor(gameId));

        // Resolve team ids: prefer the game row; fall back to predictor
        int homeId = game.getHomeTeamId() != 0 ? game.getHomeTeamId() : fpi.homeTeamId;
        int awayId = game.getAwayTeamId() != 0 ? game.getAwayTeamId() : fpi.awayTeamId;
        if (homeId == 0 || awayId == 0) {
            System.out.println("DEBUG[edge]: missing team ids for GameID " + gameId + " — skipping");
            return null;
        }

        GameEdge edge = new GameEdge();
        edge.setEspnEventId(gameId);
        edge.setSeason(season);
        edge.setInternalWeek(internalWeek);
        edge.setHomeTeamId(homeId);
        edge.setAwayTeamId(awayId);
        edge.setKickoffUtc(toSqlDatetimeUtc(game.getDate()));
        edge.setNeutralSite(false); // M1: neutral-site parsing arrives in M2

        // 3) orient market by FPI (favorite = side FPI gives the higher win prob).
        //    If the predictor's home/away ids are flipped vs the game row, align to the game's home.
        Boolean favoriteIsHome = null;
        Double fpiHome = null;
        Double predPtDiffHome = null;
        if (fpi.valid) {
            boolean predHomeMatchesGameHome = (fpi.homeTeamId == homeId);
            fpiHome = predHomeMatchesGameHome ? fpi.homeWinProb : fpi.awayWinProb;
            predPtDiffHome = predHomeMatchesGameHome ? fpi.homePredPtDiff
                    : (fpi.homePredPtDiff == null ? null : -fpi.homePredPtDiff);
            if (fpiHome != null) favoriteIsHome = fpiHome >= 0.5;
        }
        edge.setFpiHome(fpiHome);
        edge.setPredPtDiffHome(predPtDiffHome);

        // If FPI unavailable, fall back to the raw spread sign to orient (home-relative
        // assumption: negative stored spread => home favored). Logged as a fallback.
        if (favoriteIsHome == null && spread != null) {
            favoriteIsHome = spread < 0;
            System.out.println("DEBUG[edge]: FPI unavailable for " + gameId +
                               " — orienting market by raw spread sign (unverified)");
        }

        // 4a) market
        Double marketHome = null;
        if (spread != null && favoriteIsHome != null) {
            marketHome = marketProb.homeWinProb(Math.abs(spread), favoriteIsHome);
            edge.setSpread(Math.abs(spread));
            edge.setFavoriteIsHome(favoriteIsHome);
        }
        edge.setMarketHome(marketHome);

        // 4b) elo
        double eloHome = eloService.homeWinProb(homeId, awayId, edge.isNeutralSite());
        edge.setEloHome(eloHome);

        // 4c) blend (renormalized over present sources)
        edge.setBlendedHome(blend(marketHome, fpiHome, eloHome));

        return edge;
    }

    /** Weighted blend over whichever of market/fpi/elo are non-null; weights renormalize. */
    private Double blend(Double market, Double fpi, Double elo) {
        double num = 0, den = 0;
        if (market != null) { num += wMarket * market; den += wMarket; }
        if (fpi    != null) { num += wFpi    * fpi;    den += wFpi; }
        if (elo    != null) { num += wElo    * elo;    den += wElo; }
        return den == 0 ? null : num / den;
    }

    /** ESPN date ("2025-09-08T20:15Z" / "...:00Z") → SQL 'yyyy-MM-dd HH:mm:ss' (UTC). */
    private String toSqlDatetimeUtc(String espnDate) {
        if (espnDate == null || espnDate.isEmpty()) return null;
        try {
            String s = espnDate.replace("Z", "");
            // normalize to include seconds
            if (s.length() == 16) s = s + ":00";        // yyyy-MM-ddTHH:mm
            return s.replace("T", " ");
        } catch (Exception e) {
            return null;
        }
    }
}