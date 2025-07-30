package services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import model.PicksPrice;
import model.User;
import helpers.SqlConnectorPicksPriceTable;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ServletUtility {

    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private CommonProcessingService commonProcessingService;

    public void setCommonAttributes(HttpServletRequest request, ServletContext context) {
        System.out.println("ServletUtility.setCommonAttributes started");

        // First, get all relevant attributes
        String adminSetSeason = (String) context.getAttribute("adminSetSeason");
        String adminSetWeek = (String) context.getAttribute("adminSetWeek");
        String currentSeason = (String) context.getAttribute("currentSeason");
        String currentWeek = (String) context.getAttribute("currentWeek");

        System.out.println("ServletUtility: Retrieved from context - " +
                "adminSetSeason: " + adminSetSeason +
                ", adminSetWeek: " + adminSetWeek +
                ", currentSeason: " + currentSeason +
                ", currentWeek: " + currentWeek);

        // Determine which values to use (admin override takes precedence)
        String finalSeason;
        String finalWeek;

        if (adminSetSeason != null && adminSetWeek != null) {
            // Use admin override values
            finalSeason = adminSetSeason;
            finalWeek = adminSetWeek;
            System.out.println("ServletUtility: Using admin override values");
        } else {
            // In auto mode, always calculate new values
            if (commonProcessingService != null) {
                // Clear existing values to force recalculation
                context.removeAttribute("currentSeason");
                context.removeAttribute("currentWeek");

                commonProcessingService.updateSeasonAndWeek(context);
                finalSeason = (String) context.getAttribute("season");
                finalWeek = (String) context.getAttribute("week");
                System.out.println("ServletUtility: Retrieved fresh calculated values from CommonProcessingService");
            } else {
                // Fallback values if service is not available
                finalSeason = "2024";
                finalWeek = "1";
                System.out.println("ServletUtility: WARNING - CommonProcessingService not available, using fallback values");
            }
            System.out.println("ServletUtility: Using calculated values");
        }

        System.out.println("ServletUtility: Final determined values - " +
                "Season: " + finalSeason +
                ", Week: " + finalWeek);

        // Set values consistently across all attributes
        request.setAttribute("season", finalSeason);
        request.setAttribute("week", finalWeek);

        context.setAttribute("season", finalSeason);
        context.setAttribute("week", finalWeek);
        context.setAttribute("currentSeason", finalSeason);
        context.setAttribute("currentWeek", finalWeek);

        // ✅ Handle PicksPrice and allowSignUp
        handlePicksPriceAttributes(context, finalSeason);

        // ✅ Handle user attributes
        handleUserAttributes(request, context);
    }

    /**
     * Ensures PicksPrice table is initialized for the given season.
     * If no record exists, creates a default one and enables sign-up.
     */
    private void handlePicksPriceAttributes(ServletContext context, String season) {
        System.out.println("ServletUtility.handlePicksPriceAttributes started");
        try {
            if (sqlConnectorPicksPriceTable != null) {
                int seasonInt = Integer.parseInt(season);
                List<PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(seasonInt);

                if (pricesList == null || pricesList.isEmpty()) {
                    // No record found for this season → Create a default record
                    System.out.println("No PicksPrice record found for season " + seasonInt + ". Creating default entry...");

                    PicksPrice defaultPrice = new PicksPrice();
                    defaultPrice.setPicksPriceSeason(seasonInt);
                    defaultPrice.setKothSeason(String.valueOf(seasonInt));
                    defaultPrice.setMaxPicks(5);
                    defaultPrice.setPickPrice1(BigDecimal.valueOf(0));
                    defaultPrice.setPickPrice2(BigDecimal.valueOf(0));
                    defaultPrice.setPickPrice3(BigDecimal.valueOf(0));
                    defaultPrice.setPickPrice4(BigDecimal.valueOf(0));
                    defaultPrice.setPickPrice5(BigDecimal.valueOf(0));
                    defaultPrice.setAllowSignUp(true); // ✅ Enable sign-up by default

                    boolean success = sqlConnectorPicksPriceTable.updatePickPrices(defaultPrice);
                    System.out.println("Default PicksPrice created for season " + seasonInt + ": " + success);

                    context.setAttribute("allowSignUp", true);
                    context.setAttribute("kothSeason", defaultPrice.getKothSeason());
                } else {
                    // Use existing record
                    PicksPrice picksPrice = pricesList.get(0);
                    boolean allowSignUp = picksPrice.isAllowSignUp();
                    context.setAttribute("allowSignUp", allowSignUp);
                    context.setAttribute("kothSeason", picksPrice.getKothSeason());
                    System.out.println("Found PicksPrice record. allowSignUp = " + allowSignUp);
                }
            } else {
                System.out.println("sqlConnectorPicksPriceTable is null. Setting allowSignUp to false.");
                context.setAttribute("allowSignUp", false);
            }
        } catch (Exception e) {
            System.err.println("ServletUtility: Error handling PicksPrice: " + e.getMessage());
            e.printStackTrace();
            context.setAttribute("allowSignUp", false);
        }
    }

    private void handleUserAttributes(HttpServletRequest request, ServletContext context) {
        System.out.println("ServletUtility.handleUserAttributes started");
        String userName = (String) request.getSession().getAttribute("userName");
        if (userName != null) {
            request.setAttribute("userName", userName);
            User user = getUserFromContext(context, userName);
            if (user != null) {
                request.setAttribute("user", user);
            }
        }
        System.out.println("ServletUtility: Set user attributes - UserName: " + userName);
    }

    public static User getUserFromContext(ServletContext context, String username) {
        System.out.println("ServletUtility.getUserFromContext method started");
        @SuppressWarnings("unchecked")
        Map<String, User> userMap = (Map<String, User>) context.getAttribute("userMap");
        if (userMap != null) {
            User user = userMap.get(username);
            if (user != null) {
                return user;
            } else {
                System.out.println("ServletUtility: User not found in context - Username: " + username);
            }
        } else {
            System.out.println("ServletUtility: User map not found in context");
        }
        return null;
    }
}
