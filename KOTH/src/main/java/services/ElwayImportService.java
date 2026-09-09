package services;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import helpers.SqlConnectorEdgeTable;
import helpers.SqlConnectorElwayTable;
import model.ElwayProjection;
import model.TeamContext;

/**
 * Parses text copied from Silver Bulletin's "ELWAY future game projections"
 * table and stores it as KOTH.ElwayProjection rows.
 *
 * The table's columns are:
 *   Wk | Home | Avg pts | Win prob | Away | Avg pts | Win prob | Home spread | Total
 * and a neutral-site game carries an "N" marker after the week number.
 * Copy/paste yields one game per line with tab or space separators, e.g.
 *   1      SEA   24.4   69.9%   NE    18.0   29.6%   -6     42
 *   1 N    LAR   25.5   57.6%   SF    22.8   41.8%   -3     48
 *
 * Parsing is token-based and tolerant: the week is the first integer, the
 * two teams are the first two tokens that match a KOTH team abbreviation, the
 * home win prob is the first percentage, the spread is the first signed number
 * after the second percentage, and the total is the number after the spread.
 * Header/footnote lines (no '%') are skipped. Multiple weeks may be pasted at once.
 */
@Service
public class ElwayImportService {

    @Autowired private SqlConnectorEdgeTable edgeTable;
    @Autowired private SqlConnectorElwayTable elwayTable;

    /** Elway abbreviations that differ from KOTH's apiTeamShortName. */
    private static final Map<String, String> ALIASES = Map.of(
        "WAS", "WSH", "JAC", "JAX", "LA", "LAR", "STL", "LAR", "OAK", "LV", "SD", "LAC");

    private static final Pattern INT     = Pattern.compile("^\\d{1,2}$");
    private static final Pattern PCT     = Pattern.compile("^\\d{1,3}(?:\\.\\d+)?%$");
    private static final Pattern SIGNED  = Pattern.compile("^[+\\-]\\d+(?:\\.\\d+)?$");
    private static final Pattern NUM     = Pattern.compile("^\\d+(?:\\.\\d+)?$");

    public static class ImportReport {
        public int parsed;
        public int saved;
        public final SortedSet<Integer> weeks = new TreeSet<>();
        public final List<String> skipped = new ArrayList<>();
    }

    /** Parse + persist. */
    public ImportReport importText(String text, int season) {
        ImportReport rep = new ImportReport();
        List<ElwayProjection> rows = parse(text, season, abbrevToTeamId(), rep);
        rep.parsed = rows.size();
        rep.saved = elwayTable.upsertProjections(rows);
        System.out.println("ElwayImportService.importText: season " + season + " parsed=" + rep.parsed
                + " saved=" + rep.saved + " weeks=" + rep.weeks + " skipped=" + rep.skipped.size());
        return rep;
    }

    /** KOTH abbreviation (upper-case) → apiTeamID, plus Elway aliases. */
    public Map<String, Integer> abbrevToTeamId() {
        Map<String, Integer> map = new HashMap<>();
        for (TeamContext tc : edgeTable.getTeamContexts().values()) {
            if (tc.getShortName() != null) map.put(tc.getShortName().toUpperCase(), tc.getTeamId());
        }
        for (Map.Entry<String, String> a : ALIASES.entrySet()) {
            Integer id = map.get(a.getValue());
            if (id != null) map.putIfAbsent(a.getKey(), id);
        }
        return map;
    }

    /** Pure parser (static so it can be exercised without Spring). */
    public static List<ElwayProjection> parse(String text, int season,
                                              Map<String, Integer> abbrevToId, ImportReport rep) {
        List<ElwayProjection> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.replace('−', '-').replace(' ', ' ').trim();
            if (line.isEmpty() || !line.contains("%")) continue;   // header, notes, blank
            String[] tokens = line.split("[\\s]+");

            Integer week = null;
            boolean neutral = false;
            List<int[]> teams = new ArrayList<>();     // [teamId, tokenIndex]
            List<Integer> pctIdx = new ArrayList<>();
            List<Double> pcts = new ArrayList<>();

            for (int i = 0; i < tokens.length; i++) {
                String t = tokens[i];
                if (week == null && INT.matcher(t).matches()) { week = Integer.parseInt(t); continue; }
                if (teams.isEmpty() && week != null && t.equalsIgnoreCase("N")) { neutral = true; continue; }
                String key = t.toUpperCase().replaceAll("[^A-Z]", "");
                if (teams.size() < 2 && !key.isEmpty() && abbrevToId.containsKey(key)) {
                    teams.add(new int[]{ abbrevToId.get(key), i });
                    continue;
                }
                if (PCT.matcher(t).matches()) {
                    pcts.add(Double.parseDouble(t.substring(0, t.length() - 1)) / 100.0);
                    pctIdx.add(i);
                }
            }

            if (week == null || teams.size() < 2 || pcts.isEmpty()) {
                if (rep != null) rep.skipped.add(line);
                continue;
            }

            // spread: first signed token (or "0"/"PK") after the last percentage; total: next number
            Double spread = null, total = null;
            int from = pctIdx.get(pctIdx.size() - 1) + 1;
            for (int i = from; i < tokens.length; i++) {
                String t = tokens[i];
                if (spread == null) {
                    if (SIGNED.matcher(t).matches()) { spread = Double.parseDouble(t.replace("+", "")); continue; }
                    if (t.equals("0") || t.equalsIgnoreCase("PK") || t.equalsIgnoreCase("EVEN")) { spread = 0.0; continue; }
                } else if (NUM.matcher(t).matches()) {
                    total = Double.parseDouble(t);
                    break;
                }
            }

            ElwayProjection p = new ElwayProjection();
            p.setSeason(season);
            p.setWeek(week);
            p.setHomeTeamId(teams.get(0)[0]);
            p.setAwayTeamId(teams.get(1)[0]);
            p.setHomeWinProb(Math.max(0.0, Math.min(1.0, pcts.get(0))));
            p.setHomeSpread(spread);
            p.setTotal(total);
            p.setNeutralSite(neutral);
            out.add(p);
            if (rep != null) rep.weeks.add(week);
        }
        return out;
    }
}
