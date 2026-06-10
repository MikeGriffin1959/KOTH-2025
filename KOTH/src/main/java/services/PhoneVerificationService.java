package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.exception.TwilioException;
import jakarta.annotation.PostConstruct;

/**
 * Handles phone number verification using the Twilio Verify API.
 * Ported from GolferFest — same Twilio account/Verify service.
 *
 * Twilio Verify manages the entire flow: code generation, delivery,
 * expiration, and rate limiting. No need to store codes ourselves.
 */
@Service
public class PhoneVerificationService {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.verify.service.sid:}")
    private String verifyServiceSid;

    @PostConstruct
    public void init() {
        if (accountSid == null || accountSid.isEmpty() || "placeholder".equalsIgnoreCase(accountSid)) {
            System.out.println("PhoneVerificationService: Twilio not configured — verification disabled");
            return;
        }
        Twilio.init(accountSid, authToken);
        System.out.println("PhoneVerificationService initialized with Verify SID: " + verifyServiceSid);
    }

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isEmpty() && !"placeholder".equalsIgnoreCase(accountSid);
    }

    /**
     * Send a verification code to the given phone number (E.164 format).
     * Twilio handles code generation, delivery, and expiration (10 min default).
     * @return the verification status ("pending" on success)
     */
    public String sendVerificationCode(String phoneNumber) throws TwilioException {
        try {
            Verification verification = Verification.creator(
                    verifyServiceSid,
                    phoneNumber,
                    "sms"
            ).create();

            System.out.println("PhoneVerificationService: code sent to " + phoneNumber
                    + " | Status: " + verification.getStatus());
            return verification.getStatus(); // "pending"
        } catch (TwilioException e) {
            System.err.println("PhoneVerificationService: failed to send to " + phoneNumber
                    + ". Error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Check the verification code entered by the user.
     * @return true if the code is correct, false if invalid/expired
     */
    public boolean checkVerificationCode(String phoneNumber, String code) throws TwilioException {
        try {
            VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();

            boolean approved = "approved".equals(check.getStatus());
            System.out.println("PhoneVerificationService: check for " + phoneNumber
                    + " | Status: " + check.getStatus());
            return approved;
        } catch (TwilioException e) {
            // Twilio throws for invalid/expired codes in some cases
            System.err.println("PhoneVerificationService: check failed for " + phoneNumber
                    + ". Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Normalize a phone number to E.164 format. Basic US handling.
     */
    public static String normalizePhoneNumber(String raw) {
        if (raw == null) return null;

        String cleaned = raw.replaceAll("[^+\\d]", "");

        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.length() == 10) {
            return "+1" + cleaned;
        }
        if (cleaned.length() == 11 && cleaned.startsWith("1")) {
            return "+" + cleaned;
        }
        return cleaned;
    }
}
