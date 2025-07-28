package helpers;

import javax.sql.DataSource; 
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Component
public class SqlConnectorUserTable {

	@Autowired
	private DataSource dataSource;


    // USER TABLE METHODS

    // Method to add a new user (SignUpServlet)
    public int addUser(User user) throws SQLException {
        System.out.println("SqlConnectorUserTable.addUser method called");
        String sql = "INSERT INTO User (firstName, lastName, userName, email, cellNumber, password, picksSeason) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        System.out.println("SqlConnectorUserTable.addUser() started");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setString(1, user.getFirstName());
            preparedStatement.setString(2, user.getLastName());
            preparedStatement.setString(3, user.getUsername());
            preparedStatement.setString(4, user.getEmail());
            preparedStatement.setString(5, user.getCellNumber());
            // Hash the password before storing
            String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
            preparedStatement.setString(6, hashedPassword);
            preparedStatement.setInt(7, user.getPicksSeason());

            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    System.out.println("SqlConnectorUserTable: User added successfully with ID: " + userId);
                    return userId;
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        }
    }

    // Method to check if a username already exists (SignUp & UpdateUserInfo Servlets)
    public boolean usernameExists(String username) {
        System.out.println("SqlConnectorUserTable.usernameExists method started");
        String sql = "SELECT COUNT(*) FROM User WHERE userName = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Method to check user's role(s) (Login Servlet)
    public Map<String, Boolean> getUserRoles(String userName) {
        System.out.println("SqlConnectorUserTable.getUserRoles method started");
        Map<String, Boolean> roles = new HashMap<>();
        roles.put("isCommish", false);

        String sql = "SELECT commish FROM KOTH.User WHERE userName = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, userName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    roles.put("isCommish", resultSet.getBoolean("commish"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("  getUserRoles for " + userName + ", Commish=" + roles.get("isCommish"));
        return roles;
    }

    // Method to update user roles (Commissioner Servlet)
    public boolean updateUserRoles(int userId, boolean commish) {
        System.out.println("SqlConnectorUserTable.updateUserRoles method started");
        String sql = "UPDATE KOTH.User SET commish = ? WHERE idUser = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setBoolean(1, commish);
            preparedStatement.setInt(2, userId);

            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

 // Get all users, their roles, and their initial pick counts for a specific season (Commissioner Servlet)
    public List<User> getAllUsersForSeason(int season) {
        System.out.println("SqlConnectorUserTable.GetAllUsersForSeason method called");
        List<User> users = new ArrayList<>();
        String sql = "SELECT DISTINCT u.idUser, u.firstName, u.lastName, u.username, u.email, " +
                     "u.commish, u.picksPaid, u.initialPicks " +
                     "FROM KOTH.User u " +
                     "WHERE u.picksSeason = ? " +
                     "ORDER BY u.lastName";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, season);
            System.out.println("SqlConnectorUserTable.getAllUsersForSeason - Executing query for season: " + season);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setFirstName(resultSet.getString("firstName"));
                    user.setLastName(resultSet.getString("lastName"));
                    user.setUsername(resultSet.getString("username"));
                    user.setEmail(resultSet.getString("email"));
                    user.setCommish(resultSet.getBoolean("commish"));
                    user.setPicksPaid(resultSet.getBoolean("picksPaid"));
                    user.setInitialPicks(resultSet.getInt("initialPicks"));
                    user.setPicksSeason(season);

                    // Debug logging
                    System.out.println("User " + user.getUsername() + 
                                     " - Season: " + season + 
                                     " - Paid: " + user.getPicksPaid());

                    users.add(user);
                }
                System.out.println("Retrieved " + users.size() + " users for season " + season);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving users for season " + season + ": " + e.getMessage());
            e.printStackTrace();
        }
        return users;
    }

    // Get all users and their initial pick counts for a specific season (DailyCacheUpdateTask)
    public List<User> getCurrentSeasonUsers(int season, int week) {
        System.out.println("SqlConnectorUserTable.getCurrentSeasonUsers() called with season: " + season + ", week: " + week);
        long startTime = System.currentTimeMillis();
        List<User> users = new ArrayList<>();
        String sql = "SELECT DISTINCT idUser, firstName, lastName, username, email, cellNumber " +
                     "FROM KOTH.User " +
//                     "JOIN KOTH.UserPicks up ON u.idUser = up.idUser " +
                     "WHERE picksSeason = ? "
                     ;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, season);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setFirstName(resultSet.getString("firstName"));
                    user.setLastName(resultSet.getString("lastName"));
                    user.setUsername(resultSet.getString("username"));
                    user.setEmail(resultSet.getString("email"));
                    user.setCellNumber(resultSet.getString("cellNumber"));
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("SqlConnectorUserTable.getCurrentSeasonUsers() completed in " + (endTime - startTime) + " ms, retrieved " + users.size() + " users");
        return users;
    }

    // Get user info by username instead of userID (UpdateUserInfo Servlet)
    public User getUserByUsername(String username) {
        System.out.println("SqlConnectorUserTable.getUserByUsername method called");
        String sql = "SELECT idUser, firstName, lastName, userName, email, cellNumber FROM KOTH.User WHERE userName = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setFirstName(resultSet.getString("firstName"));
                    user.setLastName(resultSet.getString("lastName"));
                    user.setUsername(resultSet.getString("userName"));
                    user.setEmail(resultSet.getString("email"));
                    user.setCellNumber(resultSet.getString("cellNumber"));
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorUserTable Error retrieving user by username: " + e.getMessage());
        }
        return null;
    }
    // Method to list all users (ApplicationContextListener)
    public List<User> getAllUsers() {
        System.out.println("SqlConnectorUserTable.getAllUsers method called");
        List<User> users = new ArrayList<>();
        String sql = "SELECT idUser, firstName, lastName, userName, email, cellNumber FROM KOTH.User";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            
            while (resultSet.next()) {
                User user = new User();
                user.setIdUser(resultSet.getInt("idUser"));
                user.setFirstName(resultSet.getString("firstName"));
                user.setLastName(resultSet.getString("lastName"));
                user.setUsername(resultSet.getString("userName"));
                user.setEmail(resultSet.getString("email"));
                user.setCellNumber(resultSet.getString("cellNumber"));
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorUserTable Error retrieving all users: " + e.getMessage());
        }
        return users;
    }

    // Used for updating User info (UpdateUserInfo Servlet)
    public boolean updateUserInfo(String currentUserName, String firstName, String lastName, String newUserName, String email, String cellNumber) {
        System.out.println("SqlConnectorUserTable.updateUserInfo method called");
        String sql = "UPDATE KOTH.User SET firstName = ?, lastName = ?, userName = ?, email = ?, cellNumber = ? WHERE userName = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, firstName);
            preparedStatement.setString(2, lastName);
            preparedStatement.setString(3, newUserName);
            preparedStatement.setString(4, email);
            preparedStatement.setString(5, cellNumber);
            preparedStatement.setString(6, currentUserName);

            int rowsAffected = preparedStatement.executeUpdate();

            System.out.println("SqlConnectorUserTable.updateUserInfo() - Rows affected: " + rowsAffected);

            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorUserTable.updateUserInfo() ERROR: " + e.getMessage());
        }

        return false;
    }
        
 // Method to validate user credentials (Login Servlet)
    public LoginResult isValidUser(String username, String password) {
        System.out.println("SqlConnectorUserTable.isValidUser method called");
        String sql = "SELECT * FROM User WHERE userName = ?";

  //      String sql = "SELECT * FROM User WHERE userName = ? COLLATE utf8mb4_bin";  // Use utf8mb4_bin for case-sensitive comparison
        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                return LoginResult.INVALID_USERNAME;
            }

            String storedPassword = rs.getString("password");
            int userId = rs.getInt("idUser");

            // Check if the stored password is a BCrypt hash
            if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
                // It's a BCrypt hash, use BCrypt to check
                if (BCrypt.checkpw(password, storedPassword)) {
                    return LoginResult.SUCCESS;
                } else {
                    return LoginResult.INVALID_PASSWORD;
                }
            } else {
                // It's not a BCrypt hash, check directly (old method)
                if (storedPassword.equals(password)) {
                    // Password is correct, but we should update to BCrypt
                    String newHash = BCrypt.hashpw(password, BCrypt.gensalt());
                    updatePassword(userId, newHash);
                    return LoginResult.SUCCESS;
                } else {
                    return LoginResult.INVALID_PASSWORD;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return LoginResult.ERROR;
        }
    }
    
    // Method to update a user's password (ResetPasswordServlet)
    public void updatePassword(int userId, String newHash) {
        System.out.println("SqlConnectorUserTable.updatePassword method called");
        String sql = "UPDATE User SET password = ? WHERE idUser = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newHash);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle the exception as appropriate for your application
        }
    }

 // Method to set a reset token for a user (ResetPasswordServlet)
    public void setResetToken(int userId, String token) throws SQLException {
    	System.out.println("SqlConnectorUserTable.setResetToken method initiated");
        String sql = "UPDATE KOTH.User SET resetToken = ? WHERE idUser = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, token);
            preparedStatement.setInt(2, userId);
            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Setting reset token failed, no rows affected.");
            }
        }
    }

    // Method to clear a reset token for a user (ResetPasswordServlet)
    public void clearResetToken(int userId) throws SQLException {
    	System.out.println("SqlConnectorUserTable.clearResetToken method initiated");
        String sql = "UPDATE KOTH.User SET resetToken = NULL WHERE idUser = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Clearing reset token failed, no rows affected.");
            }
        }
    }

    // Method to get a user by email address (ResetPasswordServlet)
    public User getUserByEmail(String email) throws SQLException {
        System.out.println("SqlConnectorUserTable.getUserByEmail method initiated");
        String sql = "SELECT * FROM KOTH.User WHERE email = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setFirstName(resultSet.getString("firstName"));
                    user.setLastName(resultSet.getString("lastName"));
                    user.setUsername(resultSet.getString("userName"));
                    user.setEmail(resultSet.getString("email"));
                    user.setCellNumber(resultSet.getString("cellNumber"));
                    user.setPassword(resultSet.getString("password"));
                    user.setCommish(resultSet.getBoolean("commish"));
                    user.setResetToken(resultSet.getString("resetToken"));
                    return user;
                }
            }
        }
        return null;
    }

    // Method to validate a reset token (ResetPasswordServlet)
    public boolean validateResetToken(String email, String token) throws SQLException {
    	System.out.println("SqlConnectorUserTable.validateResetToken method initiated");
        String sql = "SELECT COUNT(*) FROM KOTH.User WHERE email = ? AND resetToken = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, email);
            preparedStatement.setString(2, token);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    // Method to get a user by reset token (ResetPasswordServlet)
    public User getUserByResetToken(String token) throws SQLException {
    	System.out.println("SqlConnectorUserTable.getUserByResetToken method initiated");
        String sql = "SELECT * FROM KOTH.User WHERE resetToken = ?";
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
                    user.setResetToken(resultSet.getString("resetToken"));
                    return user;
                }
            }
        }
        return null;
    }
    
    // Method to set remember me token (LoginServlet)
    public void setRememberMeToken(int userId, String token) throws SQLException {
        System.out.println("SqlConnectorUserTable.setRememberMeToken method called");
        String sql = "UPDATE KOTH.User SET rememberMeToken = ? WHERE idUser = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, token);
            preparedStatement.setInt(2, userId);
            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Setting RememberMeToken failed, no rows affected.");
            }
        }
    }
    
    // Method to get remember me token (HomeServlet)
    public User getUserByRememberMeToken(String token) throws SQLException {
        System.out.println("SqlConnectorUserTable.getUserByRememberMeToken method called");
        String sql = "SELECT * FROM KOTH.User WHERE RememberMeToken = ?";
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
                    user.setRememberMeToken(resultSet.getString("rememberMeToken"));
                    return user;
                }
            }
        }
        return null;
    }
    
    //method to delete a user from the database (Commissioner Servlet)
    public boolean deleteUser(int userId) {
        System.out.println("SqlConnectorUserTable.deleteUser method called");
        String sql = "DELETE FROM KOTH.User WHERE idUser = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);

            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("SqlConnectorUserTable.deleteUser: Deleted user with ID " + userId);
            
            return rowsAffected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorUserTable.deleteUser: Error deleting user: " + e.getMessage());
            return false;
        }
    } 
    
  //Method to get initial pick count (Commissioner Servlet)
    public int getUserInitialPicks(int userId, int season) {
        System.out.println("SqlConnectorUserTable.getUserInitialPicks method called");
        String sql = "SELECT initialPicks FROM KOTH.User WHERE idUser = ? AND picksSeason = ? ";
        int initialPicks = 0;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, season);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                initialPicks = resultSet.getInt("initialPicks");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return initialPicks;
    }
    
    //Method to get initial pick count (Commissioner Servlet)
    public User getInitialPickCount(int idUser, int season) {
        System.out.println("SqlConnectorUserTable.getInitialPickCount method called");
        String sql = "SELECT * FROM KOTH.User WHERE idUser = ? AND picksSeason = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, idUser);
            preparedStatement.setInt(2, season);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setIdUser(resultSet.getInt("idUser"));
                    user.setPicksSeason(resultSet.getInt("picksSeason"));
                    user.setInitialPicks(resultSet.getInt("initialPicks"));
                    user.setPicksPaid(resultSet.getBoolean("picksPaid"));
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Return null if no record found
    }
    
    //Method to change initial pick count (Commissioner Servlet)
    public void updateUserPicks(User user) {
        System.out.println("SqlConnectorUserTable.updateUserPicks method called");
        String sql = "UPDATE KOTH.User SET initialPicks = ?, picksPaid = ? WHERE idUser = ? AND picksSeason = ?";
        try (Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, user.getInitialPicks());
            preparedStatement.setBoolean(2, user.getPicksPaid());
            preparedStatement.setInt(3, user.getIdUser());
            preparedStatement.setInt(4, user.getPicksSeason());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    //Method to determine if a user has paid for season (Home Servlet)
    public boolean hasUserPaidForSeason(int userId, int season) {
        System.out.println("SqlConnectorUserTable.hasUserPaidForSeason method started");
        String sql = "SELECT picksPaid FROM KOTH.User WHERE idUser = ? AND picksSeason = ?";
        boolean hasPaid = false;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, season);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                hasPaid = resultSet.getBoolean("picksPaid");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("SqlConnectorUserTable.hasUserPaidForSeason method completed");

        return hasPaid;
    }

    
    //Method to update pick count and whether paid (Commissioner Servlets)
    public void addUserPicks(User user) throws SQLException {
        System.out.println("SqlConnectorUserTable.addUserPicks method called");
        String sql = "INSERT INTO KOTH.User (idUser, picksSeason, initialPicks, picksPaid) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE initialPicks = ?, picksPaid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, user.getIdUser());
            preparedStatement.setInt(2, user.getPicksSeason());
            preparedStatement.setInt(3, user.getInitialPicks());
            preparedStatement.setBoolean(4, user.getPicksPaid());
            preparedStatement.setInt(5, user.getInitialPicks());
            preparedStatement.setBoolean(6, user.getPicksPaid());

            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Updating user picks failed, no rows affected.");
            }
        }
    }
    
    public boolean deleteUsersForOtherSeasons(int currentSeason) {
        System.out.println("SqlConnectorUserTable.deleteUsersForOtherSeasons called for season: " + currentSeason);
        String sql = "DELETE FROM KOTH.User WHERE picksSeason != ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            
            preparedStatement.setInt(1, currentSeason);
            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("Deleted " + rowsAffected + " users from previous seasons");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Error deleting users from other seasons: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean emailExists(String email) {
        System.out.println("SqlConnectorUserTable.emailExists method started");
        String sql = "SELECT COUNT(*) FROM KOTH.User WHERE email = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("SqlConnectorUserTable Error checking email existence: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
 // Method to get user by ID
    public User getUserById(int userId) {
        System.out.println("SqlConnectorUserTable.getUserById method called");
        String sql = "SELECT idUser, firstName, lastName, userName, email, cellNumber, commish FROM KOTH.User WHERE idUser = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);
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
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    public LoginResult authenticateUser(String username, String password) {
        return isValidUser(username, password);
    }

}
