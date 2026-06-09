package controllers;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import helpers.SqlConnectorCommentaryTable;
import model.Commentary;
import services.ServletUtility;

/**
 * Serves the AI Commentary timeline page (Commentary feature). Read-only and
 * shared — any authenticated user can view it. Season-scoped (KOTH has no group
 * abstraction). Parallels GolferFest's CommentaryServlet.
 *
 * GET  /CommentaryServlet              → full page with nav
 * GET  /CommentaryServlet?popout=true  → compact pop-out (no nav)
 * POST /CommentaryServlet (refreshTimeline) → JSON for the auto-refresh timer
 */
@Controller
public class CommentaryServlet {

    private static final int TIMELINE_LIMIT = 50;

    @Autowired
    private SqlConnectorCommentaryTable commentaryTable;

    @Autowired
    private ServletUtility servletUtility;

    @Autowired
    private ServletContext servletContext;

    @GetMapping("/CommentaryServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("CommentaryServlet.doGet started");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            String original = request.getRequestURI()
                    + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String returnTo = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8);
            return "redirect:/LoginServlet?expired=1&returnTo=" + returnTo;
        }

        servletUtility.setCommonAttributes(request, servletContext);
        int season = resolveSeason(request);

        List<Commentary> timeline = commentaryTable.getRecentCommentary(season, TIMELINE_LIMIT);

        model.addAttribute("timeline", timeline);
        model.addAttribute("timelineCount", timeline.size());
        model.addAttribute("isPopout", "true".equals(request.getParameter("popout")));
        model.addAttribute("season", season);

        return "commentary";
    }

    @PostMapping("/CommentaryServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Session expired\"}");
            return null;
        }

        String action = request.getParameter("action");
        if ("refreshTimeline".equals(action)) {
            handleRefreshTimeline(request, response);
            return null;
        }
        return "redirect:/CommentaryServlet";
    }

    /** AJAX refresh — returns the timeline as JSON for the auto-refresh timer. */
    private void handleRefreshTimeline(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        servletUtility.setCommonAttributes(request, servletContext);
        int season = resolveSeason(request);
        List<Commentary> timeline = commentaryTable.getRecentCommentary(season, TIMELINE_LIMIT);

        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("success", true);
        root.put("count", timeline.size());
        ArrayNode entries = root.putArray("entries");

        for (Commentary c : timeline) {
            ObjectNode n = entries.addObject();
            n.put("id", c.getCommentaryId());
            n.put("streamType", c.getStreamType());
            n.put("eventType", c.getEventType());
            n.put("week", c.getWeek());
            n.put("snark", c.getSnarkLevel());
            n.put("time", c.getCreatedAt() != null ? fmt.format(c.getCreatedAt()) : "");
            n.put("body", c.getBody());
        }

        response.getWriter().write(mapper.writeValueAsString(root));
    }

    private int resolveSeason(HttpServletRequest request) {
        Object attr = request.getAttribute("season");
        if (attr != null) {
            try { return Integer.parseInt(attr.toString()); } catch (NumberFormatException ignore) {}
        }
        return java.time.Year.now().getValue();
    }
}
