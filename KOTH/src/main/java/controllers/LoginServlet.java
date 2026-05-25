package controllers;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import helpers.LoginResult;
import helpers.SqlConnectorRememberMeTokenTable; // New import
import helpers.SqlConnectorUserTable;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import services.ServletUtility;
import services.CookieConfigurationService;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // How long the remember-me cookie / token lives
    private static final int REMEMBER_ME_DAYS = 30;
    private static final int REMEMBER_ME_COOKIE_MAX_AGE = 60 * 60 * 24 * REMEMBER_ME_DAYS;

    @Autowired
    private SqlConnectorUserTable sqlConnectorUserTable;

    @Autowired
    private SqlConnectorRememberMeTokenTable tokenTable; // New service

    @Autowired
    private ServletUtility servletUtility;

    @Autowired
    private CookieConfigurationService cookieConfigurationService;

    @Override
    public void init() throws ServletException {
        super.init();
        // Enable Spring DI in this servlet
        WebApplicationContext springContext =
                WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        springContext.getAutowireCapableBeanFactory().autowireBean(this);
        System.out.println("LoginServlet initialized with Spring beans.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("LoginServlet.doGet() called");

        // Already logged in -> straight to home
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userName") != null) {
            response.sendRedirect("HomeServlet");
            return;
        }

        // ---- Remember Me auto-login ----
        // No valid session: look for a remember-me cookie and restore the session if the token is valid.
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("rememberMe".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    try {
                        User user = tokenTable.getUserByToken(token);
                        if (user != null) {
                            Map<String, Boolean> roles = sqlConnectorUserTable.getUserRoles(user.getUsername());

                            HttpSession newSession = request.getSession(true);
                            // Configure session cookie for current environment
                            cookieConfigurationService.configureSessionCookie(request, response);

                            newSession.setAttribute("userName", user.getUsername());
                            newSession.setAttribute("userId", user.getIdUser());
                            newSession.setAttribute("isAdmin", roles.getOrDefault("isAdmin", false));
                            newSession.setAttribute("isCommish", roles.getOrDefault("isCommish", false));

                            System.out.println("LoginServlet: Auto-login via Remember Me for " + user.getUsername());
                            response.sendRedirect("HomeServlet");
                            return;
                        } else {
                            // Token missing/expired -> clear the stale cookie
                            System.out.println("LoginServlet: Remember Me token invalid or expired, clearing cookie");
                            clearRememberMeCookie(response);
                        }
                    } catch (SQLException e) {
                        System.out.println("LoginServlet: Error during Remember Me auto-login: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break; // only one rememberMe cookie
                }
            }
        }

        // Call ServletUtility to set allowSignUp and other attributes
        servletUtility.setCommonAttributes(request, getServletContext());

        // If signup was successful, show message
        String signupSuccess = request.getParameter("signupSuccess");
        if ("true".equals(signupSuccess)) {
            request.setAttribute("message", "Sign up successful! Please log in.");
        }

        // Forward to login.jsp
        request.getRequestDispatcher("login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        System.out.println("=== LoginServlet.doPost() called ===");

        String username = request.getParameter("userName");
        String password = request.getParameter("password");

        System.out.println("Attempting login for username: " + username);
        System.out.println("Password provided: " + (password != null && !password.isEmpty()));

        try {
            LoginResult result = sqlConnectorUserTable.isValidUser(username, password);
            System.out.println("Login result: " + result);

            if (result == LoginResult.SUCCESS) {
                User user = sqlConnectorUserTable.getUserByUsername(username);

                if (user != null) {
                    // Fetch roles
                    Map<String, Boolean> roles = sqlConnectorUserTable.getUserRoles(username);

                    // Create session - Tomcat handles cookie configuration automatically
                    HttpSession session = request.getSession(true);

                    // Add debug logging
                    System.out.println("AWS_REGION: " + System.getenv("AWS_REGION"));
                    System.out.println("EB_ENVIRONMENT_NAME: " + System.getenv("EB_ENVIRONMENT_NAME"));

                    // Configure session cookie for current environment
                    cookieConfigurationService.configureSessionCookie(request, response);

                    // Set session attributes
                    session.setAttribute("userName", user.getUsername());
                    session.setAttribute("userId", user.getIdUser());
                    session.setAttribute("isAdmin", roles.getOrDefault("isAdmin", false));
                    session.setAttribute("isCommish", roles.getOrDefault("isCommish", false));

                    // ---- Remember Me ----
                    // If the checkbox was ticked, mint a per-device token and drop a long-lived cookie.
                    String rememberMe = request.getParameter("rememberMe");
                    if ("on".equals(rememberMe) || "true".equals(rememberMe)) {
                        try {
                            String token = generateRememberMeToken();
                            tokenTable.insertToken(user.getIdUser(), token, REMEMBER_ME_DAYS);
                            setRememberMeCookie(response, token);
                            System.out.println("LoginServlet: Remember Me token created for " + user.getUsername());
                        } catch (SQLException e) {
                            System.out.println("LoginServlet: Error creating Remember Me token: " + e.getMessage());
                            e.printStackTrace();
                            // Non-fatal: login still succeeds without the cookie
                        }
                    }

                    // Redirect to home page
                    String returnTo = request.getParameter("returnTo");
                    if (returnTo != null && !returnTo.isBlank()) {
                        response.sendRedirect(returnTo);
                    } else {
                        response.sendRedirect("HomeServlet");
                    }
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
        } catch (Exception e) {
            System.out.println("Exception during login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Generate a cryptographically strong, URL-safe token
    private String generateRememberMeToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void setRememberMeCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("rememberMe", token);
        cookie.setMaxAge(REMEMBER_ME_COOKIE_MAX_AGE);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // served over HTTPS via Cloudflare
        response.addCookie(cookie);
    }

    private void clearRememberMeCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("rememberMe", "");
        cookie.setMaxAge(0); // delete
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}