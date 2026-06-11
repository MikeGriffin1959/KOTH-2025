package controllers;

import services.PasswordValidator;
import helpers.SmsPreferencesDAO;
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
import services.EmailVerificationService;
import services.PhoneVerificationService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

@Controller
public class SignUpServlet {

    /** Session attribute remembering which phone was verified pre-signup. */
    private static final String VERIFIED_PHONE_ATTR = "signupVerifiedPhone";
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{9,14}$");

    private final SqlConnectorUserTable sqlConnectorUserTable;
    private final SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private CommonProcessingService commonProcessingService; // ✅ Added for cache refresh

    @Autowired
    private PhoneVerificationService phoneVerificationService; // SMS verification at signup

    @Autowired
    private SmsPreferencesDAO smsPreferencesDAO; // persist verification + seed prefs

    @Autowired
    private EmailVerificationService emailVerificationService; // email link at signup

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

        // AJAX phone-verification actions (mirrors GolferFest's signup flow)
        String action = request.getParameter("action");
        if ("sendVerificationCode".equals(action)) {
            handleSendVerificationCode(request, response);
            return null;
        }
        if ("checkVerificationCode".equals(action)) {
            handleCheckVerificationCode(request, response);
            return null;
        }

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

            // Phone must have been verified in this session (JS gates the button,
            // but this is the authoritative server-side check).
            String normalizedCell = PhoneVerificationService.normalizePhoneNumber(
                    cellNumber != null ? cellNumber.trim() : null);
            Object verifiedPhone = request.getSession(true).getAttribute(VERIFIED_PHONE_ATTR);
            if (verifiedPhone == null || !verifiedPhone.equals(normalizedCell)) {
                model.addAttribute("error",
                        "Please verify your cell number before signing up "
                        + "(use the Send Verification Code button).");
                return "signUp";
            }
            cellNumber = normalizedCell; // store E.164

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

            // Phone was verified pre-signup: persist + seed SMS prefs (non-fatal)
            try {
                smsPreferencesDAO.markPhoneVerified(userId, cellNumber);
                smsPreferencesDAO.initializeDefaultPreferences(userId);
                request.getSession().removeAttribute(VERIFIED_PHONE_ATTR);
                System.out.println("SignUpServlet: user " + userId + " created phone-verified");
            } catch (Exception smsEx) {
                System.err.println("SignUpServlet: could not persist phone verification (non-fatal): "
                        + smsEx.getMessage());
            }

            // Auto-send the email verification link (non-fatal)
            try {
                user.setIdUser(userId);
                emailVerificationService.initiateEmailVerification(user);
            } catch (Exception mailEx) {
                System.err.println("SignUpServlet: verification email failed (non-fatal): "
                        + mailEx.getMessage());
            }

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

    // ── Signup phone-verification AJAX handlers (ported from GolferFest) ──

    private void handleSendVerificationCode(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        try {
            if (!phoneVerificationService.isConfigured()) {
                response.getWriter().write("{\"success\": false, \"message\": \"Text messaging is not configured\"}");
                return;
            }
            String phone = request.getParameter("phone");
            String normalized = PhoneVerificationService.normalizePhoneNumber(phone != null ? phone.trim() : null);
            if (normalized == null || !E164_PATTERN.matcher(normalized).matches()) {
                response.getWriter().write("{\"success\": false, \"message\": \"Invalid phone number format\"}");
                return;
            }
            String status = phoneVerificationService.sendVerificationCode(normalized);
            if ("pending".equals(status)) {
                response.getWriter().write("{\"success\": true, \"message\": \"Verification code sent to "
                        + normalized.replace("\"", "'") + "\"}");
            } else {
                response.getWriter().write("{\"success\": false, \"message\": \"Failed to send code. Status: "
                        + status + "\"}");
            }
        } catch (Exception e) {
            System.err.println("SignUpServlet.handleSendVerificationCode: " + e.getMessage());
            response.getWriter().write("{\"success\": false, \"message\": \"Error: "
                    + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void handleCheckVerificationCode(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        try {
            String phone = request.getParameter("phone");
            String code = request.getParameter("code");
            String normalized = PhoneVerificationService.normalizePhoneNumber(phone != null ? phone.trim() : null);
            boolean verified = phoneVerificationService.checkVerificationCode(normalized, code != null ? code.trim() : "");
            if (verified) {
                // Remember which number was verified for the final sign-up POST
                request.getSession(true).setAttribute(VERIFIED_PHONE_ATTR, normalized);
                response.getWriter().write("{\"success\": true, \"message\": \"Phone verified! You can finish signing up.\"}");
            } else {
                response.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired code. Please try again.\"}");
            }
        } catch (Exception e) {
            System.err.println("SignUpServlet.handleCheckVerificationCode: " + e.getMessage());
            response.getWriter().write("{\"success\": false, \"message\": \"Error: "
                    + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}

