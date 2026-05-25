package controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import helpers.SqlConnectorUserTable;

import org.springframework.mail.MailException;
import java.sql.SQLException;
import services.EmailService;
import model.User;

@Controller
public class InitiateUsernameRecoveryServlet {
    private final EmailService emailService;
    private final SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    public InitiateUsernameRecoveryServlet(EmailService emailService, SqlConnectorUserTable sqlConnectorUserTable) {
        this.emailService = emailService;
        this.sqlConnectorUserTable = sqlConnectorUserTable;
    }
    
    @GetMapping("/InitiateUsernameRecoveryServlet")
    public String doGet() {
        System.out.println("InitiateUsernameRecoveryServlet: doGet() called");
        return "initiateUsernameRecovery";
    }

    @PostMapping("/InitiateUsernameRecoveryServlet")
    public String doPost(@RequestParam String email, Model model) {
        System.out.println("InitiateUsernameRecoveryServlet: doPost() called");
        long startTime = System.nanoTime();

        if (email == null || email.isEmpty()) {
            model.addAttribute("error", "Email is required");
            return "initiateUsernameRecovery";
        }
        
        try {
            User user = sqlConnectorUserTable.getUserByEmail(email);
            if (user != null) {
                emailService.sendUsernameRecoveryEmail(email, user.getUsername());
                model.addAttribute("success", "Your username has been sent to your email address");
            } else {
                model.addAttribute("error", "No account found with that email address");
            }
        } catch (SQLException e) {
            model.addAttribute("error", "A database error occurred. Please try again later.");
            System.err.println("SQLException: " + e.getMessage());
            e.printStackTrace();
        } catch (MailException e) {
            model.addAttribute("error", "Failed to send email. Please try again later.");
            System.err.println("MailException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
            System.err.println("Unexpected Exception: " + e.getMessage());
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("InitiateUsernameRecoveryServlet.doPost Method execution time: %.1f Seconds%n", durationInSeconds);
        
        return "initiateUsernameRecovery";
    }
}
