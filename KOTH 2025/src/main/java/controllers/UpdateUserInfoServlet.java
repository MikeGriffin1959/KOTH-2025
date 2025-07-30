package controllers;

import helpers.SqlConnectorUserTable;
import model.User;
import services.ServletUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class UpdateUserInfoServlet {

    private final SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private ServletUtility servletUtility;  // ✅ Inject instead of static calls

    @Autowired
    public UpdateUserInfoServlet(SqlConnectorUserTable sqlConnectorUserTable) {
        this.sqlConnectorUserTable = sqlConnectorUserTable;
    }

    @GetMapping("/UpdateUserInfoServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doGet method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (GET)");
            return "redirect:/LoginServlet";
        }

        // ✅ Use injected ServletUtility
        servletUtility.setCommonAttributes(request, request.getServletContext());

        String userName = (String) session.getAttribute("userName");
        User user = sqlConnectorUserTable.getUserByUsername(userName);
        if (user == null) {
            model.addAttribute("errorMessage", "User not found.");
            return "error";
        }

        model.addAttribute("user", user);

        long endTime = System.nanoTime();
        System.out.printf("UpdateUserInfoServlet.doGet Method execution time: %.1f Seconds%n",
                (endTime - startTime) / 1_000_000_000.0);

        return "updateUserInfo";
    }

    @PostMapping("/UpdateUserInfoServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes)
            throws ServletException, IOException {
        System.out.println("UpdateUserInfoServlet: doPost method called");
        long startTime = System.nanoTime();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            System.out.println("UpdateUserInfoServlet: Redirecting to login (POST)");
            return "redirect:/LoginServlet";
        }

        servletUtility.setCommonAttributes(request, request.getServletContext());

        String currentUserName = (String) session.getAttribute("userName");
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String userName = request.getParameter("userName");
        String email = request.getParameter("email");
        String cellNumber = request.getParameter("cellNumber");

        // ✅ Validate inputs
        if (isEmpty(firstName) || isEmpty(lastName) || isEmpty(userName) || isEmpty(email) || isEmpty(cellNumber)) {
            redirectAttributes.addFlashAttribute("message", "All fields are required.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // ✅ Validate username uniqueness
        if (!currentUserName.equals(userName) && sqlConnectorUserTable.usernameExists(userName)) {
            redirectAttributes.addFlashAttribute("message", "Username already exists. Choose a different one.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/UpdateUserInfoServlet";
        }

        // ✅ Update user info in DB
        boolean isUpdated = sqlConnectorUserTable.updateUserInfo(currentUserName, firstName, lastName, userName, email, cellNumber);

        if (isUpdated) {
            session.setAttribute("userName", userName);
            redirectAttributes.addFlashAttribute("message", "User information updated successfully.");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } else {
            redirectAttributes.addFlashAttribute("message", "Failed to update user information. Please try again.");
            redirectAttributes.addFlashAttribute("messageType", "error");
        }

        long endTime = System.nanoTime();
        System.out.printf("UpdateUserInfoServlet.doPost Method execution time: %.1f Seconds%n",
                (endTime - startTime) / 1_000_000_000.0);

        return "redirect:/UpdateUserInfoServlet";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}



