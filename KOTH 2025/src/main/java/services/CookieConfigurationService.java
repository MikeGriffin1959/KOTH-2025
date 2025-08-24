package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import config.EnvironmentConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class CookieConfigurationService {
    
    @Autowired
    private EnvironmentConfig environmentConfig;
    
    /**
     * Ensures session cookies are configured correctly for the current environment
     */
    public void configureSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        
        // Only configure if this is a new session
        if (session.isNew()) {
            String sessionId = session.getId();
            boolean useSecure = environmentConfig.useSecureCookies();
            
            // Create a properly configured session cookie
            Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
            sessionCookie.setHttpOnly(true);
            sessionCookie.setSecure(useSecure);
            sessionCookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
            sessionCookie.setMaxAge(-1); // Session cookie (expires when browser closes)
            
            response.addCookie(sessionCookie);
            
            System.out.println("CookieConfigurationService: Configured session cookie - " +
                "Secure: " + useSecure + 
                ", Environment: " + environmentConfig.getEnvironmentName());
        }
    }
    
    /**
     * Creates a properly configured cookie for the current environment
     */
    public Cookie createSecureCookie(String name, String value, int maxAge, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(environmentConfig.useSecureCookies());
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        return cookie;
    }
}