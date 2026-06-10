package helpers;

import model.SmsNotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for user SMS preferences, phone-verification status, and notification
 * idempotency. Ported from GolferFest's SmsPreferencesDAO, adapted to KOTH:
 * season-scoped recipients (no group abstraction; KOTH.User.picksSeason) and a
 * (season, week, type_key, ref_key) idempotency log.
 *
 * Backing tables (KOTH schema, see KOTH/db/sms_schema.sql):
 *  - sms_notification_types(notification_type_id, type_key, display_name,
 *      description, category 'USER'|'COMMISH', default_enabled, active, sort_order)
 *  - user_sms_preferences(idUser, notification_type_id, enabled, updated_date)
 *  - sms_notification_log(id, season, week, type_key, ref_key, sent_at)
 *  - User(idUser, cellNumber, phoneVerified, phoneVerifiedDate, picksSeason, commish)
 */
@Component
public class SmsPreferencesDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // -------------------------------------------------------------------------
    // Preference checks
    // -------------------------------------------------------------------------

    /** Should this user receive this notification type? Commissioner types only
     *  require a verified phone; user types also require opt-in. */
    public boolean shouldSendNotification(int idUser, SmsNotificationType type) {
        if (type.isCommissionerControlled()) {
            return isPhoneVerified(idUser);
        }
        String sql =
            "SELECT COALESCE(usp.enabled, snt.default_enabled) AS enabled " +
            "FROM KOTH.sms_notification_types snt " +
            "LEFT JOIN KOTH.user_sms_preferences usp " +
            "  ON snt.notification_type_id = usp.notification_type_id AND usp.idUser = ? " +
            "WHERE snt.type_key = ? AND snt.active = 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("enabled") && isPhoneVerified(idUser);
                }
                return false;
            }
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.shouldSendNotification - Error: " + e.getMessage());
            return false; // fail safe — don't send
        }
    }

    /** USER-category preferences with display info, for the settings page. */
    public List<Map<String, Object>> getUserPreferencesDetail(int idUser) {
        List<Map<String, Object>> prefs = new ArrayList<>();
        String sql =
            "SELECT snt.type_key, snt.display_name, snt.description, " +
            "       COALESCE(usp.enabled, snt.default_enabled) AS enabled " +
            "FROM KOTH.sms_notification_types snt " +
            "LEFT JOIN KOTH.user_sms_preferences usp " +
            "  ON snt.notification_type_id = usp.notification_type_id AND usp.idUser = ? " +
            "WHERE snt.category = 'USER' AND snt.active = 1 " +
            "ORDER BY snt.sort_order";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("typeKey", rs.getString("type_key"));
                    p.put("displayName", rs.getString("display_name"));
                    p.put("description", rs.getString("description"));
                    p.put("enabled", rs.getBoolean("enabled"));
                    prefs.add(p);
                }
            }
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.getUserPreferencesDetail - Error: " + e.getMessage());
        }
        return prefs;
    }

    public Map<String, Boolean> getUserPreferences(int idUser) {
        Map<String, Boolean> prefs = new LinkedHashMap<>();
        for (Map<String, Object> d : getUserPreferencesDetail(idUser)) {
            prefs.put((String) d.get("typeKey"), (Boolean) d.get("enabled"));
        }
        return prefs;
    }

    public void updatePreference(int idUser, String typeKey, boolean enabled) {
        String sql =
            "INSERT INTO KOTH.user_sms_preferences (idUser, notification_type_id, enabled) " +
            "SELECT ?, notification_type_id, ? FROM KOTH.sms_notification_types WHERE type_key = ? " +
            "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_date = NOW()";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            ps.setBoolean(2, enabled);
            ps.setString(3, typeKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.updatePreference - Error: " + e.getMessage());
        }
    }

    public void updatePreferences(int idUser, Map<String, Boolean> preferences) {
        for (Map.Entry<String, Boolean> e : preferences.entrySet()) {
            updatePreference(idUser, e.getKey(), e.getValue());
        }
    }

    public void initializeDefaultPreferences(int idUser) {
        String sql =
            "INSERT IGNORE INTO KOTH.user_sms_preferences (idUser, notification_type_id, enabled) " +
            "SELECT ?, notification_type_id, default_enabled " +
            "FROM KOTH.sms_notification_types WHERE category = 'USER' AND active = 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.initializeDefaultPreferences - Error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Phone verification
    // -------------------------------------------------------------------------

    public boolean isPhoneVerified(int idUser) {
        String sql = "SELECT phoneVerified FROM KOTH.User WHERE idUser = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("phoneVerified");
            }
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.isPhoneVerified - Error: " + e.getMessage());
            return false;
        }
    }

    public void markPhoneVerified(int idUser, String phoneNumber) {
        String sql = "UPDATE KOTH.User SET cellNumber = ?, phoneVerified = 1, phoneVerifiedDate = NOW() WHERE idUser = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            ps.setInt(2, idUser);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.markPhoneVerified - Error: " + e.getMessage());
        }
    }

    public void clearPhoneVerification(int idUser) {
        String sql = "UPDATE KOTH.User SET phoneVerified = 0, phoneVerifiedDate = NULL WHERE idUser = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUser);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.clearPhoneVerification - Error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Recipients
    // -------------------------------------------------------------------------

    /** Global recipients for a type (all verified, opted-in users, any season). */
    public List<UserPhone> getRecipientsForNotification(SmsNotificationType type) {
        return queryRecipients(type, null, false);
    }

    /** Recipients within one season's pool. If {@code commishOnly}, restrict to
     *  commissioners (User.commish = 1). */
    public List<UserPhone> getSeasonRecipients(SmsNotificationType type, int season, boolean commishOnly) {
        return queryRecipients(type, season, commishOnly);
    }

    private List<UserPhone> queryRecipients(SmsNotificationType type, Integer season, boolean commishOnly) {
        List<UserPhone> recipients = new ArrayList<>();
        boolean userControlled = type.isUserControlled();

        StringBuilder sql = new StringBuilder("SELECT DISTINCT u.idUser, u.cellNumber FROM KOTH.User u ");
        if (userControlled) {
            sql.append("JOIN KOTH.sms_notification_types snt ON snt.type_key = ? AND snt.active = 1 ");
            sql.append("LEFT JOIN KOTH.user_sms_preferences usp " +
                       "ON usp.idUser = u.idUser AND usp.notification_type_id = snt.notification_type_id ");
        }
        sql.append("WHERE u.phoneVerified = 1 AND u.cellNumber IS NOT NULL ");
        if (season != null) {
            sql.append("AND u.picksSeason = ? ");
        }
        if (commishOnly) {
            sql.append("AND u.commish = 1 ");
        }
        if (userControlled) {
            sql.append("AND COALESCE(usp.enabled, snt.default_enabled) = 1 ");
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (userControlled) ps.setString(idx++, type.name());
            if (season != null) ps.setInt(idx++, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipients.add(new UserPhone(rs.getInt("idUser"), rs.getString("cellNumber")));
                }
            }
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.queryRecipients - Error: " + e.getMessage());
        }
        return recipients;
    }

    // -------------------------------------------------------------------------
    // Idempotency log: returns true exactly once per (season, week, typeKey, refKey)
    // -------------------------------------------------------------------------

    public boolean claim(int season, int week, String typeKey, String refKey) {
        String sql = "INSERT IGNORE INTO KOTH.sms_notification_log " +
                     "(season, week, type_key, ref_key, sent_at) VALUES (?, ?, ?, ?, NOW())";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, season);
            ps.setInt(2, week);
            ps.setString(3, typeKey);
            ps.setString(4, refKey == null ? "" : refKey);
            return ps.executeUpdate() > 0; // 0 = duplicate (already claimed)
        } catch (SQLException e) {
            System.err.println("SmsPreferencesDAO.claim - Error: " + e.getMessage());
            return false; // fail safe — treat as already claimed (don't spam)
        }
    }

    // Simple holder for user ID + phone number
    public static class UserPhone {
        public final int userId;
        public final String phoneNumber;

        public UserPhone(int userId, String phoneNumber) {
            this.userId = userId;
            this.phoneNumber = phoneNumber;
        }
    }
}
