package controllers;

import helpers.SmsPreferencesDAO;
import helpers.SqlConnectorUserTable;
import model.User;
import services.PhoneVerificationService;
import services.ServletUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class UpdateUserInfoServlet {

    private final SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private ServletUtility servletUtility;  // ✅ Inject instead of static calls

    @Autowired
    private PhoneVerificationService phoneVerificationService; // SMS feature

    @Autowired
    private SmsPreferencesDAO smsPreferencesDAO; // SMS feature

    @Autowired
    private services.EmailVerificationService emailVerificationService; // Email verification

    @Autowired
    public UpdateUserInfoServlet(SqlConnectorUserTable sqlConnectorUserTable) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
    }

    @GetMapping("/UpdateUserInfoServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doGet method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (GET)");
            return "redirect:/LoginServlet";
        }

        // ✅ Use injected ServletUtility
        servletUtility.setCommonAttributes(request, request.getServletContext());

        String userName = (String) session.getAttribute("userName");
        User user = sqlConnectorUserTable.getUserByUsername(userName);
        if (user == null) {
            model.addAttribute("errorMessage", "User not found.");
            return "error";
        }

        model.addAttribute("user", user);
        // SMS notification preferences for the Text Message card
        model.addAttribute("smsPrefs", smsPreferencesDAO.getUserPreferencesDetail(user.getIdUser()));

        long endTime = System.nanoTime();
        System.out.printf("UpdateUserInfoServlet.doGet Method execution time: %.1f Seconds%n",
                (endTime - startTime) / 1_000_000_000.0);

        return "updateUserInfo";
    }

    @PostMapping("/UpdateUserInfoServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes)
            throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doPost method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (POST)");
            return "redirect:/LoginServlet";
        }

        servletUtility.setCommonAttributes(request, request.getServletContext());
        String currentUserName = (String) session.getAttribute("userName");

        // SMS feature actions (mirrors GolferFest's UpdateUserInfoServlet)
        String action = request.getParameter("action");
        if ("sendVerificationCode".equals(action)) {
            handleSendVerificationCode(request, response);
            return null;
        }
        if ("checkVerificationCode".equals(action)) {
            handleCheckVerificationCode(request, response, currentUserName);
            return null;
        }
        if ("updateSmsPrefs".equals(action)) {
            handleUpdateSmsPrefs(request, redirectAttributes, currentUserName);
            return "redirect:/UpdateUserInfoServlet";
        }
        if ("resendVerificationEmail".equals(action)) {
            handleResendVerificationEmail(response, currentUserName);
            return null;
        }

        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String userName = request.getParameter("userName");
        String email = request.getParameter("email");
        String cellNumber = request.getParameter("cellNumber");

        // ✅ Validate inputs
        if (isEmpty(firstName) || isEmpty(lastName) || isEmpty(userName) || isEmpty(email) || isEmpty(cellNumber)) {
            redirectAttributes.addFlashAttribute("message", "All fields are required.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // ✅ Validate username uniqueness
        if (!currentUserName.equals(userName) && sqlConnectorUserTable.usernameExists(userName)) {
            redirectAttributes.addFlashAttribute("message", "Username already exists. Choose a different one.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // If the cell number changed, the old verification no longer applies.
        User existing = sqlConnectorUserTable.getUserByUsername(currentUserName);
        boolean phoneChanged = existing != null && existing.isPhoneVerified()
                && existing.getCellNumber() != null
                && !PhoneVerificationService.normalizePhoneNumber(existing.getCellNumber())
                        .equals(PhoneVerificationService.normalizePhoneNumber(cellNumber));
        // Same for the email address.
        boolean emailChanged = existing != null && existing.getEmail() != null
                && !existing.getEmail().equalsIgnoreCase(email.trim());

        // ✅ Update user info in DB
        boolean isUpdated = sqlConnectorUserTable.updateUserInfo(currentUserName, firstName, lastName, userName, email, cellNumber);

        if (isUpdated) {
            if (phoneChanged) {
                smsPreferencesDAO.clearPhoneVerification(existing.getIdUser());
                System.out.println("UpdateUserInfoServlet: cell number changed — cleared phone verification for user " + existing.getIdUser());
            }
            if (emailChanged) {
                sqlConnectorUserTable.clearEmailVerification(existing.getIdUser());
                try {
                    existing.setEmail(email.trim());
                    emailVerificationService.initiateEmailVerification(existing);
                } catch (Exception mailEx) {
                    System.err.println("UpdateUserInfoServlet: verification email failed (non-fatal): " + mailEx.getMessage());
                }
                System.out.println("UpdateUserInfoServlet: email changed — verification cleared, new link sent for user " + existing.getIdUser());
            }
            session.setAttribute("userName", userName);
            redirectAttributes.addFlashAttribute("message", "User information updated successfully."
                    + (phoneChanged ? " Your new number needs to be re-verified for text messages." : "")
                    + (emailChanged ? " A verification link was sent to your new email address." : ""));
            redirectAttributes.addFlashAttribute("messageType", "success");
        } else {
            redirectAttributes.addFlashAttribute("message", "Failed to update user information. Please try again.");
            redirectAttributes.addFlashAttribute("messageType", "error");
        }

        long endTime = System.nanoTime();
        System.out.printf("UpdateUserInfoServlet.doPost Method execution time: %.1f Seconds%n",
                (endTime - startTime) / 1_000_000_000.0);

        return "redirect:/UpdateUserInfoServlet";
    }

    // -------------------------------------------------------------------------
    // SMS feature handlers (AJAX, JSON responses)
    // -------------------------------------------------------------------------

    private void handleSendVerificationCode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        try {
            if (!phoneVerificationService.isConfigured()) {
                writeJson(response, false, "Text messaging is not configured");
                return;
            }
            String phone = PhoneVerificationService.normalizePhoneNumber(request.getParameter("phone"));
            if (phone == null || phone.length() < 11) {
                writeJson(response, false, "Enter a valid phone number first");
                return;
            }
            phoneVerificationService.sendVerificationCode(phone);
            writeJson(response, true, "Verification code sent to " + phone);
        } catch (Exception e) {
            System.err.println("UpdateUserInfoServlet.handleSendVerificationCode - Error: " + e.getMessage());
            writeJson(response, false, "Could not send code: " + e.getMessage());
        }
    }

    private void handleCheckVerificationCode(HttpServletRequest request, HttpServletResponse response,
                                             String currentUserName) throws IOException {
        response.setContentType("application/json");
        try {
            String phone = PhoneVerificationService.normalizePhoneNumber(request.getParameter("phone"));
            String code = request.getParameter("code");
            if (code == null || !code.matches("\\d{6}")) {
                writeJson(response, false, "Enter the 6-digit code");
                return;
            }
            boolean approved = phoneVerificationService.checkVerificationCode(phone, code);
            if (!approved) {
                writeJson(response, false, "Invalid or expired code — try again");
                return;
            }
            User user = sqlConnectorUserTable.getUserByUsername(currentUserName);
            if (user == null) {
                writeJson(response, false, "User not found");
                return;
            }
            smsPreferencesDAO.markPhoneVerified(user.getIdUser(), phone);
            smsPreferencesDAO.initializeDefaultPreferences(user.getIdUser());
            writeJson(response, true, "Phone verified!");
        } catch (Exception e) {
            System.err.println("UpdateUserInfoServlet.handleCheckVerificationCode - Error: " + e.getMessage());
            writeJson(response, false, "Verification failed: " + e.getMessage());
        }
    }

    private void handleUpdateSmsPrefs(HttpServletRequest request, RedirectAttributes redirectAttributes,
                                      String currentUserName) {
        try {
            User user = sqlConnectorUserTable.getUserByUsername(currentUserName);
            if (user == null) return;

            Map<String, Boolean> prefs = new LinkedHashMap<>();
            for (Map<String, Object> p : smsPreferencesDAO.getUserPreferencesDetail(user.getIdUser())) {
                String typeKey = (String) p.get("typeKey");
                prefs.put(typeKey, request.getParameter("pref_" + typeKey) != null);
            }
            smsPreferencesDAO.updatePreferences(user.getIdUser(), prefs);
            redirectAttributes.addFlashAttribute("message", "Text message preferences saved.");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            System.err.println("UpdateUserInfoServlet.handleUpdateSmsPrefs - Error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("message", "Failed to save preferences.");
            redirectAttributes.addFlashAttribute("messageType", "error");
        }
    }

    private void handleResendVerificationEmail(HttpServletResponse response, String currentUserName) throws IOException {
        response.setContentType("application/json");
        try {
            User user = sqlConnectorUserTable.getUserByUsername(currentUserName);
            if (user == null) {
                writeJson(response, false, "User not found");
                return;
            }
            if (user.isEmailVerified()) {
                writeJson(response, false, "Your email is already verified");
                return;
            }
            boolean sent = emailVerificationService.initiateEmailVerification(user);
            writeJson(response, sent,
                    sent ? "Verification link sent to " + user.getEmail()
                         : "Could not send the verification email — try again later");
        } catch (Exception e) {
            System.err.println("UpdateUserInfoServlet.handleResendVerificationEmail - Error: " + e.getMessage());
            writeJson(response, false, "Error: " + e.getMessage());
        }
    }

    private void writeJson(HttpServletResponse response, boolean success, String message) throws IOException {
        response.getWriter().write(String.format("{\"success\": %b, \"message\": \"%s\"}",
                success, message.replace("\"", "'")));
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
