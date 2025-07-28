package controllers;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import helpers.LoginResult;
import helpers.SqlConnectorUserTable;
import helpers.SqlConnectorPicksPriceTable;
import model.User;
import model.PicksPrice;
import services.ServletUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Override
    public void init() throws ServletException {
        // Enable Spring dependency injection in servlet
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("LoginServlet.doGet() called");
        long startTime = System.nanoTime();

        // Set common attributes for session & app context
        ServletUtility.setCommonAttributes(request, getServletContext());

        // Update KOTH settings
        updateKothSettings();

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userName") != null) {
            response.sendRedirect("HomeServlet");
            return;
        }

        String signupSuccess = request.getParameter("signupSuccess");
        if ("true".equals(signupSuccess)) {
            request.setAttribute("message", "Sign up successful! Please log in.");
        }

        request.getRequestDispatcher("login.jsp").forward(request, response);
    }

    private void updateKothSettings() {
        // Your logic to refresh KOTH-related data
        System.out.println("Updating KOTH settings...");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        LoginResult result = sqlConnectorUserTable.authenticateUser(username, password);

        if (result == LoginResult.SUCCESS) {
            HttpSession session = request.getSession();
            session.setAttribute("userName", username);
            response.sendRedirect("HomeServlet");
        } else {
            request.setAttribute("errorMessage", "Invalid username or password.");
            request.getRequestDispatcher("login.jsp").forward(request, response);
        }
    }
}
