package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.ElwayProjection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/** JDBC for KOTH.ElwayProjection (Nate Silver ELWAY game projections). */
@Component
public class SqlConnectorElwayTable {

    @Autowired
    private DataSource dataSource;

    /** Upsert a batch of projections (unique on season+week+home+away). Returns rows written. */
    public int upsertProjections(List<ElwayProjection> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        String sql =
            "INSERT INTO KOTH.ElwayProjection " +
            "(season, week, homeTeamId, awayTeamId, homeWinProb, homeSpread, total, neutralSite) " +
            "VALUES (?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE homeWinProb=VALUES(homeWinProb), homeSpread=VALUES(homeSpread), " +
            " total=VALUES(total), neutralSite=VALUES(neutralSite), importedAt=CURRENT_TIMESTAMP";
        int written = 0;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (ElwayProjection p : rows) {
                ps.setInt(1, p.getSeason());
                ps.setInt(2, p.getWeek());
                ps.setInt(3, p.getHomeTeamId());
                ps.setInt(4, p.getAwayTeamId());
                ps.setDouble(5, p.getHomeWinProb());
                if (p.getHomeSpread() != null) ps.setDouble(6, p.getHomeSpread()); else ps.setNull(6, java.sql.Types.DECIMAL);
                if (p.getTotal() != null)      ps.setDouble(7, p.getTotal());      else ps.setNull(7, java.sql.Types.DECIMAL);
                ps.setBoolean(8, p.isNeutralSite());
                ps.addBatch();
            }
            int[] res = ps.executeBatch();
            written = res.length;
            System.out.println("SqlConnectorElwayTable.upsertProjections: wrote " + written + " row(s)");
        } catch (SQLException e) {
            System.err.println("SqlConnectorElwayTable.upsertProjections error: " + e.getMessage());
            e.printStackTrace();
        }
        return written;
    }

    /** Projections for a week keyed "homeId-awayId". */
    public Map<String, ElwayProjection> getProjectionsForWeek(int season, int week) {
        Map<String, ElwayProjection> map = new HashMap<>();
        String sql = "SELECT * FROM KOTH.ElwayProjection WHERE season=? AND week=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ElwayProjection p = new ElwayProjection();
                    p.setSeason(rs.getInt("season"));
                    p.setWeek(rs.getInt("week"));
                    p.setHomeTeamId(rs.getInt("homeTeamId"));
                    p.setAwayTeamId(rs.getInt("awayTeamId"));
                    p.setHomeWinProb(rs.getDouble("homeWinProb"));
                    double sp = rs.getDouble("homeSpread"); p.setHomeSpread(rs.wasNull() ? null : sp);
                    double to = rs.getDouble("total");      p.setTotal(rs.wasNull() ? null : to);
                    p.setNeutralSite(rs.getBoolean("neutralSite"));
                    map.put(p.getHomeTeamId() + "-" + p.getAwayTeamId(), p);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorElwayTable.getProjectionsForWeek error: " + e.getMessage());
        }
        return map;
    }

    /** Import status for the Edge page: total rows, weeks covered, most recent import time. */
    public Map<String, Object> getImportStatus(int season) {
        Map<String, Object> status = new HashMap<>();
        List<Integer> weeks = new ArrayList<>();
        int rows = 0;
        Timestamp last = null;
        String sql = "SELECT week, COUNT(*) AS n, MAX(importedAt) AS latest " +
                     "FROM KOTH.ElwayProjection WHERE season=? GROUP BY week ORDER BY week";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    weeks.add(rs.getInt("week"));
                    rows += rs.getInt("n");
                    Timestamp t = rs.getTimestamp("latest");
                    if (t != null && (last == null || t.after(last))) last = t;
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorElwayTable.getImportStatus error: " + e.getMessage());
        }
        status.put("rows", rows);
        status.put("weeks", weeks);
        status.put("lastImport", last);
        return status;
    }
}
