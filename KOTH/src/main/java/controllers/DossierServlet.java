package controllers;

import helpers.SqlConnectorDossierTable;
import helpers.SqlConnectorPicksPriceTable;
import model.PicksPrice;
import model.PoolDossier;
import model.UserDossier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import services.ServletUtility;

import java.io.IOException;
import java.util.List;

/**
 * Dossier management (Commentary M5) — commissioner-only. Pool dossier card +
 * one card per season user; AJAX saves. Design §8.2.
 */
@Controller
public class DossierServlet {

    @Autowired
    private SqlConnectorDossierTable dossierTable;

    @Autowired
    private SqlConnectorPicksPriceTable picksPriceTable;

    @Autowired
    private ServletUtility servletUtility;

    @Autowired
    private ServletContext servletContext;

    private boolean isCommish(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("userName") != null
                && Boolean.TRUE.equals(s.getAttribute("isCommish"));
    }

    @GetMapping("/DossierServlet")
    public String doGet(HttpServletRequest request, HttpServletResponse response, Model model)
            throws ServletException, IOException {
        System.out.println("DossierServlet.doGet called");
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            return "redirect:/LoginServlet";
        }
        if (!isCommish(request)) {
            return "redirect:/accessDenied.jsp";
        }

        servletUtility.setCommonAttributes(request, servletContext);
        int season = resolveSeason(request);

        PoolDossier pool = dossierTable.getPoolDossier(season);
        if (pool == null) {
            pool = new PoolDossier();
            pool.setSeason(season);
        }
        List<UserDossier> users = dossierTable.getUserDossiersForSeason(season);

        model.addAttribute("poolDossier", pool);
        model.addAttribute("userDossiers", users);
        model.addAttribute("season", season);
        return "dossier-admin";
    }

    @PostMapping("/DossierServlet")
    public String doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        if (!isCommish(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Commissioner access required\"}");
            return null;
        }

        servletUtility.setCommonAttributes(request, servletContext);
        int season = resolveSeason(request);
        String kothSeason = resolveKothSeason(season);
        String action = request.getParameter("action");

        try {
            if ("savePoolDossier".equals(action)) {
                boolean ok = dossierTable.upsertPoolDossier(season, kothSeason,
                        request.getParameter("poolIdentity"),
                        request.getParameter("poolHistory"),
                        request.getParameter("poolLore"),
                        request.getParameter("commissionerNotes"),
                        request.getParameter("toneGuidance"));
                writeJson(response, ok, ok ? "Pool dossier saved" : "Save failed");
                return null;
            }
            if ("saveUserDossier".equals(action)) {
                int userId = Integer.parseInt(request.getParameter("userId"));
                boolean ok = dossierTable.upsertUserDossier(userId, season, kothSeason,
                        request.getParameter("displayName"),
                        request.getParameter("personality"),
                        request.getParameter("rivalries"),
                        request.getParameter("sensitivities"));
                writeJson(response, ok, ok ? "Dossier saved" : "Save failed");
                return null;
            }
            writeJson(response, false, "Unknown action");
        } catch (Exception e) {
            System.err.println("DossierServlet.doPost - Error: " + e.getMessage());
            writeJson(response, false, "Error: " + e.getMessage().replace("\"", "'"));
        }
        return null;
    }

    private int resolveSeason(HttpServletRequest request) {
        Object attr = request.getAttribute("season");
        if (attr != null) {
            try { return Integer.parseInt(attr.toString()); } catch (NumberFormatException ignore) {}
        }
        return java.time.Year.now().getValue();
    }

    private String resolveKothSeason(int season) {
        List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
        return prices.isEmpty() ? null : prices.get(0).getKothSeason();
    }

    private void writeJson(HttpServletResponse response, boolean success, String message) throws IOException {
        response.getWriter().write(String.format("{\"success\": %b, \"message\": \"%s\"}", success, message));
    }
}
