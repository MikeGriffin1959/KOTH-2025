package controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import helpers.SqlConnectorRememberMeTokenTable;

@Controller
public class LogoutServlet {

    @Autowired
    private SqlConnectorRememberMeTokenTable tokenTable;

    @GetMapping("/LogoutServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("LogoutServlet.doGet() called");

        // 1. Find the rememberMe cookie, revoke its token in the DB, and kill the cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("rememberMe".equals(c.getName())) {
                    String token = c.getValue();
                    if (token != null && !token.isEmpty()) {
                        try {
                            tokenTable.deleteToken(token);
                            System.out.println("LogoutServlet: revoked remember-me token");
                        } catch (Exception e) {
                            System.out.println("LogoutServlet: failed to delete token: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Tell the browser to drop the cookie (attributes must match the original)
                    Cookie kill = new Cookie("rememberMe", "");
                    kill.setPath("/");
                    kill.setHttpOnly(true);
                    kill.setSecure(true);
                    kill.setMaxAge(0);
                    response.addCookie(kill);
                    break;
                }
            }
        }

        // 2. Kill the session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return "redirect:/LoginServlet";
    }
}