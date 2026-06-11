package services;

import helpers.SqlConnectorUserTable;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Email verification (ported from GolferFest). Generates a one-time UUID token,
 * stores it on KOTH.User.emailVerifyToken, and emails a clickable link to
 * /VerifyEmailServlet?token=... . The token is consumed (cleared) on success.
 */
@Service
public class EmailVerificationService {

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private EmailService emailService;

    // Includes the /KOTH context path; prod overrides via APP_BASE_URL env prop.
    @Value("${app.base.url:http://localhost:8081/KOTH}")
    private String appBaseUrl;

    /**
     * Generate + store a token and send the verification link to the user's
     * current email address. Returns true if the email was handed to SMTP.
     */
    public boolean initiateEmailVerification(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            return false;
        }
        String token = UUID.randomUUID().toString();
        if (!sqlConnectorUserTable.setEmailVerifyToken(user.getIdUser(), token)) {
            System.err.println("EmailVerificationService: could not store token for user " + user.getIdUser());
            return false;
        }

        String verificationLink = appBaseUrl + "/VerifyEmailServlet?token=" + token;
        try {
            emailService.sendEmailVerificationEmail(user.getEmail(), verificationLink);
            System.out.println("EmailVerificationService: verification email sent to user " + user.getIdUser());
            return true;
        } catch (Exception e) {
            System.err.println("EmailVerificationService: send failed for user " + user.getIdUser()
                    + ": " + e.getMessage());
            sqlConnectorUserTable.clearEmailVerifyToken(user.getIdUser());
            return false;
        }
    }

    /**
     * Consume a token from the emailed link. Returns the verified User, or null
     * if the token is unknown/already used.
     */
    public User verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        User user = sqlConnectorUserTable.getUserByEmailVerifyToken(token.trim());
        if (user == null) {
            return null;
        }
        if (!sqlConnectorUserTable.markEmailVerified(user.getIdUser())) {
            return null;
        }
        System.out.println("EmailVerificationService: email verified for user " + user.getIdUser());
        return user;
    }
}
