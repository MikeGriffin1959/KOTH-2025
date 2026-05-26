package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Converts a Vegas point spread into a win probability.
 *
 * Model: NFL margin ~ Normal(spread, sigma), sigma ≈ 13.5 (tune in backtest).
 *   pFavorite = Phi( |spread| / sigma )
 * A 6.5-pt favorite ≈ Phi(0.48) ≈ 0.68.
 *
 * We do NOT trust the raw sign of the stored ESPN spread (ParseESPNOdds keeps
 * only a magnitude and drops which team it favors). Instead the caller passes
 * favoriteIsHome, derived from the FPI predictor (the team with the higher
 * gameProjection). Vegas and FPI essentially never disagree on *who* is
 * favored — only on margin — so this orientation is safe, and it removes any
 * dependence on the odds payload's structure.
 */
@Service
public class MarketProbService {

    @Value("${edge.spread.sigma:13.5}")
    private double sigma;

    /**
     * @param spreadMagnitude absolute spread in points (>=0); null/NaN → returns null
     * @param favoriteIsHome  true if the home team is favored
     * @return P(home win) in [0,1], or null if no usable spread
     */
    public Double homeWinProb(Double spreadMagnitude, boolean favoriteIsHome) {
        if (spreadMagnitude == null || Double.isNaN(spreadMagnitude)) return null;
        double mag = Math.abs(spreadMagnitude);
        double pFavorite = clamp(phi(mag / sigma));
        return favoriteIsHome ? pFavorite : 1.0 - pFavorite;
    }

    /** Standard normal CDF via the A&S 7.1.26 erf approximation. */
    private double phi(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private double erf(double z) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(z));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z);
        return z >= 0 ? y : -y;
    }

    private double clamp(double p) {
        if (p < 0.0) return 0.0;
        if (p > 1.0) return 1.0;
        return p;
    }
}