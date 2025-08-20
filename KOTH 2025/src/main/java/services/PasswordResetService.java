package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import helpers.SqlConnectorUserTable;
import model.User;

import jakarta.mail.MessagingException;
import java.sql.SQLException;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private EmailService emailService;

    public void initiatePasswordReset(String email) throws SQLException, MailException, MessagingException {
        User user = sqlConnectorUserTable.getUserByEmail(email);
        if (user != null) {
            String token = UUID.randomUUID().toString();
            sqlConnectorUserTable.setResetToken(user.getIdUser(), token);

            String resetLink = "http://koth.bingmerfest.com/ResetPasswordServlet?token=" + token;

            // Call EmailService to send the reset email
            emailService.sendPasswordResetEmail(email, resetLink);
        }
    }

    public boolean resetPassword(String token, String newPassword) throws SQLException {
        User user = sqlConnectorUserTable.getUserByResetToken(token);
        if (user != null) {
            sqlConnectorUserTable.updatePassword(user.getIdUser(), newPassword);
            sqlConnectorUserTable.clearResetToken(user.getIdUser());
            return true;
        }
        return false;
    }
}
