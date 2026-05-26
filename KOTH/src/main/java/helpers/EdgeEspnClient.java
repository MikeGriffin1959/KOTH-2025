package helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.springframework.stereotype.Component;

/**
 * Self-contained ESPN fetches used only by the Edge feature.
 * Kept separate from ApiFetchers so the edge module touches no existing code.
 * Odds reuse ApiFetchers.FetchESPNGameOdds directly; this class adds the
 * FPI predictor endpoint (same host family).
 */
@Component
public class EdgeEspnClient {

    // events/{id}/competitions/{id}/predictor  — FPI gameProjection per team
    private static final String ESPN_PREDICTOR_URL =
        "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/events/%s/competitions/%s/predictor";

    /** Fetch the FPI predictor JSON for a given ESPN event id (= KOTH.Game.GameID). */
    public String fetchPredictor(long gameId) {
        String url = String.format(ESPN_PREDICTOR_URL, gameId, gameId);
        System.out.println("DEBUG[edge-fpi]: fetching predictor " + url);
        String json = get(url);
        if (json == null) {
            System.out.println("DEBUG[edge-fpi]: no predictor data for GameID " + gameId +
                               " (offseason or not yet published)");
        }
        return json;
    }

    @SuppressWarnings("deprecation")
    private String get(String urlString) {
        StringBuilder result = new StringBuilder();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                System.err.println("DEBUG[edge-fpi]: HTTP " + code + " for " + urlString);
                return null;
            }
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            }
        } catch (IOException e) {
            System.err.println("DEBUG[edge-fpi]: fetch error for " + urlString + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result.toString();
    }
}