package controllers;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import helpers.LoginResult;
import helpers.SqlConnectorUserTable;
import model.User;
import helpers.SqlConnectorPicksPriceTable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private SqlConnectorUserTable sqlConnectorUserTable;
    @SuppressWarnings("unused")
	private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Override
    public void init() throws ServletException {
        // Get Spring WebApplicationContext and fetch beans manually
        WebApplicationContext springContext =
                WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());

        this.sqlConnectorUserTable = springContext.getBean(SqlConnectorUserTable.class);
        this.sqlConnectorPicksPriceTable = springContext.getBean(SqlConnectorPicksPriceTable.class);
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

        String signupSuccess = request.getParameter("signupSuccess");
        if ("true".equals(signupSuccess)) {
            request.setAttribute("message", "Sign up successful! Please log in.");
        }

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
                response.sendRedirect("home.jsp");
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
