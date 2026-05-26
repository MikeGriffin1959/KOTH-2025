package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Weighted blend of market / FPI / ELO win probabilities.
 * Weights renormalize over whichever sources are present (non-null), so a game
 * missing FPI in the offseason still blends market+ELO correctly.
 *
 * Weights are externalized so the M5 backtest can tune them without code changes.
 * Starting point (per design): market 0.50, FPI 0.25, ELO 0.25.
 */
@Service
public class WinProbBlendingService {

    @Value("${edge.blend.wMarket:0.50}") private double wMarket;
    @Value("${edge.blend.wFpi:0.25}")    private double wFpi;
    @Value("${edge.blend.wElo:0.25}")    private double wElo;

    /** Blend home-oriented probabilities. Returns null only if all three are null. */
    public Double blend(Double market, Double fpi, Double elo) {
        double num = 0, den = 0;
        if (market != null) { num += wMarket * market; den += wMarket; }
        if (fpi    != null) { num += wFpi    * fpi;    den += wFpi; }
        if (elo    != null) { num += wElo    * elo;    den += wElo; }
        return den == 0 ? null : num / den;
    }

    public double getwMarket() { return wMarket; }
    public double getWfpi()    { return wFpi; }
    public double getWelo()    { return wElo; }
}