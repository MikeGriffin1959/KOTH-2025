package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.Commentary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the KOTH.Commentary table (Commentary feature, M1).
 * Mirrors the SqlConnector* style used elsewhere in KOTH (field @Autowired
 * DataSource, try-with-resources, System.out.println trace logging).
 *
 * Schema migration: KOTH/db/M1_commentary.sql (apply manually — no Flyway).
 */
@Component
public class SqlConnectorCommentaryTable {

    @Autowired
    private DataSource dataSource;

    /**
     * Simple {promptTokens, responseTokens} pair for the daily cost-cap query.
     * Sums are longs to avoid overflow across a busy day.
     */
    public static class TokenTotals {
        public final long promptTokens;
        public final long responseTokens;

        public TokenTotals(long promptTokens, long responseTokens) {
            this.promptTokens = promptTokens;
            this.responseTokens = responseTokens;
        }

        @Override
        public String toString() {
            return "TokenTotals{promptTokens=" + promptTokens + ", responseTokens=" + responseTokens + "}";
        }
    }

    /**
     * Insert a commentary row. createdAt is DB-populated (DEFAULT CURRENT_TIMESTAMP).
     * On success the generated commentaryId is set back onto the passed object.
     */
    public boolean insert(Commentary commentary) {
        System.out.println("SqlConnectorCommentaryTable.insert method called for streamType=" + commentary.getStreamType());
        String sql = "INSERT INTO KOTH.Commentary "
                + "(season, kothSeason, week, streamType, eventType, affectedUserIds, gameId, snarkLevel, promptTokens, responseTokens, body) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setInt(1, commentary.getSeason());
            preparedStatement.setString(2, commentary.getKothSeason());
            preparedStatement.setInt(3, commentary.getWeek());
            preparedStatement.setString(4, commentary.getStreamType());
            preparedStatement.setString(5, commentary.getEventType());
            preparedStatement.setString(6, commentary.getAffectedUserIds());
            preparedStatement.setObject(7, commentary.getGameId());        // Integer, nullable
            preparedStatement.setInt(8, commentary.getSnarkLevel());
            preparedStatement.setObject(9, commentary.getPromptTokens());   // Integer, nullable
            preparedStatement.setObject(10, commentary.getResponseTokens()); // Integer, nullable
            preparedStatement.setString(11, commentary.getBody());

            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet keys = preparedStatement.getGeneratedKeys()) {
                    if (keys.next()) {
                        commentary.setCommentaryId(keys.getInt(1));
                    }
                }
                System.out.println("SqlConnectorCommentaryTable.insert - inserted commentaryId=" + commentary.getCommentaryId());
                return true;
            }
            System.out.println("SqlConnectorCommentaryTable.insert - no rows affected");
            return false;

        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.insert - Error inserting commentary: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Dedupe check backed by idx_dedupe. Returns true if a row already exists for
     * the (season, kothSeason, week, gameId, eventType) tuple. Uses the null-safe
     * equality operator (&lt;=&gt;) so NULL kothSeason/gameId/eventType match correctly.
     */
    public boolean findByDedupeKey(int season, String kothSeason, int week, Integer gameId, String eventType) {
        String sql = "SELECT COUNT(*) FROM KOTH.Commentary "
                + "WHERE season = ? AND kothSeason <=> ? AND week = ? AND gameId <=> ? AND eventType <=> ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, season);
            preparedStatement.setString(2, kothSeason);
            preparedStatement.setInt(3, week);
            preparedStatement.setObject(4, gameId);   // Integer, nullable
            preparedStatement.setString(5, eventType);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
            return false;

        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.findByDedupeKey - Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sum prompt/response tokens for rows created today (server date). Backs the
     * daily cost cap in CommentaryService.dailyCostCapExceeded(). Uses idx_costcap.
     */
    public TokenTotals sumTokensToday() {
        String sql = "SELECT COALESCE(SUM(promptTokens), 0) AS p, COALESCE(SUM(responseTokens), 0) AS r "
                + "FROM KOTH.Commentary WHERE createdAt >= CURDATE()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            if (resultSet.next()) {
                return new TokenTotals(resultSet.getLong("p"), resultSet.getLong("r"));
            }
            return new TokenTotals(0, 0);

        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.sumTokensToday - Error: " + e.getMessage());
            e.printStackTrace();
            // Fail safe: report zero spend so a transient DB error doesn't wedge the feature.
            return new TokenTotals(0, 0);
        }
    }

    /**
     * Stream-aware dedupe: has a row of this streamType already been generated
     * for the week? (idx_dedupe alone can't distinguish TEST from RECAP since
     * both carry NULL gameId/eventType.) Used by CommentaryScheduler.
     */
    public boolean hasCommentary(int season, int week, String streamType) {
        String sql = "SELECT COUNT(*) FROM KOTH.Commentary WHERE season = ? AND week = ? AND streamType = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            ps.setString(3, streamType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.hasCommentary - Error: " + e.getMessage());
            return true; // fail safe — assume already generated (don't double-spend)
        }
    }

    /**
     * One row per pick for the given week with the game outcome attached —
     * the raw material for the Week Recap prompt (Commentary M2).
     * Keys: username, firstName, selectedTeam, homeTeamName, awayTeamName,
     *       homeScore, awayScore, status
     */
    public List<java.util.Map<String, Object>> getWeekPickOutcomes(int season, int week) {
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT u.username, u.firstName, p.selectedTeam, " +
                     "g.homeTeamName, g.awayTeamName, g.homeScore, g.awayScore, g.status " +
                     "FROM KOTH.Picks p " +
                     "JOIN KOTH.User u ON u.idUser = p.userId " +
                     "JOIN KOTH.Game g ON g.GameID = p.gameId " +
                     "WHERE p.season = ? AND p.week = ? " +
                     "ORDER BY u.username, p.pickID";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("username", rs.getString("username"));
                    row.put("firstName", rs.getString("firstName"));
                    row.put("selectedTeam", rs.getString("selectedTeam"));
                    row.put("homeTeamName", rs.getString("homeTeamName"));
                    row.put("awayTeamName", rs.getString("awayTeamName"));
                    row.put("homeScore", rs.getInt("homeScore"));
                    row.put("awayScore", rs.getInt("awayScore"));
                    row.put("status", rs.getString("status"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.getWeekPickOutcomes - Error: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Season standings for the recap: per user, initial picks and season-to-date
     * losses (a tie is a loss — same rule as CommonProcessingService.isWinningPick).
     * Keys: username, firstName, initialPicks, losses, remaining
     */
    public List<java.util.Map<String, Object>> getSeasonStandings(int season) {
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT u.username, u.firstName, COALESCE(u.initialPicks,0) AS initialPicks, " +
                     "SUM(CASE WHEN g.status IN ('STATUS_FINAL','Final','F/OT') AND ( " +
                     "  (p.selectedTeam = g.homeTeamName AND g.homeScore <= g.awayScore) OR " +
                     "  (p.selectedTeam = g.awayTeamName AND g.awayScore <= g.homeScore) " +
                     ") THEN 1 ELSE 0 END) AS losses " +
                     "FROM KOTH.User u " +
                     "LEFT JOIN KOTH.Picks p ON p.userId = u.idUser AND p.season = ? " +
                     "LEFT JOIN KOTH.Game g ON g.GameID = p.gameId " +
                     "WHERE u.picksSeason = ? " +
                     "GROUP BY u.idUser, u.username, u.firstName, u.initialPicks " +
                     "ORDER BY u.username";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    int initial = rs.getInt("initialPicks");
                    int losses = rs.getInt("losses");
                    row.put("username", rs.getString("username"));
                    row.put("firstName", rs.getString("firstName"));
                    row.put("initialPicks", initial);
                    row.put("losses", losses);
                    row.put("remaining", Math.max(0, initial - losses));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.getSeasonStandings - Error: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Detector feed (M3): one row per pick for the week with live game state.
     * Reads the RAW game team-name columns (which picks.selectedTeam matches),
     * not the Teams-join short names.
     * Keys: idUser, username, firstName, selectedTeam, gameId, homeTeamName,
     *       awayTeamName, homeScore, awayScore, status, period, displayClock,
     *       pointSpread (nullable Double, home-relative)
     */
    public List<java.util.Map<String, Object>> getWeekPicksWithGameState(int season, int week) {
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT u.idUser, u.username, u.firstName, p.selectedTeam, g.GameID, " +
                     "g.homeTeamName, g.awayTeamName, g.homeScore, g.awayScore, " +
                     "g.status, g.period, g.displayClock, g.pointSpread " +
                     "FROM KOTH.Picks p " +
                     "JOIN KOTH.User u ON u.idUser = p.userId " +
                     "JOIN KOTH.Game g ON g.GameID = p.gameId " +
                     "WHERE p.season = ? AND p.week = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("idUser", rs.getInt("idUser"));
                    row.put("username", rs.getString("username"));
                    row.put("firstName", rs.getString("firstName"));
                    row.put("selectedTeam", rs.getString("selectedTeam"));
                    row.put("gameId", rs.getInt("GameID"));
                    row.put("homeTeamName", rs.getString("homeTeamName"));
                    row.put("awayTeamName", rs.getString("awayTeamName"));
                    row.put("homeScore", rs.getInt("homeScore"));
                    row.put("awayScore", rs.getInt("awayScore"));
                    row.put("status", rs.getString("status"));
                    row.put("period", rs.getString("period"));
                    row.put("displayClock", rs.getString("displayClock"));
                    double spread = rs.getDouble("pointSpread");
                    row.put("pointSpread", rs.wasNull() ? null : spread);
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.getWeekPicksWithGameState - Error: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Lightweight per-game state for the week (ALL games, picked or not):
     * used for the game-window check and LAST_STAND's "only one game left".
     * Keys: gameId, status, date (ISO-8601 UTC string), period, displayClock
     */
    public List<java.util.Map<String, Object>> getWeekGameStates(int season, int week) {
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT GameID, status, date, period, displayClock FROM KOTH.Game WHERE season = ? AND week = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("gameId", rs.getInt("GameID"));
                    row.put("status", rs.getString("status"));
                    row.put("date", rs.getString("date"));
                    row.put("period", rs.getString("period"));
                    row.put("displayClock", rs.getString("displayClock"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.getWeekGameStates - Error: " + e.getMessage());
        }
        return rows;
    }

    private static final String SELECT_COLUMNS =
            "commentaryId, season, kothSeason, week, streamType, eventType, affectedUserIds, "
            + "gameId, snarkLevel, promptTokens, responseTokens, body, createdAt";

    /**
     * Most-recent commentary for a season, newest first (createdAt desc, id desc
     * to break same-second ties). Used by the Commentary page timeline.
     */
    public List<Commentary> getRecentCommentary(int season, int limit) {
        List<Commentary> out = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLUMNS + " FROM KOTH.Commentary WHERE season = ? "
                + "ORDER BY createdAt DESC, commentaryId DESC LIMIT ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, season);
            preparedStatement.setInt(2, limit);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorCommentaryTable.getRecentCommentary - Error: " + e.getMessage());
            e.printStackTrace();
        }
        return out;
    }

    /** The single newest commentary row for a season, or null if none. Backs the Home blurb. */
    public Commentary getLatestForSeason(int season) {
        List<Commentary> recent = getRecentCommentary(season, 1);
        return recent.isEmpty() ? null : recent.get(0);
    }

    private Commentary mapRow(ResultSet rs) throws SQLException {
        Commentary c = new Commentary();
        c.setCommentaryId(rs.getInt("commentaryId"));
        c.setSeason(rs.getInt("season"));
        c.setKothSeason(rs.getString("kothSeason"));
        c.setWeek(rs.getInt("week"));
        c.setStreamType(rs.getString("streamType"));
        c.setEventType(rs.getString("eventType"));
        c.setAffectedUserIds(rs.getString("affectedUserIds"));
        int gameId = rs.getInt("gameId");
        c.setGameId(rs.wasNull() ? null : gameId);
        c.setSnarkLevel(rs.getInt("snarkLevel"));
        int promptTokens = rs.getInt("promptTokens");
        c.setPromptTokens(rs.wasNull() ? null : promptTokens);
        int responseTokens = rs.getInt("responseTokens");
        c.setResponseTokens(rs.wasNull() ? null : responseTokens);
        c.setBody(rs.getString("body"));
        c.setCreatedAt(rs.getTimestamp("createdAt"));
        return c;
    }
}
