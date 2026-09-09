package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Weighted blend of market / FPI / ELO / ELWAY win probabilities.
 * Weights renormalize over whichever sources are present (non-null), so a game
 * missing FPI in the offseason — or missing ELWAY because the commissioner
 * hasn't pasted this week's projections yet — still blends correctly.
 *
 * Weights are externalized so the M5 backtest can tune them without code changes.
 * Starting point: market 0.40, ELWAY 0.25, FPI 0.20, ELO 0.15.
 */
@Service
public class WinProbBlendingService {

    @Value("${edge.blend.wMarket:0.40}") private double wMarket;
    @Value("${edge.blend.wFpi:0.20}")    private double wFpi;
    @Value("${edge.blend.wElo:0.15}")    private double wElo;
    @Value("${edge.blend.wElway:0.25}")  private double wElway;

    /** Blend home-oriented probabilities. Returns null only if all sources are null. */
    public Double blend(Double market, Double fpi, Double elo, Double elway) {
        double num = 0, den = 0;
        if (market != null) { num += wMarket * market; den += wMarket; }
        if (fpi    != null) { num += wFpi    * fpi;    den += wFpi; }
        if (elo    != null) { num += wElo    * elo;    den += wElo; }
        if (elway  != null) { num += wElway  * elway;  den += wElway; }
        return den == 0 ? null : num / den;
    }

    /** Three-source form kept for callers that predate ELWAY. */
    public Double blend(Double market, Double fpi, Double elo) {
        return blend(market, fpi, elo, null);
    }

    public double getwMarket() { return wMarket; }
    public double getWfpi()    { return wFpi; }
    public double getWelo()    { return wElo; }
    public double getWelway()  { return wElway; }
}
