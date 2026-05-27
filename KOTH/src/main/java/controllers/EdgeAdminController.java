package controllers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.ServletContext;
import model.EdgeCandidate;
import model.Game;
import services.EdgeCandidateService;
import services.EdgeCandidateService.Allocation;
import services.NFLSeasonCalculator;

/**
 * KOTH Edge advisor — review page (M2/M3) + apply-picks write-through (M4).
 * Commish-only. GET shows the ranked candidate list (optionally with Claude triage).
 * POST applies the selected picks through the existing pick-submission service,
 * inheriting masking and all current semantics.
 */
@Controller
public class EdgeAdminController {

    @Autowired private EdgeCandidateService edgeCandidateService;
    @Autowired private NFLSeasonCalculator seasonCalculator;
    @Autowired private helpers.SqlConnectorPicksTable picksTable;
    @Autowired private helpers.SqlConnectorGameTable gameTable;
    @Autowired private helpers.SqlConnectorEdgeTable edgeTable;
    @Autowired private services.EdgeOrchestrator edgeOrchestrator;

    /** Snapshots older than this many minutes trigger an auto-rebuild on page load. */
    private static final int STALE_AFTER_MIN = 30;

    @GetMapping("/edge")
    public String viewEdge(HttpServletRequest request, Model model,
                           @RequestParam(required = false) Integer season,
                           @RequestParam(required = false) Integer week,
                           @RequestParam(required = false) Integer lives,
                           @RequestParam(required = false) String alloc,
                           @RequestParam(required = false, defaultValue = "false") boolean triage,
                           @RequestParam(required = false, defaultValue = "false") boolean refresh) {

        // ── admin gate — Edge advisor is restricted to admin users ──
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            return "redirect:/LoginServlet";
        }
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            return "redirect:/HomeServlet";
        }

        int s = (season != null) ? season : seasonCalculator.getCurrentNFLSeason();
        int w = (week   != null) ? week   : seasonCalculator.getCurrentNFLWeekNumber();

        // ── auto-build snapshots if missing or stale ──
        // Stale = older than STALE_AFTER_MIN, so a near-kickoff refresh pulls fresh lines.
        // Manual refresh (?refresh=true) always rebuilds.
        java.sql.Timestamp newest = edgeTable.getNewestSnapshotAt(s, w);
        boolean needsBuild = refresh || newest == null ||
                (System.currentTimeMillis() - newest.getTime()) > STALE_AFTER_MIN * 60_000L;
        if (needsBuild) {
            String why = refresh ? "manual refresh"
                       : newest == null ? "no snapshots"
                       : "stale (" + ((System.currentTimeMillis() - newest.getTime()) / 60_000L) + "min old)";
            System.out.println("DEBUG[edge]: auto-build triggered — " + why);
            try {
                edgeOrchestrator.runWeeklyEdge(s, w);
                newest = edgeTable.getNewestSnapshotAt(s, w);
            } catch (Exception e) {
                System.err.println("DEBUG[edge]: auto-build failed: " + e.getMessage());
                model.addAttribute("buildError", "Auto-build failed: " + e.getMessage());
            }
        }

        // remaining lives: default to the commish's own remaining picks for this week,
        // overridable via the ?lives= param. Pull from the same context map the app uses.
        int remaining = resolveRemainingLives(request, session, lives);

        Allocation allocation = "STACK".equalsIgnoreCase(alloc) ? Allocation.STACK : Allocation.SPREAD;

        List<EdgeCandidate> candidates =
                edgeCandidateService.buildRankedCandidates(s, w, remaining, allocation);

        // Claude triage only when explicitly requested (?triage=true), so routine
        // page refreshes don't spend API calls. Verdicts merge onto the candidates.
        if (triage) {
            System.out.println("DEBUG[edge]: triage param=true, configured=" + edgeCandidateService.isTriageConfigured());
            edgeCandidateService.applyTriage(candidates, s, w);
        }

        // persist the ranked recommendations for this week (so the recs table stays current)
        edgeCandidateService.persistRecommendations(s, w, candidates);

        model.addAttribute("candidates", candidates);
        model.addAttribute("season", s);
        model.addAttribute("week", w);
        model.addAttribute("lives", remaining);
        model.addAttribute("alloc", allocation.name());
        model.addAttribute("triage", triage);
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("lastBuiltAt", newest == null ? null : new java.util.Date(newest.getTime()));
        // pass through any apply banner from a recent redirect
        model.addAttribute("applyMessage", request.getParameter("applyMessage"));
        model.addAttribute("applyError", request.getParameter("applyError"));

        return "edge";
    }

    // ════════════════════════════════════════════════════════════
    // M4 — APPLY PICKS
    // ════════════════════════════════════════════════════════════

    /**
     * Applies the selected picks for the commish (yourself) for this week.
     * Form payload (from edge.jsp):
     *   season, week              — the target week
     *   pick                      — repeated checkbox values, each "gameId|teamAbbrev"
     *   confirmedVetoes           — repeated "gameId|teamAbbrev" of checked picks
     *                               that Claude flagged recommend=false; required to
     *                               apply a vetoed pick (server-side enforcement of
     *                               the JS confirm).
     *
     * Guardrails (all rejections bounce back to the page with a banner):
     *   - login + commish required
     *   - kickoff guardrail: reject any pick whose game has already started
     *   - lives cap: total checked must not exceed your remaining lives
     *   - veto enforcement: any vetoed-and-checked pick MUST appear in confirmedVetoes
     *
     * Write path: routes through SqlConnectorPicksTable.updateUserPicks — same
     * method MakePicks uses — which DELETE-then-INSERTs the entire week. The UI
     * warns about that overwrite explicitly before submission.
     */
    @PostMapping("/edge/apply")
    public String applyPicks(HttpServletRequest request,
                             @RequestParam Integer season,
                             @RequestParam Integer week,
                             @RequestParam(required = false) String[] pick,
                             @RequestParam(required = false) String[] confirmedVetoes) {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            return "redirect:/LoginServlet";
        }
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            return "redirect:/HomeServlet";
        }

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return redirectBack(season, week, null, "Session missing userId.");
        }

        List<String[]> picks = parsePicks(pick);                  // [gameId, teamAbbrev]
        if (picks.isEmpty()) {
            return redirectBack(season, week, null, "No picks selected.");
        }

        // ── lives cap ──
        int remaining = resolveRemainingLives(request, session, null);
        if (picks.size() > remaining) {
            return redirectBack(season, week, null,
                    "Selected " + picks.size() + " picks but only " + remaining + " lives remaining.");
        }

        // ── kickoff guardrail ──
        Map<Long, Game> gamesById = indexGames(gameTable.getGamesForWeek(season, week));
        long nowUtcSec = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
        List<String> tooLate = new ArrayList<>();
        for (String[] p : picks) {
            long gid = parseLong(p[0]);
            Game g = gamesById.get(gid);
            if (g == null) {
                return redirectBack(season, week, null, "Unknown game in selection: " + p[0]);
            }
            Long kickoffSec = parseKickoffEpochUtc(g.getDate());
            if (kickoffSec != null && nowUtcSec >= kickoffSec) {
                tooLate.add(p[1] + " (game already started)");
            }
        }
        if (!tooLate.isEmpty()) {
            return redirectBack(season, week, null,
                    "Cannot apply — kickoff passed for: " + String.join(", ", tooLate));
        }

        // ── veto enforcement ──
        // If a checked pick is vetoed (Claude recommend=false), it must be present
        // in confirmedVetoes. We compute the veto set fresh by re-running the build,
        // matching what the user saw on the page they came from.
        java.util.Set<String> confirmed = new java.util.HashSet<>();
        if (confirmedVetoes != null) {
            for (String c : confirmedVetoes) if (c != null && !c.isEmpty()) confirmed.add(c);
        }
        java.util.Set<String> vetoedKeys = vetoedPickKeys(season, week, remaining);
        List<String> unconfirmedVetoes = new ArrayList<>();
        for (String[] p : picks) {
            String key = p[0] + "|" + p[1];
            if (vetoedKeys.contains(key) && !confirmed.contains(key)) {
                unconfirmedVetoes.add(p[1]);
            }
        }
        if (!unconfirmedVetoes.isEmpty()) {
            return redirectBack(season, week, null,
                    "Claude vetoed these picks — confirm to apply: " + String.join(", ", unconfirmedVetoes));
        }

        // ── write through the existing pick-submission service ──
        Map<String, List<String>> writeMap = new HashMap<>();
        for (String[] p : picks) {
            writeMap.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
        }
        try {
            picksTable.updateUserPicks(userId, season, week, writeMap);
            for (Map.Entry<String, List<String>> e : writeMap.entrySet()) {
                System.out.println("DEBUG[edge-apply]: gameId=" + e.getKey() + " teams=" + e.getValue());
            }
        } catch (Exception ex) {
            System.err.println("DEBUG[edge-apply]: write failed: " + ex.getMessage());
            ex.printStackTrace();
            return redirectBack(season, week, null, "Write failed: " + ex.getMessage());
        }

        String msg = "Applied " + picks.size() + " pick" + (picks.size() == 1 ? "" : "s") +
                     " for week " + week + ".";
        return redirectBack(season, week, msg, null);
    }

    /** Rebuild candidates with triage on, return the set of "gameId|team" keys Claude vetoed. */
    private java.util.Set<String> vetoedPickKeys(int season, int week, int lives) {
        java.util.Set<String> v = new java.util.HashSet<>();
        try {
            List<EdgeCandidate> ranked = edgeCandidateService.buildRankedCandidates(
                    season, week, lives, Allocation.SPREAD);
            edgeCandidateService.applyTriage(ranked, season, week);
            for (EdgeCandidate c : ranked) {
                if (Boolean.FALSE.equals(c.getClaudeRecommend())) {
                    v.add(c.getEspnEventId() + "|" + c.getTeamName());
                }
            }
        } catch (Exception e) {
            System.err.println("DEBUG[edge-apply]: veto-key rebuild failed: " + e.getMessage());
        }
        return v;
    }

    private List<String[]> parsePicks(String[] pick) {
        List<String[]> out = new ArrayList<>();
        if (pick == null) return out;
        for (String p : pick) {
            if (p == null) continue;
            int bar = p.indexOf('|');
            if (bar <= 0 || bar == p.length() - 1) continue;
            out.add(new String[]{ p.substring(0, bar), p.substring(bar + 1) });
        }
        return out;
    }

    private Map<Long, Game> indexGames(List<Game> games) {
        Map<Long, Game> m = new HashMap<>();
        if (games != null) for (Game g : games) m.put(g.getGameID(), g);
        return m;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    /**
     * Parse a Game.date string to a UTC epoch second. The field is messy in your DB
     * (ESPN ISO with Z, Eastern-converted ISO with offset, sometimes plain
     * 'yyyy-MM-dd HH:mm:ss'), so try a few patterns and fail to null.
     */
    private Long parseKickoffEpochUtc(String s) {
        if (s == null || s.isEmpty()) return null;
        // strip a trailing Z (ISO UTC)
        String t = s;
        try {
            if (t.endsWith("Z")) {
                LocalDateTime ldt = LocalDateTime.parse(t.substring(0, t.length() - 1),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.toEpochSecond(ZoneOffset.UTC);
            }
            // ISO with explicit offset, e.g. "2025-09-04T20:15-04:00[America/New_York]"
            int bracket = t.indexOf('[');
            if (bracket > 0) t = t.substring(0, bracket);
            if (t.contains("T") && (t.contains("+") || t.lastIndexOf('-') > 10)) {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(t);
                return odt.toEpochSecond();
            }
            // plain SQL datetime — assume UTC
            String sql = t.replace("T", " ");
            if (sql.length() == 16) sql = sql + ":00";
            LocalDateTime ldt = LocalDateTime.parse(sql,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return ldt.toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String redirectBack(int season, int week, String message, String error) {
        StringBuilder sb = new StringBuilder("redirect:/edge?season=")
                .append(season).append("&week=").append(week).append("&triage=true");
        if (message != null) sb.append("&applyMessage=").append(urlEnc(message));
        if (error != null)   sb.append("&applyError=").append(urlEnc(error));
        return sb.toString();
    }

    private String urlEnc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
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