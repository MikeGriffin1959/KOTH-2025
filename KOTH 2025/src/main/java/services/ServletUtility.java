package services;

import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import model.PicksPrice;
import model.User;
import helpers.SqlConnectorPicksPriceTable;

public class ServletUtility {
    private static SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;
    private static CommonProcessingService commonProcessingService;
    
    // Static setter for CommonProcessingService
    public static void setCommonProcessingService(CommonProcessingService service) {
        commonProcessingService = service;
    }

    // Static setter for the SqlConnectorPicksPriceTable instance
    public static void setSqlConnectorPicksPriceTable(SqlConnectorPicksPriceTable connector) {
        sqlConnectorPicksPriceTable = connector;
    }

    public static void setCommonAttributes(HttpServletRequest request, ServletContext context) {
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
        // Request attributes
        request.setAttribute("season", finalSeason);
        request.setAttribute("week", finalWeek);
        
        // Context attributes - ensure all relevant attributes are updated
        context.setAttribute("season", finalSeason);
        context.setAttribute("week", finalWeek);
        context.setAttribute("currentSeason", finalSeason);
        context.setAttribute("currentWeek", finalWeek);

        // Handle PicksPrice and allowSignUp
        handlePicksPriceAttributes(context, finalSeason);

        // Handle user attributes
        handleUserAttributes(request, context);
    
    }

    private static void handlePicksPriceAttributes(ServletContext context, String season) {
        try {
            if (sqlConnectorPicksPriceTable != null) {
                int seasonInt = Integer.parseInt(season);
                List<PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(seasonInt);
                String kothSeason = (String) context.getAttribute("kothSeason");
                
                PicksPrice picksPrice = pricesList.stream()
                    .filter(p -> p.getKothSeason().equals(kothSeason))
                    .findFirst()
                    .orElse(null);
                    
                boolean allowSignUp = picksPrice != null && picksPrice.isAllowSignUp();
                context.setAttribute("allowSignUp", allowSignUp);
            } else {
                context.setAttribute("allowSignUp", false);
            }
        } catch (Exception e) {
            System.err.println("ServletUtility: Error handling PicksPrice: " + e.getMessage());
            context.setAttribute("allowSignUp", false);
        }
    }


    private static void handleUserAttributes(HttpServletRequest request, ServletContext context) {
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
//                System.out.println("ServletUtility: Retrieved user from context - Username: " + username);
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