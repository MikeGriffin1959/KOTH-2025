# KOTH Commentary — AI-Powered Survivor Pool Commentary

**Design doc · v0.3**
App: KOTH (NFL King of the Hill) · koth.bingmerfest.com
Stack: Java 21 / Spring Boot / JSP / MySQL / Tomcat 10.1 (shared 8080, dev 8081)

*v0.3 changes from v0.2: all four §11 verification items resolved (`period` is numeric, `displayClock` is `M:SS`, `@EnableScheduling` already present, multi-pool insurance baked into schemas); DB vs. display status mapping clarified in §7. Doc is now buildable end-to-end with no remaining blockers.*

*v0.2 changes from v0.1: schema and class names made concrete after codebase review; ELIMINATION tone recalibrated to sympathetic across all snark levels; LATE_DRAMA promoted to v1 with 30s polling during 2-minute warnings; $5/day cost cap; single-pool architecture reflected throughout (no group/multi-group abstraction).*

---

## 1. Goal

Add AI-generated commentary to KOTH that brings the survivor pool to life — a snarky, dossier-aware running narrative that fires at meaningful moments throughout each NFL week. Built on the same `CommentaryService` pattern proven in GolferFest, but re-shaped for survivor's binary, weekly, elimination-driven dynamics — and adapted to KOTH's single-pool reality (one pool per `season`, configured via `picksprice`).

The deliverable is a four-stream commentary system (Weekly Preview, Kickoff Reveal, live Event-driven, Week Recap) plus dossier infrastructure and a pop-out live feed, controlled by season-level commissioner toggles and a snark slider 0–10.

## 2. Strategic premise — why KOTH commentary differs from GF

GF commentary thrives on continuous live action: lead changes, fractional margin shifts, hourly path-to-victory updates across four rounds. The CommentaryService taxonomy there (`END_OF_ROUND`, `HOURLY_ANALYSIS`, event-driven race shifts, `POST_TOURNAMENT`, state transitions) reflects that continuous-flow reality.

KOTH is structurally different in three ways that shape the entire design:

1. **Weekly cadence, not continuous.** Sunday afternoon is the burst; the rest of the week is dead air. Commentary should be silent for ~120 hours, then erupt during the Thursday–Monday game window. This is a scheduler concern, not a content one.

2. **Binary outcomes, not standings.** Every alive user is equally alive — there's no "lead." The drama is *survival*, not relative position. So the event detector hunts for survival-related moments (a user's team trailing, an upset brewing, an elimination), not standings deltas.

3. **A tie is a loss** (existing KOTH rule, baked into `isWinningPick`). Failure modes are sharper than golf. An ELIMINATION is final — a user is out, their pool over. The snark calibration on that moment matters; KOTH ELIMINATION uses a sympathetic tone at every snark level, *not* the `[UserName] : Woof!` cadence GF uses for race collapses. That signature stays with GF.

These three facts mean we lift the architectural pattern from GF but rewrite the event taxonomy, the prompt structures, and the scheduling layer.

## 3. Architecture overview

The same shape as GF, adapted to KOTH:

- **`CommentaryService`** — prompt builders + Anthropic API client. One generation method per stream (preview / reveal / event / recap), all sharing the system-prompt builder and dossier-injection helpers. Persists output to the `commentary` table.
- **`CommentaryScheduler`** — *new* `@Scheduled` Spring component. KOTH currently has no background job (game/score refresh is request-driven via `CommonProcessingService.updateCache`); the scheduler is the first scheduled work in the app. Wakes every 60s by default, tightens to every 30s during 2-minute-warning windows of in-progress games. Each tick: refresh game state, detect events, dispatch to the CommentaryService.
- **`EventDetector`** — helper invoked by the scheduler. Reads the `game` table (using `period`, `displayClock`, `homeScore`, `awayScore`, `pointSpread`, `status`) and the `picks` table for the current week. Emits `RaceEvent` objects. Dedupes against the `commentary` table via the `idx_dedupe` index.
- **`UserDossierService` + `PoolDossierService`** — mirror GF's pattern, but `PoolDossier` (the group-dossier analog) is per-season since there's no group concept.
- **Pop-out feed** — `/commentary-live.jsp` (no group qualifier needed). Auto-refresh, filter toggle, snark badge.
- **Commissioner controls** — new "Commentary" card on `commissioner.jsp` alongside the existing New Season / Pick Prices cards. Settings persist to new columns on `picksprice`.

## 4. The four streams

### 4.1 Weekly Preview

**Fires:** Once per week, Friday morning by default (configurable via `previewDayOfWeek` on picksprice).
**Style:** Setting the stage. State of the field, betting landscape, dossier flavor. ~3–5 sentences.
**Inputs:** Roster of users with `remainingPicksLive > 0`, their picks for the upcoming week (or notation that picks are masked via `picksprice.maskPicks`), `pointSpread` and `overUnder` from Game table, dossier personalities, week number, playoff context if `week >= 19`.
**Example output (snark 6):**

> Eight survivors enter Week 10, and Vegas has four games at -7 or worse. Dave's coming off three weeks of picking road dogs by gut feel, so naturally he's the one to watch. The Bears-Vikings game looms — half this room needs Minnesota and the line is tightening.

**Masked-picks variant:** When `picksprice.maskPicks = 1`, the preview talks matchups and betting landscape only — no specific pick callouts. Dossier flavor still permitted ("Dave historically takes road dogs"). The unmask reveal moves to the Kickoff Reveal stream.

### 4.2 Kickoff Reveal

**Fires:** At each kickoff window when picks are masked — Thursday ~8:15 ET, Sunday 1pm ET, Sunday 4:05/4:25 ET, SNF, MNF. Only relevant when `picksprice.maskPicks = 1`; suppressed entirely otherwise.
**Style:** The reveal moment. ~2–3 sentences per window.
**Inputs:** `selectedTeam` values from `picks` for the games kicking off in this window, dossier flavor, the game(s) about to start.
**Example output (snark 7):**

> Picks are in for the 1pm window. Five took the Eagles — chalk safety. Dave, predictably, took the Jets. Dave, you are not well.

This stream is unique to KOTH; GF has no analog because golf picks are public from entry.

### 4.3 Event-driven (live)

**Fires:** During the game window, every scheduler tick (60s default, 30s during 2-min-warning windows). The reactive layer.
**Style:** 1–2 sentences per event, snark-scaled.

| Event | Trigger condition | Tone notes |
|---|---|---|
| TROUBLE | User's team trailing by 10+ in Q4 (`period = 4`, score diff ≥ 10 against picked team) | Full snark range. Still alive, still drama. |
| UPSET_ALERT | Underdog (per `pointSpread`) leading the favorite that users picked, after halftime (`period >= 3`) | Full snark range. |
| LATE_DRAMA | Tied or one-score game, `displayClock <= 2:00` in Q4 or OT, users' fate riding on it | Full snark range. Scheduler tightens to 30s during this window. |
| NARROW_SURVIVAL | User's team wins by ≤3 after trailing in Q3/Q4 or being tied late | Full snark range. Best moment for affectionate teasing about the user's pick. |
| ELIMINATION | User's team lost or tied (game `status = Final`/`F/OT`/`STATUS_FINAL`, score equal or against the picked team) | **Sympathetic at every snark level. No `Woof!` cadence.** See calibration below. |
| GAME_FINAL_WIN | User's team won cleanly (final score, picked team won, no LATE_DRAMA fired) | Lower priority. Optional at high snark only. |
| LAST_STAND | Multiple users alive heading into the final game of the week with diverging picks | Full snark range — the cliffhanger setup. |

**ELIMINATION calibration** (per your direction — full elimination is painful, stay gentle):

- *Snark 3:* "Tough one for Mike — the Cowboys couldn't pull it out. That ends his run for the year. Hard luck."
- *Snark 6:* "Mike's Cowboys came up short, and that's the season for him. Sting's gonna last a while, but there's always next year."
- *Snark 9:* "Mike, my friend — the Cowboys did Mike dirty. Season over, head held high. We'll see you at the draft party."

At every level the user is treated with affection. Teasing flavor scales up, but the underlying tone stays sympathetic. The system prompt enforces this with explicit "no Woof, no mockery on ELIMINATION" rules and the calibration examples above baked in.

**Event detection notes:**
- `game.period` is a **numeric string** (`"1"`, `"2"`, `"3"`, `"4"`, `"5"+` where 5+ indicates OT). Confirmed via `myScoreboard.jsp` which does `Integer.parseInt(period) > 4` and prepends `Q` for display. Halftime convention: `period = "2"` AND `displayClock = "0:00"`.
- `game.displayClock` is `M:SS` format (e.g., `"12:34"`, `"0:00"`). EventDetector parses to total seconds via `split(":")` for the LATE_DRAMA 2:00 threshold.
- `game.status` is the **raw ESPN value** in the database (`STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, `STATUS_HALFTIME`, `STATUS_END_PERIOD`, `STATUS_FINAL`). The friendly forms (`Final`, `F/OT`, `In Progress`, `Scheduled`) are produced at the controller layer by `MyScoreboardServlet.convertStatus()`. EventDetector reads the DB directly, so it must match against `STATUS_*` values — except for ELIMINATION-final-game detection, which should also accept legacy `Final` / `F/OT` values for backward compatibility (same forgiveness pattern as the existing `isWinningPick` in `CommonProcessingService`).
- Dedupe key is `(season, week, gameId, eventType, affectedUserIds)` — backed by `idx_dedupe`. Same game state never fires the same event twice.
- ELIMINATION is the only event that requires the game be final. All others fire during play.

### 4.4 Week Recap

**Fires:** Once, after the last game of the NFL week is `Final` (typically MNF).
**Style:** Looking back, setting up next week. ~4–6 sentences.
**Inputs:** Full week's outcomes from `game` joined with `picks`, who survived (`remainingPicksLive` per user), who was eliminated, dossier context.

**Special case — season recap.** If only one user remains with `remainingPicksLive > 0` (or zero in a wipeout), the recap becomes the season finale. At week 22 (Super Bowl) with one or more survivors, the recap *is* the season recap — there's no Week 23 to preview.

## 5. Database schema

All names verified against the actual KOTH schema.

### 5.1 Add columns to `picksprice` (commentary settings live here, alongside `maskPicks` and `allowSignUp`)

```sql
ALTER TABLE picksprice
    ADD COLUMN snarkLevel INT DEFAULT 5,
    ADD COLUMN commentaryEnabled TINYINT(1) DEFAULT 0,
    ADD COLUMN commentaryNotifications TINYINT(1) DEFAULT 0,
    ADD COLUMN previewDayOfWeek INT DEFAULT 5;  -- 5 = Friday (java.time.DayOfWeek convention)
```

`commentaryNotifications` is a placeholder — v2 wires actual SMS via `user.cellNumber` (already present on the user table; reuse the Telnyx integration pattern from GolferFest). v1 leaves the column wired through the UI but unused server-side.

### 5.2 New table: `commentary`

```sql
CREATE TABLE commentary (
    commentaryId    INT NOT NULL AUTO_INCREMENT,
    season          INT NOT NULL,
    kothSeason      VARCHAR(10) DEFAULT NULL,            -- multi-pool insurance: matches picksprice.kothSeason
    week            INT NOT NULL,
    streamType      VARCHAR(32) NOT NULL,                -- PREVIEW, REVEAL, EVENT, RECAP
    eventType       VARCHAR(32) DEFAULT NULL,            -- TROUBLE, ELIMINATION, etc. (NULL for non-event streams)
    affectedUserIds VARCHAR(255) DEFAULT NULL,           -- comma-separated idUser values
    gameId          INT DEFAULT NULL,                    -- references game.GameID
    snarkLevel      INT NOT NULL,
    promptTokens    INT DEFAULT NULL,
    responseTokens  INT DEFAULT NULL,
    body            TEXT NOT NULL,
    createdAt       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (commentaryId),
    INDEX idx_season_week (season, kothSeason, week),
    INDEX idx_dedupe (season, kothSeason, week, gameId, eventType),
    INDEX idx_costcap (createdAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`idx_costcap` supports the daily $5 cost ceiling — the cap-check query is a `SUM(promptTokens, responseTokens) WHERE createdAt >= today` so a single-column index on `createdAt` is sufficient.

In v1, `kothSeason` is populated from `picksprice.kothSeason` of the active config (e.g., `"KOTH"` or `"KOTH 2"`) but isn't a filter in any query — there's only one active pool. If KOTH ever runs parallel pools per season (e.g., `KOTH` + `KOTH 2` side by side), the queries flip to `WHERE season = ? AND kothSeason = ?` with no schema migration.

### 5.3 New table: `user_dossier`

```sql
CREATE TABLE user_dossier (
    dossierId     INT NOT NULL AUTO_INCREMENT,
    userId        INT NOT NULL,
    season        INT NOT NULL,
    kothSeason    VARCHAR(10) DEFAULT NULL,    -- multi-pool insurance
    displayName   VARCHAR(100) DEFAULT NULL,    -- override of user.username for commentary; defaults to firstName
    personality   TEXT,
    rivalries     TEXT,
    sensitivities TEXT,                          -- pull-punches guidance; load-bearing for ELIMINATION
    updatedAt     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dossierId),
    UNIQUE KEY uq_user_season (userId, season, kothSeason),
    CONSTRAINT fk_dossier_user FOREIGN KEY (userId) REFERENCES user (idUser)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

Per-season scoping means dossier flavor can evolve year over year ("Dave's coming off back-to-back ELIMINATION weeks 2 and 3 last year" is something the v2026 dossier can carry without contaminating v2027).

### 5.4 New table: `pool_dossier`

```sql
CREATE TABLE pool_dossier (
    season            INT NOT NULL,
    kothSeason        VARCHAR(10) NOT NULL DEFAULT '',    -- multi-pool insurance; empty string for single-pool v1
    poolIdentity      TEXT,
    poolHistory       TEXT,
    poolLore          TEXT,
    commissionerNotes TEXT,
    toneGuidance      TEXT,
    updatedAt         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (season, kothSeason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

Note: `pool_dossier.kothSeason` is `NOT NULL` (with empty-string default) because it's part of the composite PK. The other two tables use `DEFAULT NULL` since their uniqueness is enforced via a unique key, not PK. In v1, populate this column from `picksprice.kothSeason` (e.g., `"KOTH"`); the empty-string default is only a fallback if `picksprice.kothSeason` is itself NULL for some reason.

## 6. Prompt architecture

Three layers, mirroring GF:

**Layer 1 — System prompt.** Establishes commentator persona, snark calibration (with explicit examples at 0, 5, 10), output constraints (sentence count per stream), the tie-equals-loss rule, and the *ELIMINATION-stays-gentle* rule with the snark 3/6/9 calibration examples from §4.3 inlined. NFL/survivor-flavored.

**Layer 2 — Pool dossier.** Prepended once per call.

**Layer 3 — Stream-specific user prompt.** Built fresh per call by the stream's prompt builder:

- `buildPreviewPrompt` — alive roster (from `user` join with computed remaining picks), picks (or masked indicator), week's slate from `game`, `pointSpread`/`overUnder`, user dossier injection for all alive users
- `buildKickoffRevealPrompt` — newly-unmasked picks for the kickoff window, dossier injection, the games about to start
- `buildEventPrompt` — the `RaceEvent` (type, affected `idUser` list, gameId, current score, `period`, `displayClock`), dossier injection for affected users only
- `buildRecapPrompt` — full week outcomes, survival/elimination list, dossier injection

Output rules baked into the system prompt:
- Sentence count appropriate to stream (1–2 events, 2–3 reveal, 3–5 preview, 4–6 recap).
- Reference users by `user_dossier.displayName` (or `firstName` fallback). Never use raw `username`.
- A tie is a loss — never frame a tie as a hopeful draw.
- ELIMINATION uses sympathetic tone at every snark level. The `[UserName] : Woof!` cadence is GF's signature and is **forbidden** in KOTH ELIMINATION output. (TROUBLE and other still-alive events do not carry this restriction.)
- Don't speculate beyond the data provided.
- Respect `user_dossier.sensitivities` — pull punches where flagged, regardless of snark setting.

## 7. Trigger logic

The `CommentaryScheduler` is a new `@Scheduled` Spring component. Pseudocode for the main tick:

```
@Scheduled(fixedRateString = "${commentary.scheduler.tickMs:60000}")
public void tick() {
    PicksPrice cfg = currentSeasonConfig();
    if (cfg == null || !cfg.isCommentaryEnabled()) return;

    int season = cfg.getPicksPriceSeason();
    int week = currentWeek();

    if (dailyCostCapExceeded()) {
        logger.warning("Commentary daily $5 cap reached; suppressing this tick");
        return;
    }

    // 1. Preview (once per week, configurable day-of-week)
    if (isPreviewWindow(cfg)) {
        if (!alreadyGenerated(season, week, "PREVIEW", null, null)) {
            commentaryService.generatePreview(season, week);
        }
    }

    // 2. Kickoff reveal (only if masking on, fires per kickoff window)
    if (cfg.isMaskPicks() && isKickoffWindowJustOpened()) {
        if (!alreadyGenerated(season, week, "REVEAL", currentKickoffGameId(), null)) {
            commentaryService.generateKickoffReveal(season, week, currentKickoffWindow());
        }
    }

    // 3. Live events — only during the Thu–Mon game window
    if (isGameWindow()) {
        gameService.refreshLiveScores();          // populates game table
        List<RaceEvent> events = eventDetector.detect(season, week);
        for (RaceEvent ev : events) {
            if (!alreadyGenerated(season, week, "EVENT", ev.gameId, ev.eventType)) {
                commentaryService.generateEventCommentary(season, week, ev);
            }
        }
    }

    // 4. Week recap (once, after last game of week is Final)
    if (isLastGameOfWeekFinal(season, week)) {
        if (!alreadyGenerated(season, week, "RECAP", null, null)) {
            commentaryService.generateRecap(season, week);
        }
    }
}
```

**Cadence behavior:**

- Default `fixedRate` = 60s. Cheap when nothing is happening (date math + a small `game` query).
- Tighten to 30s during 2-min-warning windows. Implementation option: a second `@Scheduled` method at 30s that *only* runs if any in-progress game has `displayClock <= 2:00` in Q4 or OT, otherwise no-ops. Avoids reconfiguring the main tick rate.
- Outside the Thursday–Monday window, the tick mostly no-ops (a config read + a date check).

**Cost cap (`commentary.api.dailyCapUsd = 5.00`):**

- Each `CommentaryService` call records `promptTokens` and `responseTokens` to the `commentary` row.
- `dailyCostCapExceeded()` runs `SELECT SUM(promptTokens) AS p, SUM(responseTokens) AS r FROM commentary WHERE createdAt >= CURDATE()` and applies current Sonnet 4.6 pricing ($3/Mtok input, $15/Mtok output). If total ≥ $5.00, return true.
- Logs a warning and short-circuits the tick. Does not raise an exception. Auto-resets at midnight.
- Realistic daily spend during a Sunday slate: ~50 calls × ~$0.01–0.02 each ≈ well under $1. The cap is a circuit-breaker, not a budget.

## 8. UI

### 8.1 Commissioner controls

Add a new card to `commissioner.jsp`, alongside the existing "New Season" and "Pick Prices" cards. Single card titled **"Commentary"** containing:

- **Enable Commentary** checkbox (AJAX toggle, instant save via new `CommissionerServlet` action `toggleCommentary`)
- **Snark Level** slider 0–10 with colored badge (red at 10, green at 0). Saves on release via `setSnarkLevel` action.
- **Notifications** checkbox (placeholder, v2 SMS) — saves via `toggleCommentaryNotifications`
- **Preview Day** dropdown (Monday–Sunday) — saves via `setPreviewDayOfWeek`
- A "Fire Test Commentary" admin button (M1 only; can stay as a hidden admin tool after launch) — calls a new `testCommentary` action that runs a hardcoded prompt end-to-end and returns the generated body inline.
- Link to "Manage Dossiers" → `/dossier-admin.jsp`

The pattern mirrors the existing maskPicks/allowSignUp toggle setup (see `commissioner.jsp` lines around `maskPicksForm`). New columns are populated on the `pickPricesJson` meta tag exactly the way `maskPicks` already is.

### 8.2 Dossier management page (`/dossier-admin.jsp`)

Two sections, stacked:
- **Pool Dossier** (top, expandable card) — identity, history, lore, tone guidance, commissioner notes. One row per season.
- **User Dossiers** (below, one card per user with `picksSeason = currentSeason`) — displayName, personality, rivalries, sensitivities.

UX matches GF's `dossier.jsp`. Cards expand to edit, save on blur via AJAX.

### 8.3 Live commentary feed (`/commentary-live.jsp`)

Opens in a separate browser window via a "📡 Live Commentary" link in the main KOTH nav. Newest entry at top, timestamped (e.g., "1:18 PM ET — UPSET ALERT"). Auto-refreshes every 60s during game windows, every 5 min otherwise.

Filter toggle: All / Preview & Recap / Events Only / Reveals Only. Snark badge on each entry showing the level it was generated at. Single feed — no group qualifier in the URL.

## 9. External integrations

### 9.1 Anthropic

- Standard `/v1/messages` endpoint, same auth pattern as GF.
- Key in `application.properties`: `commentary.api.key=sk-ant-...`
- Model: `commentary.api.model=claude-sonnet-4-6` (current GF model as of doc date).
- Daily cap: `commentary.api.dailyCapUsd=5.00`
- Per-call token logging into `commentary.promptTokens` / `responseTokens` for the cap calculation.

### 9.2 ESPN game data

Existing integration via `ApiParsers` and `SqlConnectorGameTable`. EventDetector reads the `game` table — no new external API needed for v1. The scheduler triggers `gameService.refreshLiveScores()` (the existing ESPN poll path) inside the game window before running detection.

## 10. Milestones

Brier-style breakdown, scoped for incremental demos. v0.2 consolidates v0.1's seven milestones into six by absorbing LATE_DRAMA / extended events into M3.

### M1 — Foundation
- Schema migrations: ALTER `picksprice`, CREATE `commentary` table.
- `CommentaryService` skeleton: system prompt builder (with ELIMINATION calibration baked in), Anthropic client, persistence to `commentary` table.
- `dailyCostCapExceeded()` logic + `commentary.api.dailyCapUsd` property.
- New "Commentary" card on `commissioner.jsp` (enable + snark + notifications + preview day).
- New servlet actions: `toggleCommentary`, `setSnarkLevel`, `toggleCommentaryNotifications`, `setPreviewDayOfWeek`, `testCommentary`.
- Update `pickPricesJson` meta-tag plumbing to carry the new fields.

**Demo:** Toggle Enable, set snark to 7, click "Fire Test Commentary" → blurb appears in the `commentary` table and on a barebones admin readout. Cap-check returns 0 cents spent today.

### M2 — Week Recap stream
- `buildRecapPrompt` + `generateWeekRecap` method on CommentaryService.
- `CommentaryScheduler` skeleton with `@Scheduled` annotation (the new background-job class for the app).
- `isLastGameOfWeekFinal(season, week)` detector reading from `game`.
- Sole-survivor / week-22 season-recap special case.

**Demo:** End-to-end on a closed-out week — the recap fires automatically after the last MNF goes Final.

### M3 — Live Event-driven layer (full event set)
- `EventDetector` class. Implements all seven event types: TROUBLE, UPSET_ALERT, LATE_DRAMA, NARROW_SURVIVAL, ELIMINATION, GAME_FINAL_WIN, LAST_STAND.
- `RaceEvent` model.
- Dedupe via `commentary.idx_dedupe`.
- `buildEventPrompt` + `generateEventCommentary`.
- Scheduler runs a tighter 30s check when any in-progress game has `displayClock <= 2:00` in Q4 (`period = "4"`) or OT (`period >= "5"`) — for LATE_DRAMA fidelity.
- Read DB values directly: `game.status` matches `STATUS_*` forms; `game.period` is numeric string; `game.displayClock` is `M:SS`.

**Demo:** Real Sunday slate produces live event commentary. ELIMINATION lines arrive sympathetic-toned at every snark level.

### M4 — Weekly Preview + Kickoff Reveal
- `buildPreviewPrompt`, `buildKickoffRevealPrompt`, generation methods.
- Preview-day scheduling using `picksprice.previewDayOfWeek`.
- Masking-aware logic: preview suppresses pick details when `picksprice.maskPicks = 1`; reveal fires per kickoff window when masking is on.
- Kickoff-window detection (Thu 8:15, Sun 1:00 / 4:05 / 4:25, SNF, MNF).

**Demo:** Friday morning preview lands; with masking on, Sunday 1pm reveal fires when picks unmask.

### M5 — Dossiers
- `user_dossier` and `pool_dossier` schemas.
- `UserDossierService` + `PoolDossierService`.
- `/dossier-admin.jsp` management JSP.
- Wire dossier injection into all four prompt builders. `sensitivities` becomes load-bearing for ELIMINATION tone modulation.

**Demo:** Edit a dossier → the next event blurb references that personality. Add a `sensitivities` note → ELIMINATION wording softens accordingly.

### M6 — Pop-out live feed
- Polished `commentary-live.jsp` with auto-refresh, filter toggle, snark badge, pop-out nav icon in the main KOTH header.

**Demo:** Open the live feed in a side window during a Sunday slate, watch commentary stream in.

## 11. Open questions & verification items

All v0.2 verification items are resolved as of v0.3:

1. **`game.period` format — RESOLVED.** Confirmed numeric string (`"1"`/`"2"`/`"3"`/`"4"`/`"5"+`) via `myScoreboard.jsp`'s `Integer.parseInt(period) > 4` check. Halftime = `period = "2"` AND `displayClock = "0:00"`. Documented in §4.3.

2. **`game.displayClock` format — RESOLVED.** `M:SS` format, confirmed via JSP's direct `"0:00"` comparison. EventDetector parses via `split(":")`. Documented in §4.3.

3. **`@EnableScheduling` already present — RESOLVED.** Confirmed in `config/AppConfig.java`. The `CommentaryScheduler` `@Scheduled` bean drops in with zero config work.

4. **Multi-pool insurance — RESOLVED.** All three new tables now carry `kothSeason` (matching `picksprice.kothSeason`). In v1 the column is populated but not filtered on; queries flip to `WHERE ... AND kothSeason = ?` when (if ever) parallel pools launch, requiring no schema migration. Documented in §5.

**Cross-cutting note worth carrying into implementation:** the codebase has two parallel status vocabularies — raw ESPN values in the `game.status` DB column (`STATUS_FINAL`, `STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, `STATUS_HALFTIME`, `STATUS_END_PERIOD`) and friendly forms produced at the controller layer (`Final`, `F/OT`, `In Progress`, `Scheduled`). `isWinningPick` in `CommonProcessingService` already handles both, suggesting older rows may carry the friendly form. `EventDetector` reads the DB directly and should match `STATUS_*` for live-game events, while also accepting `Final` / `F/OT` for ELIMINATION on backward-compatibility grounds. Same forgiveness pattern as the existing helper.

---

*End of v0.3. All schema, format, and infrastructure items verified. Ready to implement M1.*
