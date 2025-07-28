package helpers;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import model.PicksPrice;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


@Component
public class SqlConnectorPicksPriceTable {

	@Autowired
	private DataSource dataSource;

    public boolean updatePickPrices(PicksPrice picksPrice) {
        System.out.println("SqlConnectorPicksPriceTable.updatePickPrices method called");
        String sql = "INSERT INTO KOTH.PicksPrice (picksPriceSeason, maxPicks, pickPrice1, pickPrice2, pickPrice3, pickPrice4, pickPrice5, kothSeason, allowSignUp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "maxPicks = VALUES(maxPicks), " +
                    "pickPrice1 = VALUES(pickPrice1), " +
                    "pickPrice2 = VALUES(pickPrice2), " +
                    "pickPrice3 = VALUES(pickPrice3), " +
                    "pickPrice4 = VALUES(pickPrice4), " +
                    "pickPrice5 = VALUES(pickPrice5), " +
                    "kothSeason = VALUES(kothSeason), " +
                    "allowSignUp = VALUES(allowSignUp)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, picksPrice.getPicksPriceSeason());
            preparedStatement.setInt(2, picksPrice.getMaxPicks());
            preparedStatement.setBigDecimal(3, picksPrice.getPickPrice1());
            preparedStatement.setBigDecimal(4, picksPrice.getPickPrice2());
            preparedStatement.setBigDecimal(5, picksPrice.getPickPrice3());
            preparedStatement.setBigDecimal(6, picksPrice.getPickPrice4());
            preparedStatement.setBigDecimal(7, picksPrice.getPickPrice5());
            preparedStatement.setString(8, picksPrice.getKothSeason());
            preparedStatement.setBoolean(9, picksPrice.isAllowSignUp());

            System.out.println("SqlConnectorPicksPriceTable.updatePickPrices - Updating pick prices with values:");
            System.out.println("  Season: " + picksPrice.getPicksPriceSeason());
            System.out.println("  MaxPicks: " + picksPrice.getMaxPicks());
            System.out.println("  Price1: " + picksPrice.getPickPrice1());
            System.out.println("  Price2: " + picksPrice.getPickPrice2());
            System.out.println("  Price3: " + picksPrice.getPickPrice3());
            System.out.println("  Price4: " + picksPrice.getPickPrice4());
            System.out.println("  Price5: " + picksPrice.getPickPrice5());
            System.out.println("  KothSeason: " + picksPrice.getKothSeason());
            System.out.println("  AllowSignUp: " + picksPrice.isAllowSignUp());

            int rowsAffected = preparedStatement.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("SqlConnectorPicksPriceTable.updatePickPrices - Successfully updated pick prices in database");
            } else {
                System.out.println("SqlConnectorPicksPriceTable.updatePickPrices - No rows were affected by the update");
            }
            
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("SqlConnectorPicksPriceTable.updatePickPrices - Error updating pick prices: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Method to get pick prices for a specific season
    public List<PicksPrice> getPickPrices(int season) {
        List<PicksPrice> prices = new ArrayList<>();
        String sql = "SELECT picksPriceSeason, maxPicks, pickPrice1, pickPrice2, pickPrice3, pickPrice4, pickPrice5, AllowSignUp, KOTHSeason FROM KOTH.PicksPrice WHERE picksPriceSeason = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, season);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    PicksPrice picksPrice = new PicksPrice();
                    picksPrice.setPicksPriceSeason(resultSet.getInt("picksPriceSeason"));
                    picksPrice.setMaxPicks(resultSet.getInt("maxPicks"));
                    picksPrice.setPickPrice1(resultSet.getBigDecimal("pickPrice1"));
                    picksPrice.setPickPrice2(resultSet.getBigDecimal("pickPrice2"));
                    picksPrice.setPickPrice3(resultSet.getBigDecimal("pickPrice3"));
                    picksPrice.setPickPrice4(resultSet.getBigDecimal("pickPrice4"));
                    picksPrice.setPickPrice5(resultSet.getBigDecimal("pickPrice5"));
                    picksPrice.setAllowSignUp(resultSet.getBoolean("allowSignUp"));
                    picksPrice.setKothSeason(resultSet.getString("kothSeason"));
                    prices.add(picksPrice);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving pick prices: " + e.getMessage());
            e.printStackTrace();
        }
        return prices;
    }
    
    // method to delete all records from the table (CommisionerServlet)
    public boolean truncatePicksPriceTable() {
        String sql = "TRUNCATE TABLE KOTH.PicksPrice";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            
            preparedStatement.executeUpdate();
            System.out.println("SqlConnectorPicksPriceTable.truncatePicksPriceTable - Successfully truncated PicksPrice table");
            return true;
            
        } catch (SQLException e) {
            System.err.println("SqlConnectorPicksPriceTable.truncatePicksPriceTable - Error truncating table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
