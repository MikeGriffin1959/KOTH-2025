package controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.mail.MailException;

import java.sql.SQLException;
import services.PasswordResetService;

@Controller
public class InitiatePasswordResetServlet {

    private final PasswordResetService passwordResetService;

    @Autowired
    public InitiatePasswordResetServlet(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }
    
    @GetMapping("/InitiatePasswordResetServlet")
    public String doGet() {
        System.out.println("InitiatePasswordResetServlet: doGet() called");
        long startTime = System.nanoTime();
        
        // Log execution time
        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("InitiatePasswordResetServlet.doGet Method execution time: %.1f Seconds%n", durationInSeconds);
        
        return "initiatePasswordReset";
    }

    @PostMapping("/InitiatePasswordResetServlet")
    public String doPost(@RequestParam String email, Model model) {
        System.out.println("InitiatePasswordResetServlet: doPost() called");
        long startTime = System.nanoTime();

        if (email == null || email.isEmpty()) {
            model.addAttribute("error", "Email is required");
            return "initiatePasswordReset";
        }
        
        try {
            passwordResetService.initiatePasswordReset(email);
            model.addAttribute("success", "A password reset link has been sent");
        } catch (MailException e) {
            model.addAttribute("error", "Failed to send password reset email. Please try again later");
            System.err.println("MailException: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            model.addAttribute("error", "A database error occurred. Please try again later.");
            System.err.println("SQLException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
            System.err.println("Unexpected Exception: " + e.getMessage());
            e.printStackTrace();
        }

        // Log execution time
        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("InitiatePasswordResetServlet.doPost Method execution time: %.1f Seconds%n", durationInSeconds);

        return "initiatePasswordReset";
    }
}