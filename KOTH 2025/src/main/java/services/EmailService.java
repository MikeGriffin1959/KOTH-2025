package services;


import javax.mail.MessagingException;  // For Java EE
import javax.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private String senderEmail;

    public void sendPasswordResetEmail(String to, String resetLink) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject("Password Reset Request");
        helper.setText("<h3>Reset Your Password</h3><p>Click the link below:</p>"
                + "<a href='" + resetLink + "'>Reset Password</a>", true);

        mailSender.send(message);
    }

    public void sendUsernameRecoveryEmail(String to, String username) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject("Username Recovery");
        helper.setText("<p>Your username is: <b>" + username + "</b></p>", true);

        mailSender.send(message);
    }
}
