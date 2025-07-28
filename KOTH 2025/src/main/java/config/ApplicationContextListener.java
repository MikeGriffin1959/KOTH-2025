package config;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import services.NFLSeasonCalculator;
import model.User;
import helpers.SqlConnectorUserTable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ApplicationContextListener {
    private final SqlConnectorUserTable sqlConnectorUserTable;
    private final NFLSeasonCalculator nflSeasonCalculator;

    public ApplicationContextListener(SqlConnectorUserTable sqlConnectorUserTable, NFLSeasonCalculator nflSeasonCalculator) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
        this.nflSeasonCalculator = nflSeasonCalculator;
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("ApplicationInitializer: onApplicationEvent called");
        WebApplicationContext context = (WebApplicationContext) event.getApplicationContext();
        
        int currentSeason = nflSeasonCalculator.getCurrentNFLSeason();
        int currentWeek = nflSeasonCalculator.getCurrentNFLWeekNumber();
        
        context.getServletContext().setAttribute("season", String.valueOf(currentSeason));
        context.getServletContext().setAttribute("week", String.valueOf(currentWeek));

        try {
            List<User> allUsers = sqlConnectorUserTable.getAllUsers();
            Map<String, User> userMap = allUsers.stream()
                .collect(Collectors.toMap(User::getUsername, user -> user));
            context.getServletContext().setAttribute("userMap", userMap);
            System.out.println("Application initialized with season: " + currentSeason +
                ", week: " + currentWeek +
                ", users loaded: " + allUsers.size());
        } catch (Exception e) {
            System.err.println("ApplicationInitializer: Error fetching users: " + e.getMessage());
            e.printStackTrace();
        }
    }
}