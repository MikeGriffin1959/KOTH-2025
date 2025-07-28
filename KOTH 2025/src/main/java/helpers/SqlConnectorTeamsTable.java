package helpers;

import javax.sql.DataSource; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.Team;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class SqlConnectorTeamsTable {

	@Autowired
	private DataSource dataSource;


    //Method to store team data (TeamsCreator)
    public void storeTeamsData(List<Team> teams) {
        String sql = "INSERT INTO Teams (apiTeamID, apiTeamName, apiTeamShortName, apiTeamFullName) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "apiTeamName = VALUES(apiTeamName), " +
                "apiTeamShortName = VALUES(apiTeamShortName), " +
                "apiTeamFullName = VALUES(apiTeamFullName)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (Team team : teams) {
                preparedStatement.setInt(1, team.getApiTeamID());
                preparedStatement.setString(2, team.getApiTeamName());
                preparedStatement.setString(3, team.getApiTeamShortName());
                preparedStatement.setString(4, team.getApiTeamFullName());

                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();
            System.out.println("SqlConnectorTeamsTable: Team data successfully inserted/updated into the database.");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SqlConnectorTeamsTable: Error while inserting/updating team data: " + e.getMessage());
        }
    }

    //Method to get abbreviated Team Names (CommissionerOverride, MakePicks, and CommonProcessingService)
    public Map<String, String> getTeamNameToAbbrev() {
        Map<String, String> teamNameToAbbrev = new HashMap<>();
        String sql = "SELECT apiTeamID, apiTeamName, apiTeamShortName, apiTeamFullName FROM KOTH.Teams";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
             
            while (resultSet.next()) {
                String name = resultSet.getString("apiTeamName");
                String shortName = resultSet.getString("apiTeamShortName");
                String fullName = resultSet.getString("apiTeamFullName");
                
                // Add all name variations mapping to the abbreviation
                teamNameToAbbrev.put(name, shortName);
                teamNameToAbbrev.put(shortName, shortName); 
                teamNameToAbbrev.put(fullName, shortName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return teamNameToAbbrev;
    }
}

