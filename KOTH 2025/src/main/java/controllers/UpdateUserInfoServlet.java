package controllers;

import helpers.SqlConnectorUserTable;
import model.User;
import services.ServletUtility;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class UpdateUserInfoServlet {

    private final SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    public UpdateUserInfoServlet(SqlConnectorUserTable sqlConnectorUserTable) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
    }

    @GetMapping("/UpdateUserInfoServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model) throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doGet method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (GET)");
            return "redirect:/LoginServlet";
        }

        // Set common attributes
        ServletUtility.setCommonAttributes(request, request.getServletContext());

        // Debug logging for message attributes
        System.out.println("Message: " + session.getAttribute("message"));
        System.out.println("Message Type: " + session.getAttribute("messageType"));

        // Get user information
        String userName = (String) session.getAttribute("userName");
        User user = sqlConnectorUserTable.getUserByUsername(userName);
        model.addAttribute("user", user);

        System.out.println("UpdateUserInfoServlet: Returning updateUserInfo view (GET)");

        // End timing & calculate elapsed time for this method
        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("UpdateUserInfoServlet.doGet Method execution time: %.1f Seconds%n", durationInSeconds);

        return "updateUserInfo";
    }

    @PostMapping("/UpdateUserInfoServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doPost method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (POST)");
            return "redirect:/LoginServlet";
        }

        // Set common attributes
        ServletUtility.setCommonAttributes(request, request.getServletContext());

        String currentUserName = (String) session.getAttribute("userName");
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String userName = request.getParameter("userName");
        String email = request.getParameter("email");
        String cellNumber = request.getParameter("cellNumber");

        // Check if any of the fields are null or empty
        if (firstName == null || lastName == null || userName == null || email == null || cellNumber == null ||
            firstName.isEmpty() || lastName.isEmpty() || userName.isEmpty() || email.isEmpty() || cellNumber.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "All fields are required. Please fill in all the information.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // Check if the new username is unique (only if it's different from the current username)
        if (!currentUserName.equals(userName) && sqlConnectorUserTable.usernameExists(userName)) {
            redirectAttributes.addFlashAttribute("message", "Username already exists. Please choose a different username.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // Update user info
        boolean isUpdated = sqlConnectorUserTable.updateUserInfo(currentUserName, firstName, lastName, userName, email, cellNumber);

        if (isUpdated) {
            session.setAttribute("userName", userName); // Update session with new username
            redirectAttributes.addFlashAttribute("message", "User information updated successfully.");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } else {
            redirectAttributes.addFlashAttribute("message", "Failed to update user information. Please try again.");
            redirectAttributes.addFlashAttribute("messageType", "error");
        }

        // End timing & calculate elapsed time for this method
        long endTime = System.nanoTime();
        double durationInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("UpdateUserInfoServlet.doPost Method execution time: %.1f Seconds%n", durationInSeconds);

        return "redirect:/UpdateUserInfoServlet";
    }
}


