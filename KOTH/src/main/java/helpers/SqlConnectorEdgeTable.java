package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.Game;
import model.GameEdge;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * JDBC for the KOTH Edge tables (EdgeSnapshot, EloRating, EdgeRecommendation)
 * plus the read of historical finals used to bootstrap ELO.
 * Mirrors the style of SqlConnectorGameTable / SqlConnectorPicksTable.
 */
@Component
public class SqlConnectorEdgeTable {

    @Autowired
    private DataSource dataSource;

    /**
     * All completed games across all seasons, ordered for ELO replay.
     * Equivalent to getFinalsBefore(Integer.MAX_VALUE, Integer.MAX_VALUE).
     */
    public List<Game> getAllFinalsAsGames() {
        return getFinalsBefore(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Completed games strictly BEFORE the given (season, internalWeek), ordered for
     * ELO replay. "Before" means: any earlier season in full, plus earlier weeks of
     * the target season. This prevents lookahead leakage when bootstrapping ELO to
     * rate an upcoming week (critical for an honest backtest).
     *
     * @param beforeSeason the target season
     * @param beforeWeek   the target internal week (games in this week and later are excluded for this season)
     */
    public List<Game> getFinalsBefore(int beforeSeason, int beforeWeek) {
        System.out.println("SqlConnectorEdgeTable.getFinalsBefore started (< " + beforeSeason + "/wk" + beforeWeek + ")");
        List<Game> games = new ArrayList<>();
        String sql =
            "SELECT GameID, season, week, date, status, homeTeamId, awayTeamId, homeScore, awayScore " +
            "FROM KOTH.Game " +
            "WHERE status IN ('STATUS_FINAL','Final','F/OT') " +
            "  AND homeTeamId IS NOT NULL AND awayTeamId IS NOT NULL " +
            "  AND homeTeamId <> 0 AND awayTeamId <> 0 " +
            "  AND ( season < ? OR (season = ? AND week < ?) ) " +
            "ORDER BY season, week, date";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, beforeSeason);
            ps.setInt(2, beforeSeason);
            ps.setInt(3, beforeWeek);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Game g = new Game();
                    g.setGameID(rs.getLong("GameID"));
                    g.setSeason(rs.getInt("season"));
                    g.setWeek(rs.getInt("week"));
                    g.setDate(rs.getString("date"));
                    g.setStatus(rs.getString("status"));
                    g.setHomeTeamId(rs.getInt("homeTeamId"));
                    g.setAwayTeamId(rs.getInt("awayTeamId"));
                    g.setHomeScore(rs.getInt("homeScore"));
                    g.setAwayScore(rs.getInt("awayScore"));
                    games.add(g);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorEdgeTable.getFinalsBefore error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("SqlConnectorEdgeTable.getFinalsBefore: " + games.size() + " finals");
        return games;
    }

    /** Insert or update one EdgeSnapshot row (unique on season+internalWeek+espnEventId). */
    public void upsertSnapshot(GameEdge e) {
        String sql =
            "INSERT INTO KOTH.EdgeSnapshot " +
            "(season, internalWeek, espnEventId, homeTeamId, awayTeamId, kickoffUtc, neutralSite, " +
            " spread, favoriteIsHome, marketHome, marketAway, fpiHome, fpiAway, eloHome, eloAway, " +
            " blendedHome, blendedAway, predPtDiffHome) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE " +
            " homeTeamId=VALUES(homeTeamId), awayTeamId=VALUES(awayTeamId), kickoffUtc=VALUES(kickoffUtc), " +
            " neutralSite=VALUES(neutralSite), spread=VALUES(spread), favoriteIsHome=VALUES(favoriteIsHome), " +
            " marketHome=VALUES(marketHome), marketAway=VALUES(marketAway), " +
            " fpiHome=VALUES(fpiHome), fpiAway=VALUES(fpiAway), " +
            " eloHome=VALUES(eloHome), eloAway=VALUES(eloAway), " +
            " blendedHome=VALUES(blendedHome), blendedAway=VALUES(blendedAway), " +
            " predPtDiffHome=VALUES(predPtDiffHome)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, e.getSeason());
            ps.setInt(2, e.getInternalWeek());
            ps.setLong(3, e.getEspnEventId());
            ps.setInt(4, e.getHomeTeamId());
            ps.setInt(5, e.getAwayTeamId());
            if (e.getKickoffUtc() != null) ps.setString(6, e.getKickoffUtc());
            else ps.setNull(6, java.sql.Types.TIMESTAMP);
            ps.setBoolean(7, e.isNeutralSite());
            setNullableDouble(ps, 8, e.getSpread());
            if (e.getFavoriteIsHome() != null) ps.setBoolean(9, e.getFavoriteIsHome());
            else ps.setNull(9, java.sql.Types.BOOLEAN);
            setNullableDouble(ps, 10, e.getMarketHome());
            setNullableDouble(ps, 11, e.getMarketAway());
            setNullableDouble(ps, 12, e.getFpiHome());
            setNullableDouble(ps, 13, e.getFpiAway());
            setNullableDouble(ps, 14, e.getEloHome());
            setNullableDouble(ps, 15, e.getEloAway());
            setNullableDouble(ps, 16, e.getBlendedHome());
            setNullableDouble(ps, 17, e.getBlendedAway());
            setNullableDouble(ps, 18, e.getPredPtDiffHome());
            ps.executeUpdate();
            System.out.println("DEBUG[edge-persist]: snapshot saved " + e);
        } catch (SQLException ex) {
            System.err.println("SqlConnectorEdgeTable.upsertSnapshot error for event " +
                               e.getEspnEventId() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Persist the current ELO ratings for all teams as of a given week. */
    public void upsertEloRatings(int season, int throughWeek, Map<Integer, Double> ratings) {
        String sql =
            "INSERT INTO KOTH.EloRating (season, throughWeek, teamId, rating) VALUES (?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE rating=VALUES(rating), updatedAt=CURRENT_TIMESTAMP";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<Integer, Double> en : ratings.entrySet()) {
                ps.setInt(1, season);
                ps.setInt(2, throughWeek);
                ps.setInt(3, en.getKey());
                ps.setDouble(4, en.getValue());
                ps.addBatch();
            }
            int[] res = ps.executeBatch();
            System.out.println("DEBUG[edge-persist]: persisted " + res.length + " ELO ratings (" +
                               season + "/wk" + throughWeek + ")");
        } catch (SQLException e) {
            System.err.println("SqlConnectorEdgeTable.upsertEloRatings error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setNullableDouble(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.DECIMAL);
        else ps.setDouble(idx, v);
    }

    // ════════════════════════════════════════════════════════════
    // M2 reads/writes
    // ════════════════════════════════════════════════════════════

    /**
     * Load the seeded static metadata for all teams, keyed by teamId.
     * Teams may have multiple rows (one per season) in KOTH.Teams; we coalesce
     * to one TeamContext per apiTeamID (last non-null wins), since division/geo
     * are identical across a team's rows.
     */
    public Map<Integer, model.TeamContext> getTeamContexts() {
        Map<Integer, model.TeamContext> map = new HashMap<>();
        String sql = "SELECT DISTINCT apiTeamID, apiTeamShortName, division, conference, lat, lng, dome, tz " +
                     "FROM KOTH.Teams";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("apiTeamID");
                model.TeamContext tc = map.computeIfAbsent(id, k -> {
                    model.TeamContext t = new model.TeamContext();
                    t.setTeamId(id);
                    return t;
                });
                if (rs.getString("apiTeamShortName") != null) tc.setShortName(rs.getString("apiTeamShortName"));
                if (rs.getString("division") != null)   tc.setDivision(rs.getString("division"));
                if (rs.getString("conference") != null) tc.setConference(rs.getString("conference"));
                double lat = rs.getDouble("lat"); if (!rs.wasNull()) tc.setLat(lat);
                double lng = rs.getDouble("lng"); if (!rs.wasNull()) tc.setLng(lng);
                tc.setDome(rs.getBoolean("dome"));
                if (rs.getString("tz") != null) tc.setTz(rs.getString("tz"));
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorEdgeTable.getTeamContexts error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("DEBUG[edge]: loaded " + map.size() + " team contexts");
        return map;
    }

    /** Read back the EdgeSnapshot rows for a week as GameEdge objects. */
    public List<GameEdge> getSnapshotsForWeek(int season, int internalWeek) {
        List<GameEdge> list = new ArrayList<>();
        String sql = "SELECT * FROM KOTH.EdgeSnapshot WHERE season=? AND internalWeek=? ORDER BY kickoffUtc";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, internalWeek);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GameEdge e = new GameEdge();
                    e.setEspnEventId(rs.getLong("espnEventId"));
                    e.setSeason(rs.getInt("season"));
                    e.setInternalWeek(rs.getInt("internalWeek"));
                    e.setHomeTeamId(rs.getInt("homeTeamId"));
                    e.setAwayTeamId(rs.getInt("awayTeamId"));
                    e.setKickoffUtc(rs.getString("kickoffUtc"));
                    e.setNeutralSite(rs.getBoolean("neutralSite"));
                    e.setSpread(getNullableDouble(rs, "spread"));
                    boolean favHome = rs.getBoolean("favoriteIsHome");
                    e.setFavoriteIsHome(rs.wasNull() ? null : favHome);
                    e.setMarketHome(getNullableDouble(rs, "marketHome"));
                    e.setFpiHome(getNullableDouble(rs, "fpiHome"));
                    e.setEloHome(getNullableDouble(rs, "eloHome"));
                    e.setBlendedHome(getNullableDouble(rs, "blendedHome"));
                    e.setPredPtDiffHome(getNullableDouble(rs, "predPtDiffHome"));
                    list.add(e);
                }
            }
        } catch (SQLException ex) {
            System.err.println("SqlConnectorEdgeTable.getSnapshotsForWeek error: " + ex.getMessage());
            ex.printStackTrace();
        }
        return list;
    }

    /** Replace this week's recommendations with the supplied ranked list. */
    public void replaceRecommendations(int season, int internalWeek, List<model.EdgeCandidate> ranked) {
        String del = "DELETE FROM KOTH.EdgeRecommendation WHERE season=? AND internalWeek=?";
        String ins = "INSERT INTO KOTH.EdgeRecommendation " +
                "(season, internalWeek, teamId, blendedPwin, safetyScore, rankInWeek, " +
                " upsetRisk, claudeConf, claudeRationale, source) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement d = c.prepareStatement(del)) {
                d.setInt(1, season); d.setInt(2, internalWeek); d.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(ins)) {
                int rank = 1;
                for (model.EdgeCandidate cand : ranked) {
                    p.setInt(1, season);
                    p.setInt(2, internalWeek);
                    p.setInt(3, cand.getTeamId());
                    p.setDouble(4, cand.getBlendedProb() == null ? 0.0 : cand.getBlendedProb());
                    p.setDouble(5, cand.getSafetyScore());
                    p.setInt(6, rank++);
                    p.setNull(7, java.sql.Types.VARCHAR);   // upsetRisk (Claude — M3)
                    p.setNull(8, java.sql.Types.DECIMAL);   // claudeConf
                    p.setNull(9, java.sql.Types.VARCHAR);   // claudeRationale
                    p.setString(10, "auto");
                    p.addBatch();
                }
                p.executeBatch();
            }
            c.commit();
            System.out.println("DEBUG[edge-persist]: wrote " + ranked.size() + " recommendations (" +
                               season + "/wk" + internalWeek + ")");
        } catch (SQLException e) {
            System.err.println("SqlConnectorEdgeTable.replaceRecommendations error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}