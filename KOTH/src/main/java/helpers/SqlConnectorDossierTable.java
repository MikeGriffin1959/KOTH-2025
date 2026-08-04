package helpers;

import model.PoolDossier;
import model.UserDossier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the dossier tables (Commentary M5). Matches the SqlConnector style.
 * Tables: KOTH.user_dossier, KOTH.pool_dossier (see KOTH/db/dossier_schema.sql).
 */
@Component
public class SqlConnectorDossierTable {

    @Autowired
    private DataSource dataSource;

    /** All user dossiers for a season, joined with User for names — one row per
     *  season user (LEFT JOIN: users without a dossier yet get an empty shell). */
    public List<UserDossier> getUserDossiersForSeason(int season) {
        List<UserDossier> out = new ArrayList<>();
        String sql = "SELECT u.idUser, u.username, u.firstName, " +
                     "d.dossierId, d.kothSeason, d.displayName, d.personality, d.rivalries, d.sensitivities " +
                     "FROM KOTH.User u " +
                     "LEFT JOIN KOTH.user_dossier d ON d.userId = u.idUser AND d.season = ? " +
                     "WHERE u.picksSeason = ? " +
                     "ORDER BY u.firstName, u.username";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserDossier d = new UserDossier();
                    d.setUserId(rs.getInt("idUser"));
                    d.setUsername(rs.getString("username"));
                    d.setFirstName(rs.getString("firstName"));
                    d.setSeason(season);
                    d.setDossierId(rs.getInt("dossierId"));
                    d.setKothSeason(rs.getString("kothSeason"));
                    d.setDisplayName(rs.getString("displayName"));
                    d.setPersonality(rs.getString("personality"));
                    d.setRivalries(rs.getString("rivalries"));
                    d.setSensitivities(rs.getString("sensitivities"));
                    out.add(d);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorDossierTable.getUserDossiersForSeason - Error: " + e.getMessage());
        }
        return out;
    }

    /** Upsert one user's dossier for a season (unique key userId+season+kothSeason). */
    public boolean upsertUserDossier(int userId, int season, String kothSeason,
                                     String displayName, String personality,
                                     String rivalries, String sensitivities) {
        String sql = "INSERT INTO KOTH.user_dossier " +
                     "(userId, season, kothSeason, displayName, personality, rivalries, sensitivities) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE displayName = VALUES(displayName), " +
                     "personality = VALUES(personality), rivalries = VALUES(rivalries), " +
                     "sensitivities = VALUES(sensitivities)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, season);
            ps.setString(3, kothSeason);
            ps.setString(4, emptyToNull(displayName));
            ps.setString(5, emptyToNull(personality));
            ps.setString(6, emptyToNull(rivalries));
            ps.setString(7, emptyToNull(sensitivities));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("SqlConnectorDossierTable.upsertUserDossier - Error: " + e.getMessage());
            return false;
        }
    }

    /** The season's pool dossier, or null if none saved yet. */
    public PoolDossier getPoolDossier(int season) {
        String sql = "SELECT season, kothSeason, poolIdentity, poolHistory, poolLore, " +
                     "commissionerNotes, toneGuidance FROM KOTH.pool_dossier WHERE season = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, season);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PoolDossier d = new PoolDossier();
                    d.setSeason(rs.getInt("season"));
                    d.setKothSeason(rs.getString("kothSeason"));
                    d.setPoolIdentity(rs.getString("poolIdentity"));
                    d.setPoolHistory(rs.getString("poolHistory"));
                    d.setPoolLore(rs.getString("poolLore"));
                    d.setCommissionerNotes(rs.getString("commissionerNotes"));
                    d.setToneGuidance(rs.getString("toneGuidance"));
                    return d;
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorDossierTable.getPoolDossier - Error: " + e.getMessage());
        }
        return null;
    }

    public boolean upsertPoolDossier(int season, String kothSeason, String poolIdentity,
                                     String poolHistory, String poolLore,
                                     String commissionerNotes, String toneGuidance) {
        String sql = "INSERT INTO KOTH.pool_dossier " +
                     "(season, kothSeason, poolIdentity, poolHistory, poolLore, commissionerNotes, toneGuidance) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE poolIdentity = VALUES(poolIdentity), " +
                     "poolHistory = VALUES(poolHistory), poolLore = VALUES(poolLore), " +
                     "commissionerNotes = VALUES(commissionerNotes), toneGuidance = VALUES(toneGuidance)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setString(2, kothSeason == null ? "" : kothSeason);
            ps.setString(3, emptyToNull(poolIdentity));
            ps.setString(4, emptyToNull(poolHistory));
            ps.setString(5, emptyToNull(poolLore));
            ps.setString(6, emptyToNull(commissionerNotes));
            ps.setString(7, emptyToNull(toneGuidance));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("SqlConnectorDossierTable.upsertPoolDossier - Error: " + e.getMessage());
            return false;
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
