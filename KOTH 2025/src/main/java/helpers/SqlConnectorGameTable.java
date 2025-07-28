package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.Game;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class SqlConnectorGameTable {

	@Autowired
	private DataSource dataSource;

    
    //Method to create schedule data (ScheduleCreator)
    public void storeGameDataInSQLTable(List<Game> games) {
        System.out.println("SqlConnectorGameTable.storeGameDataInSQLTable method started");
        System.out.println(" Number of games to store: " + games.size());

        String sql = "INSERT INTO KOTH.Game (GameID, season, week, date, status, " +
                     "awayTeamId, hmeTeamId, awayScore, homeScore, pointSpread, overUnder) "+
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "season = VALUES(season), week = VALUES(week),  " +
                     "date = VALUES(date), status = VALUES(status), awayTeamId = VALUES(awayTeamId), " +
                     "homeTeamId = VALUES(homeTeamId), awayScore = VALUES(awayScore), homeScore = VALUES(homeScore), " +
                     "pointSpread = VALUES(pointSpread), overUnder = VALUES(overUnder)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (Game game : games) {
                System.out.println(" Storing game: ID=" + game.getGameID() +
                        ", Season=" + game.getSeason() +
                        ", Week=" + game.getWeek() +
                        ", Date=" + game.getDate() +
                        ", Status=" + game.getStatus());

                preparedStatement.setLong(1, game.getGameID());
                preparedStatement.setInt(2, game.getSeason());
                preparedStatement.setInt(3, game.getWeek());
                preparedStatement.setString(4, game.getDate());
                preparedStatement.setString(5, game.getStatus());
                preparedStatement.setInt(6, game.getAwayTeamId());
                preparedStatement.setInt(7, game.getHomeTeamId());
                preparedStatement.setInt(8, game.getAwayScore());
                preparedStatement.setInt(9, game.getHomeScore());

                if (game.getPointSpread() == null) {
                    preparedStatement.setNull(10, java.sql.Types.DOUBLE);
                } else {
                    preparedStatement.setDouble(10, game.getPointSpread());
                }

                if (game.getOverUnder() == null) {
                    preparedStatement.setNull(11, java.sql.Types.DOUBLE);
                } else {
                    preparedStatement.setDouble(11, game.getOverUnder());
                }

                // Set the new columns
                preparedStatement.setString(12, game.getHomeTeamAbbreviation());
                preparedStatement.setString(13, game.getAwayTeamAbbreviation());

                preparedStatement.addBatch();
            }

            int[] result = preparedStatement.executeBatch();
            System.out.println("SqlConnectorGameTable: Stored " + result.length + " games in the database.");

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SqlConnectorGameTable.storeGameDataInSQLTable: Error storing game data: " + e.getMessage());
        }
    }

    public List<Game> fetchGamesFromDatabase(int season, int week) {
        List<Game> games = new ArrayList<>();
        String sql = "SELECT * FROM KOTH.Game WHERE season = ? AND week = ? ";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, season);
            stmt.setInt(2, week);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Game game = new Game();
                    game.setGameID(rs.getInt("GameID"));
                    game.setSeason(rs.getInt("season"));
                    game.setWeek(rs.getInt("week"));
                    game.setDate(rs.getString("date"));
                    game.setStatus(rs.getString("status"));
                    game.setHomeTeamId(rs.getInt("homeTeamId"));
                    game.setAwayTeamId(rs.getInt("awayTeamId"));
                    game.setHomeScore(rs.getInt("homeScore"));
                    game.setAwayScore(rs.getInt("awayScore"));
                    game.setPointSpread(rs.getDouble("pointSpread"));
                    game.setOverUnder(rs.getDouble("overUnder"));

                    if (game.getGameID() != 0) {
                        games.add(game);
                    } else {
                        System.out.println("Skipping game with null or empty GameID");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("SqlConnectorGameTable.fetchGamesFromDatabase: Fetched " + games.size() + " valid games for Season: " + season + ", Week: " + week);
        return games;
    }

    public List<Game> getGamesUpToWeek(int season, int currentWeek) {
        System.out.println("SqlConnectorGameTable.getGamesUpToWeek method started");
        List<Game> games = new ArrayList<>();
        String sql = "SELECT * FROM KOTH.Game WHERE season = ?  AND week <= ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, season);
            stmt.setInt(2, currentWeek);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Game game = new Game();
                    game.setGameID(rs.getInt("GameID"));
                    game.setSeason(rs.getInt("season"));
                    game.setWeek(rs.getInt("week"));
                    game.setDate(rs.getString("date"));
                    game.setStatus(rs.getString("status"));
                    game.setHomeTeamId(rs.getInt("homeTeamId"));
                    game.setAwayTeamId(rs.getInt("awayTeamId"));
                    game.setHomeScore(rs.getInt("homeScore"));
                    game.setAwayScore(rs.getInt("awayScore"));
                    game.setPointSpread(rs.getDouble("pointSpread"));
                    game.setOverUnder(rs.getDouble("overUnder"));

                    if (game.getGameID() != 0) {
                        games.add(game);
                    } else {
                        System.out.println("Skipping game with null or empty GameID");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("SqlConnectorGameTable.getGamesUpToWeek: Fetched " + games.size() + " games up to Week " + currentWeek + " for Season: " + season);
        return games;
    }
    
    //Method to get all games for a season/week (CommissionerOverride, DisplayScoreboard, and MakePicks Servlets)
    public List<Game> getGamesForWeek(int season, int week) {
        System.out.println("SqlConnectorGameTable.getGamesForWeek method started - Fetching games for Season: "
        		+ season + ", Week: " + week );

        List<Game> games = new ArrayList<>();
        String sql = "SELECT g.*, ht.apiTeamShortName AS HomeTeamName, at.apiTeamShortName AS AwayTeamName " +
                     "FROM KOTH.Game g " +
                     "LEFT JOIN KOTH.Teams ht ON g.homeTeamId = ht.apiTeamID " +
                     "LEFT JOIN KOTH.Teams at ON g.awayTeamId = at.apiTeamID " +
                     "WHERE g.season = ? AND g.week = ? ";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, season);
            stmt.setInt(2, week);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Game game = new Game();
                    game.setGameID(rs.getInt("GameID"));
                    game.setSeason(rs.getInt("season"));
                    game.setWeek(rs.getInt("week"));
                    game.setDate(rs.getString("date"));
                    game.setStatus(rs.getString("status"));
                    game.setHomeTeamId(rs.getInt("homeTeamId"));
                    game.setAwayTeamId(rs.getInt("awayTeamId"));
                    game.setHomeScore(rs.getInt("homeScore"));
                    game.setAwayScore(rs.getInt("awayScore"));
                    game.setPointSpread(rs.getDouble("pointSpread"));
                    game.setOverUnder(rs.getDouble("overUnder"));
                    game.setHomeTeamName(rs.getString("HomeTeamName"));
                    game.setAwayTeamName(rs.getString("AwayTeamName"));
                    game.setDisplayClock(rs.getString("displayClock"));
                    game.setPeriod(rs.getString("period"));

                    if (game.getGameID() != 0) {
                        games.add(game);
                    } else {
                        System.out.println("Skipping game with null or empty GameID");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SqlConnectorGameTable.getGamesForWeek: Error fetching games: " + e.getMessage());
        }

        System.out.println("SqlConnectorGameTable.getGamesForWeek: Fetched " + games.size() + " games for Season: " + season + ", Week: " + week);
        
        return games;
    }

    //Method to add full season (ScheduleCreator.java)
    public void updateGameTableFull(List<Game> games) {
        System.out.println("SqlConnectorGameTable.updateESPNGameTableFull method started. "
                + "Number of games to update: " + games.size());

        String sql = "INSERT INTO KOTH.Game (GameID, season, week, date, " +
                "homeTeamId, homeTeamName, homeScore, awayTeamId, awayTeamName, awayScore, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "season = VALUES(season), " +
                "week = VALUES(week), " +
                "date = VALUES(date), " +
                "homeTeamId = VALUES(homeTeamId), " +
                "homeTeamName = VALUES(homeTeamName), " +
                "homeScore = VALUES(homeScore), " +
                "awayTeamId = VALUES(awayTeamId), " +
                "awayTeamName = VALUES(awayTeamName), " +
                "awayScore = VALUES(awayScore), " +
                "status = VALUES(status)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (Game game : games) {
                preparedStatement.setLong(1, game.getGameID());
                preparedStatement.setInt(2, game.getSeason());
                preparedStatement.setInt(3, game.getWeek());
                preparedStatement.setString(4, game.getDate());
                preparedStatement.setInt(5, game.getHomeTeamId());
                preparedStatement.setString(6, game.getHomeTeamName());
                preparedStatement.setInt(7, game.getHomeScore());
                preparedStatement.setInt(8, game.getAwayTeamId());
                preparedStatement.setString(9, game.getAwayTeamName());
                preparedStatement.setInt(10, game.getAwayScore());
                preparedStatement.setString(11, game.getStatus());

                preparedStatement.addBatch();
            }

            int[] result = preparedStatement.executeBatch();
            System.out.println("SqlConnectorGameTable.updateGameTableFull: Updated " + result.length + " games in the database.");

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SqlConnectorGameTable.updateGameTableFull: Error updating game data: " + e.getMessage());
        }
    }
    
    //  Method to update status of a specific game (HomeServlet)
    public void updateGameTableMinimal(List<Game> games) {
    	System.out.println("SqlConnectorGameTable.updateGameTableMinimal method started");
        String sql = "INSERT INTO KOTH.Game (GameID, Status, HomeTeamId, AwayTeamId, HomeScore, AwayScore, " +
                     "displayClock, period, season, week, date, homeTeamName, awayTeamName) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "Status = VALUES(Status), " +
                     "HomeTeamId = VALUES(HomeTeamId), " +
                     "AwayTeamId = VALUES(AwayTeamId), " +
                     "HomeScore = VALUES(HomeScore), " +
                     "AwayScore = VALUES(AwayScore), " +
                     "displayClock = VALUES(displayClock), " +
                     "period = VALUES(period), " +
                     "homeTeamName = VALUES(homeTeamName), " +
                     "awayTeamName = VALUES(awayTeamName), " +
                     "season = VALUES(season), " +  
                     "week = VALUES(week)";         

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Game game : games) {
            	 System.out.println("Pre-update values for game " + game.getGameID() + 
                         ": Season=" + game.getSeason() + 
                         ", Week=" + game.getWeek());
                // Convert full names to abbreviations
                String homeTeamAbbrev = game.getHomeTeamName().contains(" ") ? 
                    game.getHomeTeamName().substring(game.getHomeTeamName().lastIndexOf(" ") + 1) : 
                    game.getHomeTeamName();
                    
                String awayTeamAbbrev = game.getAwayTeamName().contains(" ") ? 
                    game.getAwayTeamName().substring(game.getAwayTeamName().lastIndexOf(" ") + 1) : 
                    game.getAwayTeamName();

                pstmt.setLong(1, game.getGameID());
                pstmt.setString(2, game.getStatus());
                pstmt.setInt(3, game.getHomeTeamId());
                pstmt.setInt(4, game.getAwayTeamId());
                pstmt.setInt(5, game.getHomeScore());
                pstmt.setInt(6, game.getAwayScore());
                pstmt.setString(7, game.getDisplayClock());
                pstmt.setString(8, game.getPeriod());
                pstmt.setInt(9, game.getSeason());     
                pstmt.setInt(10, game.getWeek());      
                pstmt.setString(11, game.getDate());
                pstmt.setString(12, homeTeamAbbrev);
                pstmt.setString(13, awayTeamAbbrev);

                int rowsAffected = pstmt.executeUpdate();
                System.out.println("  Updated game " + game.getGameID() + 
                                 " - Season: " + game.getSeason() + 
                                 ", Week: " + game.getWeek() + 
                                 " - Rows affected: " + rowsAffected);
            }
        } catch (SQLException e) {
            System.err.println("Error updating game table: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    //Method to update the ESPN odds
    public void updateGameOdds(Game game) {
        System.out.println("SqlConnectorGameTable.updateGameOdds method started");
        System.out.println(" Updating odds for game: GameID=" + game.getGameID() +
                          ", PointSpread=" + game.getPointSpread() +
                          ", OverUnder=" + game.getOverUnder());
                          
        String sql = "UPDATE KOTH.Game SET pointSpread = ?, overUnder = ? WHERE GameID = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            if (game.getPointSpread() != null) {
                stmt.setDouble(1, game.getPointSpread());
            } else {
                stmt.setNull(1, java.sql.Types.DOUBLE);
            }

            if (game.getOverUnder() != null) {
                stmt.setDouble(2, game.getOverUnder());
            } else {
                stmt.setNull(2, java.sql.Types.DOUBLE);
            }

            stmt.setLong(3, game.getGameID());

            int rowsAffected = stmt.executeUpdate();
            System.out.println(" SQL Update completed - GameID=" + game.getGameID() +
                              ", PointSpread=" + game.getPointSpread() +
                              ", OverUnder=" + game.getOverUnder() +
                              ", Rows affected: " + rowsAffected);

        } catch (SQLException e) {
            System.err.println("SqlConnectorGameTable.updateGameOdds: Error updating odds: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

