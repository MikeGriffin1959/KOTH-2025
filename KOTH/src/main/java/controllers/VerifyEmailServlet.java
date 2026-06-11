package controllers;

import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import services.EmailVerificationService;

/**
 * Landing endpoint for the emailed verification link
 * (/VerifyEmailServlet?token=...). Deliberately does NOT require a session —
 * it is clicked from an inbox, usually while logged out. The token is an
 * unguessable one-time UUID. Ported from GolferFest.
 */
@Controller
public class VerifyEmailServlet {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @GetMapping("/VerifyEmailServlet")
    public String doGet(@RequestParam(value = "token", required = false) String token,
                        RedirectAttributes redirectAttributes) {
        System.out.println("VerifyEmailServlet.doGet called");
        User user = emailVerificationService.verifyToken(token);
        if (user != null) {
            redirectAttributes.addFlashAttribute("message",
                "Email verified! Thanks, " + user.getFirstName() + " - you're all set.");
        } else {
            redirectAttributes.addFlashAttribute("error",
                "That verification link is invalid or was already used. "
                + "You can request a new one from the Update User Info page.");
        }
        return "redirect:/LoginServlet";
    }
}
