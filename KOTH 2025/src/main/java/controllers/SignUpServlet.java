package controllers;

import services.PasswordValidator;
import helpers.SqlConnectorPicksPriceTable;
import helpers.SqlConnectorUserTable;
import model.PicksPrice;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import services.CommonProcessingService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

@Controller
public class SignUpServlet {

    private final SqlConnectorUserTable sqlConnectorUserTable;
    private final SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private CommonProcessingService commonProcessingService; // ✅ Added for cache refresh

    @Autowired
    public SignUpServlet(SqlConnectorUserTable sqlConnectorUserTable,
                          SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
        this.sqlConnectorPicksPriceTable = sqlConnectorPicksPriceTable;
    }

    @GetMapping("/SignUpServlet")
    public String doGet(Model model) {
        // Get current year for season
        int currentSeason = Calendar.getInstance().get(Calendar.YEAR);

        // Get pick prices for current season
        List<PicksPrice> picksPrices = sqlConnectorPicksPriceTable.getPickPrices(currentSeason);

        if (!picksPrices.isEmpty()) {
            PicksPrice currentPrices = picksPrices.get(0);
            model.addAttribute("maxPicks", currentPrices.getMaxPicks());
            model.addAttribute("pickPrice1", currentPrices.getPickPrice1());
            model.addAttribute("pickPrice2", currentPrices.getPickPrice2());
            model.addAttribute("pickPrice3", currentPrices.getPickPrice3());
            model.addAttribute("pickPrice4", currentPrices.getPickPrice4());
            model.addAttribute("pickPrice5", currentPrices.getPickPrice5());
        } else {
            model.addAttribute("maxPicks", 5); // Default
        }

        // Default initial picks to 1 if not set
        model.addAttribute("initialPicks", 1);

        // Add password requirements to the model for use in JSP
        model.addAttribute("passwordRegex", PasswordValidator.getJavaScriptRegex());
        model.addAttribute("passwordRequirements", PasswordValidator.getRequirements());
        return "signUp";
    }

    @PostMapping("/SignUpServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirectAttributes)
            throws ServletException, IOException {
        System.out.println("SignUpServlet.doPost() called");

        // Get form parameters
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String username = request.getParameter("username");
        String email = request.getParameter("email");
        String cellNumber = request.getParameter("cellNumber");
        String password = request.getParameter("password");
        String initialPicksStr = request.getParameter("initialPicks");

        // Preserve form data in case of error
        model.addAttribute("firstName", firstName);
        model.addAttribute("lastName", lastName);
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("cellNumber", cellNumber);
        model.addAttribute("initialPicks", initialPicksStr);

        // Add password requirements to model
        model.addAttribute("passwordRegex", PasswordValidator.getJavaScriptRegex());
        model.addAttribute("passwordRequirements", PasswordValidator.getRequirements());

        try {
            // Validate password
            if (!PasswordValidator.isValid(password)) {
                model.addAttribute("error", PasswordValidator.getRequirements());
                return "signUp";
            }

            // Check if username already exists
            if (sqlConnectorUserTable.usernameExists(username)) {
                model.addAttribute("error", "Username '" + username + "' is already taken. Please choose a different username.");
                return "signUp";
            }

            // Check if email already exists
            if (sqlConnectorUserTable.emailExists(email)) {
                model.addAttribute("error", "Email address '" + email + "' is already registered. Please use a different email or try logging in.");
                return "signUp";
            }

            // Get current season and parse initial picks
            int currentSeason = Calendar.getInstance().get(Calendar.YEAR);
            int initialPicks = Integer.parseInt(initialPicksStr);

            // Create and set up user object
            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setEmail(email);
            user.setCellNumber(cellNumber);
            user.setPassword(password);
            user.setAdmin(false);
            user.setCommish(false);
            user.setPicksSeason(currentSeason);
            user.setInitialPicks(initialPicks);
            user.setPicksPaid(false);

            // Add the user and get the generated ID
            int userId = sqlConnectorUserTable.addUser(user);

            // Create picks record
            User userPicks = new User();
            userPicks.setIdUser(userId);
            userPicks.setInitialPicks(initialPicks);
            userPicks.setPicksSeason(currentSeason);
            userPicks.setPicksPaid(false);

            sqlConnectorUserTable.addUserPicks(userPicks);

            // ✅ Refresh cache so new user data is immediately visible
            System.out.println("SignUpServlet: Refreshing application and session cache after new user signup...");
            commonProcessingService.ensureSessionData(request.getSession(), request.getServletContext());

            // Redirect to login with success message
            redirectAttributes.addFlashAttribute("signupSuccess", true);
            redirectAttributes.addFlashAttribute("username", username);
            return "redirect:/LoginServlet";

        } catch (SQLException e) {
            String errorMessage;
            if (e.getMessage().contains("Duplicate entry") && e.getMessage().contains("email")) {
                errorMessage = "This email address is already registered. Please use a different email or try logging in.";
            } else if (e.getMessage().contains("Duplicate entry") && e.getMessage().contains("username")) {
                errorMessage = "This username is already taken. Please choose a different username.";
            } else {
                errorMessage = "A database error occurred. Please try again later.";
                System.out.println("SignUpServlet: SQL Exception during sign up: " + e.getMessage());
                e.printStackTrace();
            }
            model.addAttribute("error", errorMessage);
        } catch (NumberFormatException e) {
            model.addAttribute("error", "Please enter a valid number for initial picks");
        } catch (Exception e) {
            System.out.println("SignUpServlet: Unexpected error during sign up: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "An unexpected error occurred. Please try again later.");
        }

        return "signUp";
    }
}

