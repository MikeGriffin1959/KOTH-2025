package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.twilio.exception.TwilioException;

import helpers.SmsPreferencesDAO;
import helpers.SmsPreferencesDAO.UserPhone;
import model.SmsNotificationType;
import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * SMS send service for KOTH. Ported from GolferFest's SmsService (same Twilio
 * account/number), adapted to KOTH's single-pool model: broadcasts are
 * season-scoped instead of group-scoped.
 */
@Service
public class SmsService {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;

    @Autowired
    private SmsPreferencesDAO smsPreferencesDAO;

    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            System.out.println("SmsService: Twilio not configured — SMS disabled");
            return;
        }
        Twilio.init(accountSid, authToken);
        System.out.println("SmsService initialized with number: " + twilioPhoneNumber);
    }

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isEmpty() && !"placeholder".equalsIgnoreCase(accountSid);
    }

    // =========================================================================
    // CORE SEND METHODS
    // =========================================================================

    /**
     * Send a raw SMS. No preference checking — use for commissioner/system messages.
     * @return the Twilio message SID
     */
    public String sendSms(String to, String messageBody) throws TwilioException {
        try {
            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilioPhoneNumber),
                    messageBody
            ).create();

            System.out.println("SmsService: SMS sent to " + to + " | SID: " + message.getSid());
            return message.getSid();
        } catch (TwilioException e) {
            System.err.println("SmsService: failed to send SMS to " + to + ". Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Preference-aware send. COMMISSIONER types always send (if verified);
     * USER types also check opt-in. Returns true if sent, false if skipped.
     */
    public boolean sendNotification(int userId, String phoneNumber,
                                    SmsNotificationType type, String messageBody) {
        if (!smsPreferencesDAO.shouldSendNotification(userId, type)) {
            System.out.println("SmsService: SMS skipped for user " + userId
                    + " | Type: " + type.name() + " (opted out or unverified)");
            return false;
        }
        try {
            sendSms(phoneNumber, messageBody);
            return true;
        } catch (TwilioException e) {
            System.err.println("SmsService: notification failed for user " + userId
                    + " | Type: " + type.name());
            return false;
        }
    }

    /**
     * Broadcast a notification to all eligible recipients globally (any season).
     * Filters by verification + opt-in. Returns count sent.
     */
    public int broadcastNotification(SmsNotificationType type, String messageBody) {
        return sendToAll(smsPreferencesDAO.getRecipientsForNotification(type), type.name(), messageBody);
    }

    /**
     * Broadcast a notification to the season's pool members. If commishOnly,
     * restrict to commissioners. Honors per-type opt-in + verification.
     */
    public int broadcastToSeason(SmsNotificationType type, int season,
                                 boolean commishOnly, String messageBody) {
        return sendToAll(smsPreferencesDAO.getSeasonRecipients(type, season, commishOnly),
                type.name() + " season=" + season, messageBody);
    }

    private int sendToAll(List<UserPhone> recipients, String label, String messageBody) {
        int successCount = 0;
        for (UserPhone recipient : recipients) {
            try {
                sendSms(recipient.phoneNumber, messageBody);
                successCount++;
            } catch (TwilioException e) {
                System.err.println("SmsService: broadcast failed for user " + recipient.userId
                        + " - " + e.getMessage());
            }
        }
        System.out.println("SmsService: broadcast [" + label + "] complete: "
                + successCount + "/" + recipients.size() + " sent.");
        return successCount;
    }

    // =========================================================================
    // USE-CASE WRAPPERS (formatted messages). More land with the M2+ scheduler:
    // picks reminders, elimination alerts, week recaps, live commentary pushes.
    // =========================================================================

    public boolean sendCommissionerMessage(int userId, String phone, String commName, String message) {
        String body = String.format("[KOTH] From %s: %s", commName, message);
        return sendNotification(userId, phone, SmsNotificationType.COMMISSIONER_MESSAGE, body);
    }

    public boolean sendEliminationAlert(int userId, String phone, String message) {
        String body = String.format("[KOTH] %s", message);
        return sendNotification(userId, phone, SmsNotificationType.ELIMINATION_ALERT, body);
    }

    public int broadcastWeekRecap(int season, String message) {
        return broadcastToSeason(SmsNotificationType.WEEK_RECAP, season, false,
                String.format("[KOTH] %s", message));
    }

    public int broadcastCommentaryEvent(int season, String message) {
        return broadcastToSeason(SmsNotificationType.COMMENTARY_EVENT, season, false,
                String.format("[KOTH] %s", message));
    }
}
