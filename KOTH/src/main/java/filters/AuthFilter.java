package filters;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class AuthFilter implements Filter {

	private static final Set<String> PUBLIC_PATHS = Set.of(
			  "/", "/index.jsp",
			  "/LoginServlet", 
			  "/login.jsp",
			  "/SignUpServlet", 
			  "/InitiateUsernameRecoveryServlet", 
			  "/ResetPasswordServlet",
			  "/InitiatePasswordResetServlet", 
			  "/accessDenied.jsp",            
			  "/health", 
			  "/status",
			  "/styles.css",
//			  "/favicon.ico",
			  "/KOTH-Tab-Icon.png",
			  "/stadium.jpeg",
			  // Email verification link is clicked from an inbox, usually logged out
			  // (token itself is the credential — an unguessable one-time UUID)
			  "/VerifyEmailServlet",
			  // PWA assets: must load before/without a session (install + service worker)
			  "/manifest.webmanifest",
			  "/sw.js",
			  "/icon-192.png",
			  "/icon-512.png",
			  "/icon-180.png"
			);

  private static boolean isPublic(HttpServletRequest req) {
    String ctx  = req.getContextPath();
    String path = req.getRequestURI().substring(ctx.length());
    if (path.isEmpty()) path = "/";
    if (path.startsWith("/css/") || path.startsWith("/js/") ||
        path.startsWith("/img/") || path.startsWith("/assets/")) {
      return true;
    }
    return PUBLIC_PATHS.contains(path);
  }

  @Override public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    if (isPublic(req)) {
      chain.doFilter(request, response);
      return;
    }

    HttpSession session = req.getSession(false);
    boolean loggedIn = (session != null) && (session.getAttribute("userName") != null);

    if (!loggedIn) {
      String original = req.getRequestURI();
      String q = req.getQueryString();
      if (q != null && !q.isBlank()) original += "?" + q;
      String returnTo = URLEncoder.encode(original, StandardCharsets.UTF_8);

      if ("XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"))) {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setHeader("X-Login-Redirect", req.getContextPath() + "/LoginServlet?expired=1&returnTo=" + returnTo);
        return;
      }

      res.sendRedirect(req.getContextPath() + "/LoginServlet?expired=1&returnTo=" + returnTo);
      return;
    }

    chain.doFilter(request, response);
  }

  @Override public void destroy() {}
}
