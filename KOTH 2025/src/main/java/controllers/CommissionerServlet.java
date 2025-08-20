package controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.ServletContext;
import model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import helpers.*;
import services.CommonProcessingService;
import services.ServletUtility;

@Controller
public class CommissionerServlet {
    private final SqlConnectorUserTable sqlConnectorUserTable;
    private final SqlConnectorPicksTable sqlConnectorPicksTable;
    private final SqlConnectorGameTable sqlConnectorGameTable;
    private final SqlConnectorTeamsTable sqlConnectorTeamsTable;
    private final SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;
    private final ServletContext servletContext;
    private final CommonProcessingService commonProcessingService;
    
    @Autowired
    private ServletUtility servletUtility; // ✅ Injected instead of static

    @Autowired
    public CommissionerServlet(SqlConnectorUserTable sqlConnectorUserTable,
                              SqlConnectorPicksTable sqlConnectorPicksTable,
                              SqlConnectorGameTable sqlConnectorGameTable,
                              SqlConnectorTeamsTable sqlConnectorTeamsTable,
                              SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable,
                              ServletContext servletContext,
                              CommonProcessingService commonProcessingService,
                              ApiFetchers apiFetchers,
                              ApiParsers apiParsers) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
        this.sqlConnectorPicksTable = sqlConnectorPicksTable;
        this.sqlConnectorGameTable = sqlConnectorGameTable;
        this.sqlConnectorTeamsTable = sqlConnectorTeamsTable;
        this.sqlConnectorPicksPriceTable = sqlConnectorPicksPriceTable;
        this.servletContext = servletContext;
        this.commonProcessingService = commonProcessingService;
              
        System.out.println("CommissionerServlet initialized. sqlConnectorUserTable is " + 
                          (sqlConnectorUserTable != null ? "not null" : "null"));
    }
    
    private boolean isLoggedIn(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("userName") != null;
    }

    private boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
            || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));
    }

    /** Returns null if auth OK; otherwise returns a redirect string to LoginServlet with returnTo. */
    private String requireLoginOrRedirect(HttpServletRequest request) {
        if (isLoggedIn(request)) return null;
        String original = request.getRequestURI() +
            (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
    }

    /** For XHR endpoints: if not logged in or not commish -> write 401 JSON and return true (handled). */
    private boolean failAjaxUnauthorizedIfNeeded(HttpServletRequest request, HttpServletResponse response, boolean mustBeCommish) throws IOException {
        if (!isAjax(request)) return false;
        HttpSession s = request.getSession(false);
        boolean loggedIn = s != null && s.getAttribute("userName") != null;
        boolean commish = loggedIn && Boolean.TRUE.equals(s.getAttribute("isCommish"));
        if (!loggedIn || (mustBeCommish && !commish)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Login-Redirect",
                request.getContextPath() + "/LoginServlet?expired=1&returnTo=" +
                java.net.URLEncoder.encode(request.getRequestURI() +
                  (request.getQueryString() != null ? "?" + request.getQueryString() : ""), 
                  java.nio.charset.StandardCharsets.UTF_8));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized or session expired\"}");
            return true;
        }
        return false;
    }


    @GetMapping("/CommissionerServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {

        // First: require login
        String maybeRedirect = requireLoginOrRedirect(request);
        if (maybeRedirect != null) return maybeRedirect;

        // Then: require role
        if (!isCommish(request)) {
            return "redirect:/accessDenied.jsp";
        }

        // ✅ Use injected ServletUtility instead of static
        servletUtility.setCommonAttributes(request, servletContext);
        String currentSeason = (String) request.getAttribute("season");

        if (currentSeason == null) {
            model.addAttribute("errorMessage", "Missing current season.");
            return "error";
        }

        int season = Integer.parseInt(currentSeason);
        List<User> users = sqlConnectorUserTable.getAllUsersForSeason(season);
        model.addAttribute("users", users);

        // ✅ Fetch PicksPrice data for season
        List<PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(season);
        String kothSeason;
        PicksPrice currentPrices = null;

        if (!pricesList.isEmpty()) {
            currentPrices = pricesList.get(0);
            kothSeason = currentPrices.getKothSeason();
            servletContext.setAttribute("kothSeason", kothSeason);
            servletContext.setAttribute("allowSignUp", currentPrices.isAllowSignUp());

            // Format prices to 2 decimals
            formatPrices(currentPrices);
        } else {
            kothSeason = String.valueOf(season);
            servletContext.setAttribute("kothSeason", kothSeason);
            currentPrices = new PicksPrice();
            currentPrices.setPicksPriceSeason(season);
            currentPrices.setKothSeason(kothSeason);
        }

        model.addAttribute("currentPrices", currentPrices);

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            rootNode.put("picksPriceSeason", currentPrices.getPicksPriceSeason());
            rootNode.put("maxPicks", currentPrices.getMaxPicks());
            rootNode.put("pickPrice1", getPriceAsString(currentPrices.getPickPrice1()));
            rootNode.put("pickPrice2", getPriceAsString(currentPrices.getPickPrice2()));
            rootNode.put("pickPrice3", getPriceAsString(currentPrices.getPickPrice3()));
            rootNode.put("pickPrice4", getPriceAsString(currentPrices.getPickPrice4()));
            rootNode.put("pickPrice5", getPriceAsString(currentPrices.getPickPrice5()));
            rootNode.put("allowSignUp", currentPrices.isAllowSignUp());
            rootNode.put("kothSeason", currentPrices.getKothSeason());

            model.addAttribute("pickPricesJson", mapper.writeValueAsString(rootNode));
        } catch (Exception e) {
            System.err.println("CommissionerServlet: Error creating pick prices JSON: " + e.getMessage());
            createDefaultPicksPricesJson(model, season, kothSeason);
        }

        return "commissioner";
    }

    private void formatPrices(PicksPrice currentPrices) {
        if (currentPrices.getPickPrice1() != null) {
            currentPrices.setPickPrice1(currentPrices.getPickPrice1().setScale(2));
        }
        if (currentPrices.getPickPrice2() != null) {
            currentPrices.setPickPrice2(currentPrices.getPickPrice2().setScale(2));
        }
        if (currentPrices.getPickPrice3() != null) {
            currentPrices.setPickPrice3(currentPrices.getPickPrice3().setScale(2));
        }
        if (currentPrices.getPickPrice4() != null) {
            currentPrices.setPickPrice4(currentPrices.getPickPrice4().setScale(2));
        }
        if (currentPrices.getPickPrice5() != null) {
            currentPrices.setPickPrice5(currentPrices.getPickPrice5().setScale(2));
        }
    }

    private String getPriceAsString(BigDecimal price) {
        return price != null ? price.toString() : "";
    }

    private void createDefaultPicksPricesJson(Model model, int season, String kothSeason) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.put("picksPriceSeason", season);
            rootNode.put("maxPicks", "");
            rootNode.put("pickPrice1", "");
            rootNode.put("pickPrice2", "");
            rootNode.put("pickPrice3", "");
            rootNode.put("pickPrice4", "");
            rootNode.put("pickPrice5", "");
            rootNode.put("allowSignUp", false);
            rootNode.put("kothSeason", kothSeason);
            model.addAttribute("pickPricesJson", mapper.writeValueAsString(rootNode));
        } catch (Exception ex) {
            System.err.println("CommissionerServlet: Error creating default pick prices JSON: " + ex.getMessage());
        }
    }

    @PostMapping("/CommissionerServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("CommissionerServlet: doPost method called");
        long startTime = System.nanoTime();

        // If this is an Ajax/JSON call and not authorized, reply 401 JSON
        if (failAjaxUnauthorizedIfNeeded(request, response, true)) {
            return null;
        }

        // Non-Ajax: redirect to login if needed
        String maybeRedirect = requireLoginOrRedirect(request);
        if (maybeRedirect != null) return maybeRedirect;

        if (!isCommish(request)) {
            return "redirect:/accessDenied.jsp";
        }

        String action = request.getParameter("action");
        System.out.println("CommissionerServlet: Action parameter = " + action);

        try {
            // ✅ Use injected ServletUtility
            servletUtility.setCommonAttributes(request, servletContext);
            String currentSeason = (String) request.getAttribute("season");

            if (currentSeason == null) {
                throw new ServletException("Missing current season.");
            }

            int season = Integer.parseInt(currentSeason);

            if ("deleteUser".equals(action)) {
                handleUserDeletion(request, response, model);
                return null;
            }

            switch (action) {
                case "createTeams":
                    boolean success = handleCreateTeams();
                    writeJsonResponse(response, success, success ? "Teams created successfully" : "Error creating teams");
                    return null;

                case "updateUserRoles":
                    handleUpdateUserRoles(request, model);
                    break;

                case "createSchedule":
                    handleCreateSchedule(request, response);
                    return null;

                case "setSeasonWeek":
                    handleSetSeasonWeek(request, response);
                    return null;

                case "updateUsers":
                    handleUserUpdates(request, response, model, season);
                    break;

                case "updatePricePerPick":
                    handleUpdatePricePerPick(request, response);
                    return null;

                case "allowNewUsers":
                    handleAllowNewUsers(request, response);
                    return null;

                default:
                    model.addAttribute("message", "Invalid action specified");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("application/json");
            response.getWriter().write("{\"success\": false, \"message\": \"Operation failed: " + e.getMessage() + "\"}");
            return null;
        }

        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("CommissionerServlet.doPost Method execution time: %.1f Seconds%n", durationInSeconds);

        return "redirect:/CommissionerServlet";
    }

    private void writeJsonResponse(HttpServletResponse response, boolean success, String message) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"success\": %b, \"message\": \"%s\"}", success, message));
    }

    private boolean isCommish(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("isCommish"));
    }

    private void handleUserUpdates(HttpServletRequest request, HttpServletResponse response, Model model, int season)
            throws ServletException, IOException, SQLException {
        boolean updateSuccessful = false;
        boolean deletePerformed = false;

        List<User> users = sqlConnectorUserTable.getAllUsersForSeason(season);

        for (User user : users) {
            // ✅ Check delete request
            String deleteParam = "deleteUser_" + user.getIdUser();
            boolean deleteRequested = request.getParameter(deleteParam) != null;
            boolean confirmDelete = "on".equals(request.getParameter("confirmDelete"));

            if (deleteRequested && confirmDelete) {
                System.out.println("CommissionerServlet: Deleting user " + user.getIdUser());
                boolean picksDeleted = sqlConnectorPicksTable.deleteAllPicksForUser(user.getIdUser());
                boolean userDeleted = sqlConnectorUserTable.deleteUser(user.getIdUser());
                if (picksDeleted && userDeleted) {
                    System.out.println("CommissionerServlet: User " + user.getIdUser() + " successfully deleted");
                    deletePerformed = true;
                    updateSuccessful = true;
                } else {
                    System.err.println("CommissionerServlet: Failed to fully delete user " + user.getIdUser());
                    model.addAttribute("message", "Failed to delete user " + user.getIdUser());
                    model.addAttribute("messageType", "error");
                }
                continue; // ✅ Skip updates for deleted user
            }

            // ✅ Otherwise, process normal updates
            String paidParam = "userPicksPaid_" + user.getIdUser();
            boolean newPaidStatus = request.getParameter(paidParam) != null;

            String commishParam = "userCommish_" + user.getIdUser();
            boolean newCommishStatus = request.getParameter(commishParam) != null;

            String initialPicksParam = "initialPicks_" + user.getIdUser();
            String initialPicksValue = request.getParameter(initialPicksParam);
            int newInitialPicks = 0;
            if (initialPicksValue != null && !initialPicksValue.isEmpty()) {
                newInitialPicks = Integer.parseInt(initialPicksValue);
            }

            System.out.println("CommissionerServlet.doPost: Attempting to update user " + user.getIdUser());
            System.out.println("CommissionerServlet.doPost: New paid status: " + newPaidStatus + " for season: " + season);
            System.out.println("CommissionerServlet.doPost: New commish status: " + newCommishStatus);
            System.out.println("CommissionerServlet.doPost: New initial picks: " + newInitialPicks);

            // Update commish status
            if (user.isCommish() != newCommishStatus) {
                sqlConnectorUserTable.updateUserRoles(user.getIdUser(), newCommishStatus);
            }

            // Update picks record
            User existingUserPicks = sqlConnectorUserTable.getInitialPickCount(user.getIdUser(), season);
            if (existingUserPicks != null) {
                existingUserPicks.setInitialPicks(newInitialPicks);
                existingUserPicks.setPicksPaid(newPaidStatus);
                sqlConnectorUserTable.updateUserPicks(existingUserPicks);
                System.out.println("CommissionerServlet.doPost: Successfully updated user " + user.getIdUser());
            } else {
                User userPicks = new User();
                userPicks.setIdUser(user.getIdUser());
                userPicks.setPicksSeason(season);
                userPicks.setInitialPicks(newInitialPicks);
                userPicks.setPicksPaid(newPaidStatus);
                sqlConnectorUserTable.addUserPicks(userPicks);
                System.out.println("CommissionerServlet.doPost: Successfully created new record for user " + user.getIdUser());
            }
            updateSuccessful = true;
        }

        // ✅ Feedback message
        if (deletePerformed) {
            model.addAttribute("message", "User(s) successfully deleted");
            model.addAttribute("messageType", "success");
        } else if (updateSuccessful) {
            model.addAttribute("message", "Update Successful");
            model.addAttribute("messageType", "success");
        } else {
            model.addAttribute("message", "No changes were made");
            model.addAttribute("messageType", "warning");
        }

        // ✅ NEW: bump cache version so Home/MakePicks sessions know to refresh
        long now = System.currentTimeMillis();
        servletContext.setAttribute("derivedDataVersion", now);

        // (Optional) clear any app-scope derived objects you populate elsewhere
        servletContext.removeAttribute("userRemainingPicksPriorWeek");
        servletContext.removeAttribute("teamNameToAbbrev");
        servletContext.removeAttribute("gameWinners");
        // add/remove more keys here if you cache others in application scope
    }



    private void handleUpdateUserRoles(HttpServletRequest request, Model model) throws SQLException {
        int userId = Integer.parseInt(request.getParameter("selectedUserId"));

        boolean isCommish = request.getParameter("commish") != null;

        boolean updated = sqlConnectorUserTable.updateUserRoles(userId, isCommish);

        if (updated) {
            model.addAttribute("message", "User roles updated successfully");
            model.addAttribute("success", true);
        } else {
            model.addAttribute("message", "Failed to update user roles");
            model.addAttribute("success", false);
        }
    }
    
    private boolean handleCreateTeams() {
        System.out.println("CommissionerServlet: handleCreateTeams method started");
        try {
            System.out.println("CommissionerServlet: Fetching ESPN teams data");
            String apiResponse = ApiFetchers.FetchESPNTeams();
            System.out.println("CommissionerServlet: Parsing ESPN teams data");
            List<Team> teams = ApiParsers.ParseESPNTeams(apiResponse);
            System.out.println("CommissionerServlet: Storing teams data");
            sqlConnectorTeamsTable.storeTeamsData(teams);

            System.out.println("CommissionerServlet: Team data successfully stored in the database.");
            return true;
        } catch (Exception e) {
            System.err.println("CommissionerServlet: Error occurred while creating teams: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            System.out.println("CommissionerServlet: handleCreateTeams method completed");
        }
    }

    private void processESPNAPI(int season) throws IOException, SQLException {
        System.out.println("CommissionerServlet: Fetching data from ESPN Full Season Schedule API");
        String espnApiResponse = ApiFetchers.FetchESPNFullSeasonSchedule(season);
        if (espnApiResponse == null || espnApiResponse.isEmpty()) {
            throw new IOException("Received empty response from ESPN Full Season Schedule API");
        }
        System.out.println("CommissionerServlet: Parsing ESPN API response");
        List<Game> games = ApiParsers.ParseESPNAPI(espnApiResponse);
        if (games == null || games.isEmpty()) {
            throw new IllegalStateException("No games parsed from ESPN API response");
        }
        System.out.println("CommissionerServlet: Storing ESPN game data in ESPNGame SQL table");
        sqlConnectorGameTable.updateGameTableFull(games);
        System.out.println("CommissionerServlet: Game data successfully stored in the ESPNGame database for season " + season + ".");
    }
    
    private void handleAllowNewUsers(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (failAjaxUnauthorizedIfNeeded(request, response, true)) return;
        try {
            String seasonStr = (String) request.getAttribute("season");
            if (seasonStr == null) {
                throw new ServletException("Current season not found in request attributes");
            }
            
            int season = Integer.parseInt(seasonStr);
            boolean allowNewUsers = request.getParameter("allowNewUsers") != null;
            
            // Get existing prices first
            List<PicksPrice> pricesList = sqlConnectorPicksPriceTable.getPickPrices(season);
            if (pricesList.isEmpty()) {
                throw new ServletException("No existing price configuration found for season " + season);
            }
            
            // Use the existing price configuration and only update allowSignUp
            PicksPrice picksPrice = pricesList.get(0);
            picksPrice.setAllowSignUp(allowNewUsers);
            
            boolean success = sqlConnectorPicksPriceTable.updatePickPrices(picksPrice);
            
         // Update application scope immediately
            servletContext.setAttribute("allowSignUp", allowNewUsers);
            
            String message = URLEncoder.encode("Sign ups are now " + 
                (allowNewUsers ? "enabled" : "disabled"), "UTF-8");
            response.sendRedirect("CommissionerServlet?success=" + success + 
                "&messageType=allowSignUp&message=" + message);
                
        } catch (Exception e) {
            System.err.println("CommissionerServlet: Error in handleAllowNewUsers: " + e.getMessage());
            String errorMessage = URLEncoder.encode("Error updating settings: " + e.getMessage(), "UTF-8");
            response.sendRedirect("CommissionerServlet?success=false&messageType=allowSignUp&message=" + errorMessage);
        }
    }
    
    private void handleSetSeasonWeek(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        if (failAjaxUnauthorizedIfNeeded(request, response, true)) return;
        try {
            String season = request.getParameter("season");
            String week = request.getParameter("week");
            boolean autoSeason = Boolean.parseBoolean(request.getParameter("autoSeason"));
            
            System.out.println("CommissionerServlet: Handling set season/week. Season: " + season + 
                              ", Week: " + week + ", Auto: " + autoSeason);
            
            if (autoSeason) {
                // Remove admin settings
                servletContext.removeAttribute("adminSetSeason");
                servletContext.removeAttribute("adminSetWeek");
                
                // Use CommonProcessingService to update calculated values
                commonProcessingService.updateSeasonAndWeek(servletContext);
                
                // Get the calculated values for the message
                String calculatedSeason = (String) servletContext.getAttribute("season");
                String calculatedWeek = (String) servletContext.getAttribute("week");
                
                System.out.println("CommissionerServlet: Switched to auto mode. Calculated Season: " + 
                                 calculatedSeason + ", Week: " + calculatedWeek);
                
                response.getWriter().write("{\"success\": true, \"message\": \"Switched to auto mode<br>Season " + 
                        calculatedSeason + " Week " + calculatedWeek + "\"}");
            } else if (season != null && week != null) {
                // Manual override
                servletContext.setAttribute("adminSetSeason", season);
                servletContext.setAttribute("adminSetWeek", week);
                servletContext.setAttribute("currentSeason", season);
                servletContext.setAttribute("currentWeek", week);
                
                System.out.println("CommissionerServlet: Successfully set manual season/week");
                response.getWriter().write("{\"success\": true, \"message\": \"Updated to Season " + 
                                         season + " Week " + week + "\"}");
            } else {
                System.out.println("CommissionerServlet: Failed to set season/week - invalid input");
                response.getWriter().write("{\"success\": false, \"message\": \"Invalid input for Season or Week\"}");
            }
            
        } catch (Exception e) {
            System.err.println("CommissionerServlet: Error in handleSetSeasonWeek: " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"success\": false, \"message\": \"Error updating season/week: " + 
                                     e.getMessage() + "\"}");
        }
    }
    
    private void handleUserDeletion(HttpServletRequest request, HttpServletResponse response, Model model) 
            throws ServletException, IOException, SQLException {
        System.out.println("CommissionerServlet: Starting handleUserDeletion");
        List<Integer> userIdsToDelete = new ArrayList<>();
        
        // Get all parameters and find the ones for deletion
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (paramName.startsWith("deleteUser_") && "on".equals(request.getParameter(paramName))) {
                try {
                    int userId = Integer.parseInt(paramName.substring("deleteUser_".length()));
                    userIdsToDelete.add(userId);
                    System.out.println("CommissionerServlet: Found user to delete, ID: " + userId);
                } catch (NumberFormatException e) {
                    System.err.println("CommissionerServlet: Error parsing user ID from " + paramName);
                }
            }
        }
        
        if (userIdsToDelete.isEmpty()) {
            System.out.println("CommissionerServlet: No users selected for deletion");
            model.addAttribute("message", "Please select at least one user to delete");
            model.addAttribute("messageType", "warning");
            return;
        }

        boolean confirmDelete = "on".equals(request.getParameter("confirmDelete"));
        if (!confirmDelete) {
            System.out.println("CommissionerServlet: Deletion not confirmed");
            model.addAttribute("message", "Please check the confirmation box to confirm user deletion");
            model.addAttribute("messageType", "warning");
            return;
        }

        boolean allSuccessful = true;
        StringBuilder errorMessage = new StringBuilder();
        int successCount = 0;
        
        for (Integer userId : userIdsToDelete) {
            System.out.println("CommissionerServlet: Attempting to delete user " + userId);
            // First delete all picks
            boolean picksDeleted = sqlConnectorPicksTable.deleteAllPicksForUser(userId);
            System.out.println("CommissionerServlet: Picks deleted: " + picksDeleted);
            
            // Then delete the user
            boolean userDeleted = sqlConnectorUserTable.deleteUser(userId);
            System.out.println("CommissionerServlet: User deleted: " + userDeleted);
            
            if (!picksDeleted || !userDeleted) {
                allSuccessful = false;
                errorMessage.append("Failed to delete user ").append(userId).append(". ");
            } else {
                successCount++;
            }
        }
        
        if (allSuccessful) {
            System.out.println("CommissionerServlet: All users successfully deleted");
            String successMessage = successCount == 1 ? 
                "1 user successfully deleted" : 
                successCount + " users successfully deleted";
            model.addAttribute("message", successMessage);
            model.addAttribute("messageType", "success");
        } else {
            System.out.println("CommissionerServlet: Some deletions failed: " + errorMessage.toString());
            model.addAttribute("message", errorMessage.toString());
            model.addAttribute("messageType", "error");
        }
    }
    
    private void handleUpdatePricePerPick(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (failAjaxUnauthorizedIfNeeded(request, response, true)) return;
        
        try {
            // Get season value from request
            String seasonStr = request.getParameter("season");
            int season = Integer.parseInt(seasonStr);
            
            // Get current prices to preserve existing values
            List<PicksPrice> existingPrices = sqlConnectorPicksPriceTable.getPickPrices(season);
            PicksPrice priceData = new PicksPrice();
            
            // Set season
            priceData.setPicksPriceSeason(season);
            
            // Get and set max picks
            String maxPicksStr = request.getParameter("maxPicks");
            int maxPicks = 0;
            if (maxPicksStr != null && !maxPicksStr.isEmpty()) {
                maxPicks = Integer.parseInt(maxPicksStr);
                priceData.setMaxPicks(maxPicks);
            }
            
            // Process each price value
            for (int i = 1; i <= 5; i++) {
                String priceStr = request.getParameter("price" + i);
                BigDecimal price;
                
                // If within max picks and price provided, use that price
                if (i <= maxPicks && priceStr != null && !priceStr.isEmpty()) {
                    price = new BigDecimal(priceStr);
                }
                // If beyond max picks or no price provided, set to zero
                else {
                    price = BigDecimal.ZERO;
                }
                
                // Set the price
                switch (i) {
                    case 1: priceData.setPickPrice1(price); break;
                    case 2: priceData.setPickPrice2(price); break;
                    case 3: priceData.setPickPrice3(price); break;
                    case 4: priceData.setPickPrice4(price); break;
                    case 5: priceData.setPickPrice5(price); break;
                }
            }
            
            // Preserve existing values for other fields
            if (!existingPrices.isEmpty()) {
                PicksPrice existing = existingPrices.get(0);
                priceData.setAllowSignUp(existing.isAllowSignUp());
                priceData.setKothSeason(existing.getKothSeason());
            } else {
                // Set defaults for new records
                priceData.setAllowSignUp(false);
                priceData.setKothSeason("KOTH");
            }
            
            // Update the database
            boolean success = sqlConnectorPicksPriceTable.updatePickPrices(priceData);
            
            // Log the final values being sent to the database
            System.out.println("CommissionerServlet - Final price data being sent to update:");
            System.out.println("  Season: " + priceData.getPicksPriceSeason());
            System.out.println("  MaxPicks: " + priceData.getMaxPicks());
            System.out.println("  Price1: " + priceData.getPickPrice1());
            System.out.println("  Price2: " + priceData.getPickPrice2());
            System.out.println("  Price3: " + priceData.getPickPrice3());
            System.out.println("  Price4: " + priceData.getPickPrice4());
            System.out.println("  Price5: " + priceData.getPickPrice5());
            
            // Redirect back with status
            String message = success ? "Pick prices updated successfully" : "Failed to update pick prices";
            response.sendRedirect("CommissionerServlet?success=" + success + 
                "&messageType=pickPrices&message=" + URLEncoder.encode(message, "UTF-8"));
                
        } catch (Exception e) {
            System.err.println("CommissionerServlet - Error in handleUpdatePricePerPick: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("CommissionerServlet?success=false&messageType=pickPrices&message=" + 
                URLEncoder.encode("Error: " + e.getMessage(), "UTF-8"));
        }
    }

    private void handleCreateSchedule(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        if (failAjaxUnauthorizedIfNeeded(request, response, true)) return;

        PrintWriter out = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            String seasonStr = request.getParameter("season");
            String seasonType = request.getParameter("seasonType");
            boolean confirmed = Boolean.parseBoolean(request.getParameter("confirmed"));
            
            // Add debug logging
            System.out.println("CommissionerServlet.handleCreateSchedule - Received parameters:");
            System.out.println("  Season: " + seasonStr);
            System.out.println("  SeasonType: " + seasonType);
            System.out.println("  Confirmed: " + confirmed);

            if (seasonStr == null || seasonStr.trim().isEmpty()) {
                out.write(mapper.writeValueAsString(Map.of(
                    "success", false,
                    "message", "Season parameter is required"
                )));
                return;
            }

            // Validate seasonType
            if (seasonType == null || seasonType.trim().isEmpty()) {
                out.write(mapper.writeValueAsString(Map.of(
                    "success", false,
                    "message", "Season Type parameter is required"
                )));
                return;
            }

            if (!confirmed) {
                out.write(mapper.writeValueAsString(Map.of(
                    "requireConfirmation", true,
                    "message", "Warning: All previous season's data will be deleted. Do you want to continue?"
                )));
                return;
            }

            int season = Integer.parseInt(seasonStr);
            List<String> messages = new ArrayList<>();
            boolean overallSuccess = processScheduleCreation(season, seasonType.trim(), messages);

            Map<String, Object> responseObj = new HashMap<>();
            responseObj.put("success", overallSuccess);
            responseObj.put("messages", messages);
            out.write(mapper.writeValueAsString(responseObj));

        } catch (Exception e) {
            handleScheduleError(e, out, mapper);
        }
    }

    private boolean processScheduleCreation(int season, String seasonType, List<String> messages) {
        try {
            // Process ESPN API first
            processESPNAPI(season);
            messages.add("*Schedule successfully updated for season " + season);

            // Create teams
            boolean teamsSuccess = handleCreateTeams();
            messages.add(teamsSuccess ? "*Teams data successfully created" : "Failed to create teams data");

            // Delete old picks
            boolean picksDeleted = sqlConnectorPicksTable.deletePicksForOtherSeasons(season);
            messages.add(picksDeleted ? "*Successfully deleted picks from previous seasons" : 
                                      "Failed to delete picks from previous seasons");

            // Delete old users
            boolean usersDeleted = sqlConnectorUserTable.deleteUsersForOtherSeasons(season);
            messages.add(usersDeleted ? "*Successfully deleted users from previous seasons" : 
                                      "Failed to delete users from previous seasons");

            // Always process the season type update
            System.out.println("Processing season type update for season: " + season + ", type: " + seasonType);
            
            // Truncate the PicksPrice table
            boolean truncateSuccess = sqlConnectorPicksPriceTable.truncatePicksPriceTable();
            System.out.println("PicksPrice table truncated: " + truncateSuccess);
            messages.add(truncateSuccess ? "*Successfully cleared previous pick prices" : 
                                         "Failed to clear previous pick prices");
            
            if (!truncateSuccess) {
                return false;
            }
            
            // Create new price record with defaults
            PicksPrice priceData = createDefaultPicksPrice(season, seasonType);
            System.out.println("Created default picks price record for season: " + season);
            
            // Update the database with new record
            boolean priceUpdateSuccess = sqlConnectorPicksPriceTable.updatePickPrices(priceData);
            System.out.println("Price update success: " + priceUpdateSuccess);
            
            messages.add(priceUpdateSuccess ? "*Successfully set new season type to " + seasonType : 
                                            "Failed to set season type");
            
            return teamsSuccess && picksDeleted && usersDeleted && truncateSuccess && priceUpdateSuccess;
            
        } catch (Exception e) {
            System.err.println("Error in processScheduleCreation: " + e.getMessage());
            e.printStackTrace();
            messages.add("Error: " + e.getMessage());
            return false;
        }
    }
    
    private PicksPrice createDefaultPicksPrice(int season, String seasonType) {
        PicksPrice priceData = new PicksPrice();
        priceData.setPicksPriceSeason(season);
        priceData.setKothSeason(seasonType);
        priceData.setMaxPicks(5);
        priceData.setPickPrice1(new BigDecimal("99.99"));
        priceData.setPickPrice2(new BigDecimal("99.99"));
        priceData.setPickPrice3(new BigDecimal("99.99"));
        priceData.setPickPrice4(new BigDecimal("99.99"));
        priceData.setPickPrice5(new BigDecimal("99.99"));
        priceData.setAllowSignUp(true);
        return priceData;
    }

    private void handleScheduleError(Exception e, PrintWriter out, ObjectMapper mapper) throws IOException {
        System.err.println("CommissionerServlet: Error in handleCreateSchedule: " + e.getMessage());
        e.printStackTrace();
        out.write(mapper.writeValueAsString(Map.of(
            "success", false,
            "message", "Error creating schedule: " + e.getMessage()
        )));
    }
}