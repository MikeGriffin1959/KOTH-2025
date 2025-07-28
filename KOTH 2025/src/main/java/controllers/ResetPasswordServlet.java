package controllers;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import services.PasswordResetService;
import org.springframework.context.ApplicationContext;
import config.ApplicationContextProvider;

@WebServlet("/ResetPasswordServlet")
public class ResetPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private PasswordResetService passwordResetService;

    @Override
    public void init() throws ServletException {
        super.init();
        ApplicationContext context = ApplicationContextProvider.getApplicationContext();
        passwordResetService = context.getBean(PasswordResetService.class);
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("ResetPasswordServlet: doGet() called");
        long startTime = System.nanoTime();
        String token = request.getParameter("token");
        if (token == null || token.isEmpty()) {
            response.sendRedirect("InitiatePasswordResetServlet");
            return;
        }
        request.setAttribute("token", token);
        request.getRequestDispatcher("resetPassword.jsp").forward(request, response);
        
        long endTime = System.nanoTime();
        long durationInNano = endTime - startTime;
        double durationInSeconds = durationInNano / 1_000_000_000.0;
        String formattedDuration = String.format("%.1f", durationInSeconds);
        System.out.println("ResetPasswordServlet.doGet Method execution time: " + formattedDuration + " Seconds");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("ResetPasswordServlet: doPost() called");
        long startTime = System.nanoTime();
        
        String token = request.getParameter("token");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        if (token == null || newPassword == null || confirmPassword == null) {
            request.setAttribute("error", "All fields are required");
            request.getRequestDispatcher("resetPassword.jsp").forward(request, response);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            request.setAttribute("error", "Passwords do not match");
            request.getRequestDispatcher("resetPassword.jsp").forward(request, response);
            return;
        }

        try {
            boolean resetSuccess = passwordResetService.resetPassword(token, newPassword);
            if (resetSuccess) {
                // Redirect to login page after successful password reset
                response.sendRedirect("login.jsp");
                return;
            } else {
                request.setAttribute("error", "Password reset failed. Please try again or request a new reset link.");
                request.getRequestDispatcher("resetPassword.jsp").forward(request, response);
            }
        } catch (Exception e) {
            request.setAttribute("error", "An error occurred: " + e.getMessage());
            request.getRequestDispatcher("resetPassword.jsp").forward(request, response);
        }
        
        long endTime = System.nanoTime();
        long durationInNano = endTime - startTime;
        double durationInSeconds = durationInNano / 1_000_000_000.0;
        String formattedDuration = String.format("%.1f", durationInSeconds);
        System.out.println("ResetPasswordServlet.doPost Method execution time: " + formattedDuration + " Seconds");
    }
}