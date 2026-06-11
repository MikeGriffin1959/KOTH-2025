package services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import helpers.SqlConnectorCommentaryTable;
import helpers.SqlConnectorGameTable;
import helpers.SqlConnectorPicksPriceTable;
import model.PicksPrice;

import java.util.List;

/**
 * Commentary scheduler (M2) — the first @Scheduled background job in KOTH
 * (@EnableScheduling already lives in config/AppConfig). Wakes every
 * commentary.scheduler.tickMs (default 60s) and, when the week's last game
 * goes final, fires the Week Recap exactly once.
 *
 * Designed so an idle tick is cheap: one picksprice read, one game-table
 * count. M3 adds live-event detection to this tick; M4 adds preview/reveal.
 */
@Service
public class CommentaryScheduler {

    @Autowired
    private SqlConnectorPicksPriceTable picksPriceTable;

    @Autowired
    private SqlConnectorGameTable gameTable;

    @Autowired
    private SqlConnectorCommentaryTable commentaryTable;

    @Autowired
    private CommentaryService commentaryService;

    @Autowired
    private NFLSeasonCalculator nflSeasonCalculator;

    @Scheduled(fixedRateString = "${commentary.scheduler.tickMs:60000}")
    public void tick() {
        try {
            int season = nflSeasonCalculator.getCurrentNFLSeason();

            // Guard 1: commentary must be enabled for the season (cheap read)
            List<PicksPrice> prices = picksPriceTable.getPickPrices(season);
            if (prices.isEmpty() || !prices.get(0).isCommentaryEnabled()) {
                return;
            }

            int currentWeek = nflSeasonCalculator.getCurrentNFLWeekNumber();
            if (currentWeek < 1) {
                return; // offseason / pre-week-1
            }

            // Week Recap: once, after the last game of the week is final.
            // Also look at the previous week — if the calculator rolls to the
            // next week right after MNF, the just-finished week still gets its
            // recap (hasCommentary makes this idempotent).
            for (int week = Math.max(1, currentWeek - 1); week <= currentWeek; week++) {
                if (!gameTable.isWeekComplete(season, week)) {
                    continue;
                }
                if (commentaryTable.hasCommentary(season, week, "RECAP")) {
                    continue; // already generated
                }
                System.out.println("CommentaryScheduler.tick - week " + week + " is complete, generating recap");
                boolean generated = commentaryService.generateWeekRecap(season, week);
                System.out.println("CommentaryScheduler.tick - recap " + (generated ? "generated" : "skipped/failed")
                        + " for season " + season + " week " + week);
            }

        } catch (Exception e) {
            // Never let one bad tick kill the schedule
            System.err.println("CommentaryScheduler.tick - error (will retry next tick): " + e.getMessage());
            e.printStackTrace();
        }
    }
}
