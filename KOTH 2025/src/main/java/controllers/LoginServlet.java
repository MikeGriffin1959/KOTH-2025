package controllers;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import helpers.LoginResult;
import helpers.SqlConnectorUserTable;
import helpers.SqlConnectorPicksPriceTable;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import services.ServletUtility;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private ServletUtility servletUtility; // ✅ Autowired Spring Bean

    @Override
    public void init() throws ServletException {
        super.init();
        // ✅ Enable Spring DI in this servlet
        WebApplicationContext springContext =
                WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        springContext.getAutowireCapableBeanFactory().autowireBean(this);
        System.out.println("LoginServlet initialized with Spring beans.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("LoginServlet.doGet() called");

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userName") != null) {
            response.sendRedirect("HomeServlet");
            return;
        }

        // ✅ Call ServletUtility to set allowSignUp and other attributes
        servletUtility.setCommonAttributes(request, getServletContext());

        // If signup was successful, show message
        String signupSuccess = request.getParameter("signupSuccess");
        if ("true".equals(signupSuccess)) {
            request.setAttribute("message", "Sign up successful! Please log in.");
        }

        // ✅ Forward to login.jsp (instead of redirect to HomeServlet)
        request.getRequestDispatcher("login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        System.out.println("LoginServlet.doPost() called");

        String username = request.getParameter("userName");
        String password = request.getParameter("password");

        LoginResult result = sqlConnectorUserTable.isValidUser(username, password);

        if (result == LoginResult.SUCCESS) {
            User user = sqlConnectorUserTable.getUserByUsername(username);

            if (user != null) {
                // Fetch roles
                Map<String, Boolean> roles = sqlConnectorUserTable.getUserRoles(username);

                // Set session attributes
                HttpSession session = request.getSession(true);
                session.setAttribute("userName", user.getUsername());
                session.setAttribute("userId", user.getIdUser());
                session.setAttribute("isAdmin", roles.getOrDefault("isAdmin", false));
                session.setAttribute("isCommish", roles.getOrDefault("isCommish", false));

                // Redirect to home page
                response.sendRedirect("HomeServlet");
            } else {
                // Handle user not found
                request.setAttribute("errorMessage", "User not found.");
                request.getRequestDispatcher("login.jsp").forward(request, response);
            }
        } else {
            // Handle invalid login
            request.setAttribute("errorMessage", "Invalid username or password.");
            request.getRequestDispatcher("login.jsp").forward(request, response);
        }
    }
}


