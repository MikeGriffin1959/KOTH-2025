package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO for the KOTH.remember_me_tokens table.
 *
 * Replaces the single rememberMeToken column on KOTH.User so that each
 * browser / device can hold its own independent remember-me token.
 *
 * Mirrors the Golf app design. Uses the same raw-JDBC + @Autowired DataSource
 * style as SqlConnectorUserTable, with the KOTH. schema prefix throughout.
 */
@Component
public class SqlConnectorRememberMeTokenTable {

    @Autowired
    private DataSource dataSource;

    // Insert a new token for a user, valid for the given number of days (LoginServlet)
    public void insertToken(int userId, String token, int daysValid) throws SQLException {
        System.out.println("SqlConnectorRememberMeTokenTable.insertToken called for userId: " + userId);
        String sql = "INSERT INTO KOTH.remember_me_tokens (user_id, token, expires_at) " +
                     "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? DAY))";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);
            preparedStatement.setString(2, token);
            preparedStatement.setInt(3, daysValid);

            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Inserting remember-me token failed, no rows affected.");
            }
        }
    }

    // Look up the user for a token, only if the token has not expired (LoginServlet doGet)
    public User getUserByToken(String token) throws SQLException {
        System.out.println("SqlConnectorRememberMeTokenTable.getUserByToken called");
        String sql = "SELECT u.idUser, u.firstName, u.lastName, u.userName, u.email, u.cellNumber, u.commish " +
                     "FROM KOTH.User u " +
                     "JOIN KOTH.remember_me_tokens t ON u.idUser = t.user_id " +
                     "WHERE t.token = ? AND t.expires_at > NOW()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, token);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setFirstName(resultSet.getString("firstName"));
                    user.setLastName(resultSet.getString("lastName"));
                    user.setUsername(resultSet.getString("userName"));
                    user.setEmail(resultSet.getString("email"));
                    user.setCellNumber(resultSet.getString("cellNumber"));
                    user.setCommish(resultSet.getBoolean("commish"));
                    System.out.println("SqlConnectorRememberMeTokenTable: Found user: " + user.getUsername());
                    return user;
                }
            }
        }
        return null;
    }

    // Delete a single token (logout on this device only) (LogoutServlet)
    public void deleteToken(String token) throws SQLException {
        System.out.println("SqlConnectorRememberMeTokenTable.deleteToken called");
        String sql = "DELETE FROM KOTH.remember_me_tokens WHERE token = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, token);
            preparedStatement.executeUpdate();
        }
    }

    // Delete all tokens for a user (force logout on every device)
    public void deleteTokensByUserId(int userId) throws SQLException {
        System.out.println("SqlConnectorRememberMeTokenTable.deleteTokensByUserId called for userId: " + userId);
        String sql = "DELETE FROM KOTH.remember_me_tokens WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            preparedStatement.executeUpdate();
        }
    }

    // Housekeeping: purge expired tokens (optional, e.g. from a daily task)
    public void deleteExpiredTokens() throws SQLException {
        System.out.println("SqlConnectorRememberMeTokenTable.deleteExpiredTokens called");
        String sql = "DELETE FROM KOTH.remember_me_tokens WHERE expires_at < NOW()";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("SqlConnectorRememberMeTokenTable: Purged " + rowsAffected + " expired tokens");
        }
    }

    // Count a user's active (non-expired) tokens. Useful for limiting max devices.
    public int countActiveTokensForUser(int userId) {
        System.out.println("SqlConnectorRememberMeTokenTable.countActiveTokensForUser called for userId: " + userId);
        String sql = "SELECT COUNT(*) FROM KOTH.remember_me_tokens WHERE user_id = ? AND expires_at > NOW()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorRememberMeTokenTable: Error counting tokens for userId " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }
}