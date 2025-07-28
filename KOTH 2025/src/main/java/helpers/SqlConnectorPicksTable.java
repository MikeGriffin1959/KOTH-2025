package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class SqlConnectorPicksTable {

	@Autowired
	private DataSource dataSource;

// Method to get Picks and games (CommonProcessingService)
    public Map<Integer, Map<String, List<Map<String, Object>>>> getOptimizedPicksAndGames(int currentSeason, int currentWeek) {
        System.out.println("SqlConnectorPicksTable.getOptimizedPicksAndGames method started");
        Map<Integer, Map<String, List<Map<String, Object>>>> allPicksByWeek = new HashMap<>();

        String sql = "SELECT u.idUser, u.userName, p.week, p.gameId, p.selectedTeam, p.season,  " +
                "g.homeTeamId, g.awayTeamId, g.homeScore, g.awayScore, g.status, " +
                "ht.apiTeamName AS homeTeamName, at.apiTeamName AS awayTeamName, " +
                "u.picksPaid, u.initialPicks " +
                "FROM KOTH.User u " +
                "LEFT JOIN KOTH.Picks p ON u.idUser = p.userID AND p.season = ? " +
                "LEFT JOIN KOTH.Game g ON p.gameId = g.GameID " +
                "LEFT JOIN KOTH.Teams ht ON g.homeTeamId = ht.apiTeamID " +
                "LEFT JOIN KOTH.Teams at ON g.awayTeamId = at.apiTeamID " +
                "ORDER BY u.userName, p.week";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, currentSeason);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                int rowCount = 0;
                while (resultSet.next()) {
                    rowCount++;
                    
                    // Store user data
                    int userId = resultSet.getInt("idUser");
                    String username = resultSet.getString("userName");
                    boolean userPaid = resultSet.getBoolean("picksPaid");
                    int initialPicks = resultSet.getInt("initialPicks");

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("userId", userId);
                    userData.put("username", username);
                    userData.put("userPaid", userPaid);
                    userData.put("initialPicks", initialPicks);
                    
                    allPicksByWeek
                        .computeIfAbsent(0, k -> new HashMap<>())
                        .computeIfAbsent("allUsers", k -> new ArrayList<>())
                        .add(userData);

                    // Store pick data if it exists
                    String gameId = resultSet.getString("gameId");
                    if (gameId != null) {
                        int week = resultSet.getInt("week");
                        Map<String, Object> pickData = createPickData(resultSet, week);
                        
                        // Store under the correct week
                        allPicksByWeek
                            .computeIfAbsent(week, k -> new HashMap<>())
                            .computeIfAbsent(gameId, k -> new ArrayList<>())
                            .add(pickData);
                    }
                }
                System.out.println("  Total rows retrieved: " + rowCount);
            }
        } catch (SQLException e) {
            System.out.println("SqlConnectorPicksTable.getOptimizedPicksAndGames: SQL Exception: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("  Returning data with size: " + allPicksByWeek.size());
        // Log the structure of the data
        allPicksByWeek.forEach((week, weekData) -> {
            if (week == 0) {
                System.out.println("  Week " + week + " (User data): " + weekData.size() + " entries");
            } else {
                System.out.println("  Week " + week + ": " + weekData.size() + " games");
                weekData.forEach((gameId, picks) -> {
                    System.out.println("    Game " + gameId + ": " + picks.size() + " picks");
                });
            }
        });
        
        return allPicksByWeek;
    }
    
    //Method to get User's picks for a particular season/week/season type (CommissionerOverride  and MakePicks Servlets)
    public Map<String, List<String>> getUserPicks(int userId, int season, int week) {
        System.out.println("SqlConnectorPicksTable.getUserPicks method started");
        Map<String, List<String>> userPicks = new HashMap<>();
        String sql = "SELECT gameId, selectedTeam FROM KOTH.Picks WHERE userID = ? AND season = ? AND week = ? ";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);
            preparedStatement.setInt(2, season);
            preparedStatement.setInt(3, week);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String gameId = resultSet.getString("gameId");
                    String selectedTeam = resultSet.getString("selectedTeam");
                    userPicks.computeIfAbsent(gameId, k -> new ArrayList<>()).add(selectedTeam);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorPicksTable: Error retrieving user picks: " + e.getMessage());
        }

        return userPicks;
    }


    //Method to update a User's picks for a particular season/week (CommissionerOverride  and MakePicks Servlets)
    public void updateUserPicks(int userId, int season, int week, Map<String, List<String>> newPicks) {
        System.out.println("SqlConnectorPicksTable.updateUserPicks method started");
        String deleteSql = "DELETE FROM KOTH.Picks WHERE userID = ? AND season = ? AND week = ?";
        String insertSql = "INSERT INTO KOTH.Picks (userID, season, week, gameId, selectedTeam) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            try {
                // Start transaction and disable auto-commit
                conn.setAutoCommit(false);
                
                // First verify the table is empty for this user/season/week
                String verifySql = "SELECT COUNT(*) FROM KOTH.Picks WHERE userID = ? AND season = ? AND week = ?";
                try (PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {
                    verifyStmt.setInt(1, userId);
                    verifyStmt.setInt(2, season);
                    verifyStmt.setInt(3, week);
                    
                    try (ResultSet rs = verifyStmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            System.out.println("  Found " + count + " existing picks for user " + userId);
                        }
                    }
                }
                
                // Delete existing picks
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, userId);
                    deleteStmt.setInt(2, season);
                    deleteStmt.setInt(3, week);
                    
                    int deletedRows = deleteStmt.executeUpdate();
                    System.out.println("  Deleted " + deletedRows + " existing picks for user " + userId);
                }

                // Insert new picks
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    int batchCount = 0;
                    
                    for (Map.Entry<String, List<String>> entry : newPicks.entrySet()) {
                        String gameId = entry.getKey();
                        List<String> picks = entry.getValue();

                        for (String pick : picks) {
                            insertStmt.setInt(1, userId);
                            insertStmt.setInt(2, season);
                            insertStmt.setInt(3, week);
                            insertStmt.setString(4, gameId);
                            insertStmt.setString(5, pick);
                            insertStmt.addBatch();
                            batchCount++;
                        }
                    }

                    if (batchCount > 0) {
                        int[] results = insertStmt.executeBatch();
                        System.out.println("  Inserted " + results.length + " new picks for user " + userId);
                    }
                }

                // If we got here without exception, commit the transaction
                conn.commit();
                System.out.println("SqlConnectorPicksTable.updateUserPicks: Successfully committed all picks updates for user " + userId);

            } catch (SQLException e) {
                // Roll back on any error
                try {
                    conn.rollback();
                    System.err.println("SqlConnectorPicksTable.updateUserPicks: Rolled back transaction due to error: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    System.err.println("SqlConnectorPicksTable.updateUserPicks: Error during rollback: " + rollbackEx.getMessage());
                }
                throw e; // Re-throw the original exception
            }
        } catch (SQLException e) {
            System.err.println("SqlConnectorPicksTable.updateUserPicks: Database error updating picks for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("SqlConnectorPicksTable.updateUserPicks: Failed to update picks", e);
        }
    }

        
    //Method to delete all of a user's picks (Commissioner Servlet)
    public boolean deleteAllPicksForUser(int userId) {
        System.out.println("SqlConnectorPicksTable.deleteAllPicksForUser method started");
        String sql = "DELETE FROM KOTH.Picks WHERE userID = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);

            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("SqlConnectorPicksTable.deleteAllPicksForUser: Deleted " + rowsAffected + " picks for user " + userId);
            
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorPicksTable.deleteAllPicksForUser: Error deleting picks for user: " + e.getMessage());
            return false;
        }
    }
    
    //Method to get a User's picks for a particular season/week/season type.  Despite the name, it does not update the SQL table (HomeServlet)
    public void updateCurrentWeekPicksForAllUsers(int currentSeason, int currentWeek,  Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData) {
        System.out.println("SqlConnectorPicksTable.updateCurrentWeekPicksForAllUsers method started");
        System.out.println("SqlConnectorPicksTable.updateCurrentWeekPicksForAllUsers: Refreshing picks for season " + currentSeason + ", week " + currentWeek);

        String sql = "SELECT u.idUser, u.userName, p.gameId, p.selectedTeam, " +
                     "g.homeTeamId, g.awayTeamId, g.homeScore, g.awayScore, g.status, " +
                     "ht.apiTeamName AS homeTeamName, at.apiTeamName AS awayTeamName, " +
                     "u.picksPaid, u.initialPicks " +
                     "FROM KOTH.User u " +
                     "LEFT JOIN KOTH.Picks p ON u.idUser = p.userID AND p.season = ? AND p.week = ?  " +
                     "LEFT JOIN KOTH.Game g ON p.gameId = g.GameID " +
                     "LEFT JOIN KOTH.Teams ht ON g.homeTeamId = ht.apiTeamID " +
                     "LEFT JOIN KOTH.Teams at ON g.awayTeamId = at.apiTeamID";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, currentSeason);
            preparedStatement.setInt(2, currentWeek);

            optimizedData.remove(currentWeek);
            Map<String, List<Map<String, Object>>> weekData = new HashMap<>();
            optimizedData.put(currentWeek, weekData);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String gameId = resultSet.getString("gameId");
                    if (gameId != null) {
                        Map<String, Object> pickData = createPickData(resultSet, currentWeek);
                        weekData.computeIfAbsent(gameId, k -> new ArrayList<>()).add(pickData);
                    }
                    updateUserData(optimizedData, resultSet);
                }
            }

            System.out.println("SqlConnectorPicksTable.updateCurrentWeekPicksForAllUsers: Successfully refreshed picks for week " + currentWeek);
        } catch (SQLException e) {
            System.err.println("SqlConnectorPicksTable.updateCurrentWeekPicksForAllUsers: Error refreshing picks: " + e.getMessage());
            e.printStackTrace();
        }
    }
    	// (SqlConnectorPicksTable)
	    private Map<String, Object> createPickData(ResultSet rs, int week) throws SQLException {
//	        System.out.println("SqlConnectorPicksTable.createPickData method started");
	        Map<String, Object> pickData = new HashMap<>();
	        pickData.put("userId", rs.getInt("idUser"));
	        pickData.put("username", rs.getString("userName"));
	        pickData.put("week", week);
	        pickData.put("gameId", rs.getString("gameId"));
	        pickData.put("selectedTeam", rs.getString("selectedTeam"));
	        pickData.put("homeTeamId", rs.getInt("homeTeamId"));
	        pickData.put("awayTeamId", rs.getInt("awayTeamId"));
	        pickData.put("homeScore", rs.getInt("homeScore"));
	        pickData.put("awayScore", rs.getInt("awayScore"));
	        pickData.put("status", rs.getString("status"));
	        pickData.put("homeTeamName", rs.getString("homeTeamName"));
	        pickData.put("awayTeamName", rs.getString("awayTeamName"));
	        pickData.put("picksPaid", rs.getBoolean("picksPaid"));
	        pickData.put("initialPicks", rs.getInt("initialPicks"));
	        return pickData;
			}
		
		    private void updateUserData(Map<Integer, Map<String, List<Map<String, Object>>>> optimizedData, ResultSet rs) throws SQLException {
//		        System.out.println("SqlConnectorPicksTable.updateUserData method started");
		        int userId = rs.getInt("idUser");
		        String username = rs.getString("userName");
		        boolean userPaid = rs.getBoolean("picksPaid");
		        int initialPicks = rs.getInt("initialPicks");
		
		        Map<String, Object> userData = new HashMap<>();
		        userData.put("userId", userId);
		        userData.put("username", username);
		        userData.put("userPaid", userPaid);
		        userData.put("initialPicks", initialPicks);
		
		        List<Map<String, Object>> allUsersList = optimizedData
		            .computeIfAbsent(0, k -> new HashMap<>())
		            .computeIfAbsent("allUsers", k -> new ArrayList<>());
		
		        // Update or add user data
		        boolean userFound = false;
		        for (Map<String, Object> existingUserData : allUsersList) {
		            if (existingUserData.get("userId").equals(userId)) {
		                existingUserData.putAll(userData);
		                userFound = true;
		                break;
		            }
		        }
		        if (!userFound) {
		            allUsersList.add(userData);
		        }
    }
		    // Method to delete prior season Picks (Commissioner Servlet)
		    public boolean deletePicksForOtherSeasons(int currentSeason) {
		        System.out.println("SqlConnectorPicksTable.deletePicksForOtherSeasons called for season: " + currentSeason);
		        String sql = "DELETE FROM KOTH.Picks WHERE season != ?";
		        
		        try (Connection connection = dataSource.getConnection();
		             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
		            
		            preparedStatement.setInt(1, currentSeason);
		            int rowsAffected = preparedStatement.executeUpdate();
		            System.out.println("Deleted " + rowsAffected + " picks from previous seasons");
		            return true;
		            
		        } catch (SQLException e) {
		            System.err.println("Error deleting picks from other seasons: " + e.getMessage());
		            e.printStackTrace();
		            return false;
		        }
		    }
		    public Map<Integer, Map<String, List<Map<String, Object>>>> getPicksForAllWeeks(int season, int currentWeek) {
		        System.out.println("SqlConnectorPicksTable.getPicksForAllWeeks method started");
		        Map<Integer, Map<String, List<Map<String, Object>>>> allWeeksData = new HashMap<>();

		        String sql = "SELECT u.idUser, u.userName, p.week, p.gameId, p.selectedTeam, " +
		                     "g.homeTeamId, g.awayTeamId, g.homeScore, g.awayScore, g.status, " +
		                     "ht.apiTeamName AS homeTeamName, at.apiTeamName AS awayTeamName, " +
		                     "u.picksPaid, u.initialPicks " +
		                     "FROM KOTH.User u " +
		                     "LEFT JOIN KOTH.Picks p ON u.idUser = p.userID AND p.season = ? " +
		                     "LEFT JOIN KOTH.Game g ON p.gameId = g.GameID " +
		                     "LEFT JOIN KOTH.Teams ht ON g.homeTeamId = ht.apiTeamID " +
		                     "LEFT JOIN KOTH.Teams at ON g.awayTeamId = at.apiTeamID " +
		                     "WHERE p.week <= ? " +
		                     "ORDER BY p.week, u.userName";

		        try (Connection connection = dataSource.getConnection();
		             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

		            preparedStatement.setInt(1, season);
		            preparedStatement.setInt(2, currentWeek);

		            try (ResultSet resultSet = preparedStatement.executeQuery()) {
		                while (resultSet.next()) {
		                    int week = resultSet.getInt("week");
		                    String gameId = resultSet.getString("gameId");
		                    
		                    if (gameId != null) {
		                        // Create pick data for this entry
		                        Map<String, Object> pickData = createPickData(resultSet, week);
		                        
		                        // Add to the week's data structure
		                        allWeeksData
		                            .computeIfAbsent(week, k -> new HashMap<>())
		                            .computeIfAbsent(gameId, k -> new ArrayList<>())
		                            .add(pickData);
		                    }
		                    
		                    // Update user data in week 0 structure
		                    updateUserData(allWeeksData, resultSet);
		                }
		            }
		            
		            System.out.println("SqlConnectorPicksTable.getPicksForAllWeeks: Successfully retrieved picks for all weeks up to " + currentWeek);
		        } catch (SQLException e) {
		            System.err.println("SqlConnectorPicksTable.getPicksForAllWeeks: Error retrieving picks: " + e.getMessage());
		            e.printStackTrace();
		        }

		        return allWeeksData;
		    }
}

