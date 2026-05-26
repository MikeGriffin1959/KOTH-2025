package controllers;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.ServletContext;
import model.EdgeCandidate;
import services.EdgeCandidateService;
import services.EdgeCandidateService.Allocation;
import services.NFLSeasonCalculator;

/**
 * Read-only review page for the KOTH Edge advisor (M2).
 * Commish-only. Builds the ranked candidate list for a week and forwards to edge.jsp.
 * Apply Picks is M4 — this page only displays.
 */
@Controller
public class EdgeAdminController {

    @Autowired private EdgeCandidateService edgeCandidateService;
    @Autowired private NFLSeasonCalculator seasonCalculator;

    @GetMapping("/admin/edge")
    public String viewEdge(HttpServletRequest request, Model model,
                           @RequestParam(required = false) Integer season,
                           @RequestParam(required = false) Integer week,
                           @RequestParam(required = false) Integer lives,
                           @RequestParam(required = false) String alloc) {

        // ── commish gate (same pattern as the commissioner pages) ──
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            return "redirect:/LoginServlet";
        }
        Boolean isCommish = (Boolean) session.getAttribute("isCommish");
        if (!Boolean.TRUE.equals(isCommish)) {
            return "redirect:/HomeServlet";
        }

        int s = (season != null) ? season : seasonCalculator.getCurrentNFLSeason();
        int w = (week   != null) ? week   : seasonCalculator.getCurrentNFLWeekNumber();

        // remaining lives: default to the commish's own remaining picks for this week,
        // overridable via the ?lives= param. Pull from the same context map the app uses.
        int remaining = resolveRemainingLives(request, session, lives);

        Allocation allocation = "STACK".equalsIgnoreCase(alloc) ? Allocation.STACK : Allocation.SPREAD;

        List<EdgeCandidate> candidates =
                edgeCandidateService.buildRankedCandidates(s, w, remaining, allocation);

        // persist the ranked recommendations for this week (so the recs table stays current)
        edgeCandidateService.persistRecommendations(s, w, candidates);

        model.addAttribute("candidates", candidates);
        model.addAttribute("season", s);
        model.addAttribute("week", w);
        model.addAttribute("lives", remaining);
        model.addAttribute("alloc", allocation.name());
        model.addAttribute("userName", session.getAttribute("userName"));

        return "edge";
    }

    @SuppressWarnings("unchecked")
    private int resolveRemainingLives(HttpServletRequest request, HttpSession session, Integer livesParam) {
        if (livesParam != null && livesParam >= 0) return livesParam;

        String userName = (String) session.getAttribute("userName");
        ServletContext ctx = request.getServletContext();

        // prefer the app-scope prior-week map the picks flow maintains
        Object m = ctx.getAttribute("userRemainingPicksPriorWeek");
        if (m == null) m = session.getAttribute("userRemainingPicks");
        if (m instanceof Map) {
            Map<String, Integer> map = (Map<String, Integer>) m;
            Integer r = map.get(userName);
            if (r != null) return r;
        }
        return 1; // safe fallback: recommend a single pick
    }
}