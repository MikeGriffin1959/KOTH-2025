# CLAUDE.md — KOTH Project Context

This file is read at the start of every Claude Code session in this repo. It describes the project, its conventions, and the gotchas worth knowing up front. Update it when the architecture changes meaningfully.

## What this is

KOTH = "King of the Hill" — an NFL survivor pool app run by Griff (Michael Griffin) for a private group of family and friends. Originally on AWS, migrated to a home Threadripper server with dual-ISP Cloudflare load balancing. Live at koth.bingmerfest.com.

Survivor pool rules: each season, users buy 1–5 "picks." Each week, each user picks one NFL team to win. If the team wins, the pick survives; if it loses **or ties (ties count as losses)**, the pick is gone. Last survivor(s) win the pot. Pool plays through the NFL regular season and playoffs (internal weeks 1–22, where 19–22 are Wild Card → Super Bowl).

## Tech stack

- **Java 21** / **Spring Boot 3** / **JSP**
- **MySQL 9.5** (tables include `user`, `picks`, `game`, `teams`, `picksprice`)
- **Apache Tomcat 10.1** at `C:\Program Files\Apache Software Foundation\Tomcat 10.1\` — shared with other Bingmerfest apps; KOTH lives at context path `/KOTH`
- **Eclipse** for development; project at `C:\dev\koth\KOTH\` (Git root at `C:\dev\koth\`)
- **Maven** dependency management; WAR packaging
- **Bootstrap 4.5.2** + jQuery for frontend (no React)
- **ESPN APIs** for game data via `ApiFetchers` + `ApiParsers`
- **Anthropic Claude API** (in active development for commentary feature — see `KOTH-Commentary-Design.md`)

## Repo layout

- `src/main/java/controllers/` — `@Controller` Spring servlets (`MyScoreboardServlet`, `CommissionerServlet`, `LoginServlet`, etc.)
- `src/main/java/services/` — `@Service` business logic (`CommonProcessingService`, `NFLGameFetcherService`, `ServletUtility`, `NFLSeasonCalculator`)
- `src/main/java/helpers/` — `SqlConnector*` DAOs, `ApiFetchers`, `ApiParsers`
- `src/main/java/model/` — POJOs (`User`, `Game`, `PicksPrice`)
- `src/main/java/config/` — `AppConfig.java` (Spring config; `@EnableScheduling` is here)
- `src/main/webapp/` — JSPs and static assets
- `src/main/webapp/WEB-INF/` — `web.xml`, Spring config

## Local dev workflow

- **Dev server:** Tomcat on port 8081 inside Eclipse; Spring profile = `dev`.
- **Prod server:** Shared Tomcat on port 8080 outside Eclipse, running as Windows service `Tomcat10`. Spring profile = `prod`. `setenv.bat` at `C:\Program Files\Apache Software Foundation\Tomcat 10.1\bin\` loads `-Dspring.config.additional-location=C:\KOTH-secrets\secrets.properties` globally.
- **Eclipse refresh sequence after JSP changes** (mandatory — JSPs cache aggressively): **F5 (Project Refresh) → Project Clean → Server Clean → Clean Tomcat Work Directory → restart.**
- **Eclipse project import:** use General → Existing Projects into Workspace, **not** Maven import (loses the WTP `org.eclipse.jst.component.dependency=/WEB-INF/lib` attribute).
- **Git** is on PATH directly from PowerShell. Remote = `MikeGriffin1959` on GitHub (private repo).

## Coding conventions

- **Logging:** `System.out.println("ClassName.method ...")` for trace logging. Match the existing style; no SLF4J in this repo.
- **Spring injection:** field `@Autowired` is the dominant pattern (see `CommonProcessingService`). Match it for consistency.
- **Never commit credentials.** Secrets live in `C:\KOTH-secrets\secrets.properties`, loaded via Spring's `additional-location` mechanism. The Gmail app password currently in `AppConfig.java` is a known exception and intentional — do not flag, move, or lecture about it.
- **MySQL reserved words:** new column names like `position`, `rank`, `order` need aliases (e.g., `position_code`, `overall_rank`).
- **Status values:** `game.status` stores raw ESPN values (`STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, `STATUS_HALFTIME`, `STATUS_END_PERIOD`, `STATUS_FINAL`). Friendly forms (`Final`, `F/OT`, `In Progress`, `Scheduled`) are produced at the controller layer by `MyScoreboardServlet.convertStatus()`. Backend code matches against `STATUS_*`; some legacy code accepts both forms (see `CommonProcessingService.isWinningPick`).
- **Game period:** numeric string `"1"`/`"2"`/`"3"`/`"4"`/`"5"+` (where `5+` = OT). Halftime convention: `period = "2"` AND `displayClock = "0:00"`.
- **Game displayClock:** `M:SS` format (e.g., `"12:34"`, `"0:00"`).
- **A tie is a loss** — baked into `isWinningPick`. Never frame ties as draws or hopeful outcomes.

## Key tables

- **`user`**: `idUser` (PK), `username`, `firstName`, `lastName`, `email`, `cellNumber`, `password`, `initialPicks`, `remainingPicksLive`, `remainingPicksWeekly`, `admin`, `commish`, `picksSeason`, `picksPaid`, `rememberMeToken`.
- **`picks`**: `pickID` (PK), `userId` (FK to user), `season`, `week`, `gameId` (INT, references `game.GameID`), `selectedTeam` (team name string), `created_at`.
- **`game`**: `GameID` (PK), `season`, `week`, `date` (UTC), `homeTeamId`/`homeTeamName`/`homeScore`, `awayTeamId`/`awayTeamName`/`awayScore`, `status`, `pointSpread`, `overUnder`, `period`, `displayClock`, plus team abbreviations and display flags.
- **`teams`**: `apiTeamID` (PK), `apiTeamName`, `apiTeamShortName`, `apiTeamFullName`, division/conference/geo metadata.
- **`picksprice`**: `picksPriceSeason` (PK), per-season config — `maxPicks`, `pickPrice1`–`pickPrice5`, `allowSignUp`, `maskPicks`, `kothSeason` (label like `"KOTH"` or `"KOTH 2"`). Settings for new features attach here.

## App architecture notes

- **Single-pool design.** One pool per season — the entire `user` table for that season *is* the pool. No "group" abstraction.
- **`CommonProcessingService.processCommonData`** is the central per-request data refresh — pulls users, picks, games, computes losses and remaining picks. Called from most servlets. The whole computation cycle (`updateCache`) is request-driven; there is no background score-refresh job (yet — the commentary feature will introduce the first `@Scheduled` bean).
- **`ServletUtility.setCommonAttributes`** sets request-scoped season/week.
- **`@EnableScheduling` is in `AppConfig.java`** — `@Scheduled` beans drop in with no extra config.
- **Login/session:** `LoginServlet` + remember-me multi-device token table. Most servlets check `session.getAttribute("userName")` and redirect to login if absent.
- **AJAX pattern:** commissioner-style settings POST to `CommissionerServlet` with an `action` parameter and return JSON (`{success, message, messageType}`). Frontend toggles show alerts on response.

## External integrations

- **ESPN APIs** via `ApiFetchers` + `ApiParsers` for games, scores, odds.
- **Email** via `JavaMailSender` (Gmail SMTP) configured in `AppConfig.java`.
- **Cloudflare** for DNS, dual-ISP load balancing (AT&T + Xfinity via No-IP DDNS `sundog-att.ddns.net` / `sundog-xfinity.ddns.net`), Origin Rules for port routing, and SSL termination.

## Deployment

- Build to `KOTH.war`; deploy by dropping into `Tomcat 10.1\webapps\`. Context path `/KOTH`. Shares Tomcat with `Golf.war`, `Brier.war`, `ninjasensation.war`.

## Active feature work

- **AI-powered commentary** — see `KOTH-Commentary-Design.md`. Currently at M1 (Foundation). Adds Anthropic-API-driven snarky narration with four streams (Weekly Preview, Kickoff Reveal, live Event-driven, Week Recap). Introduces the first `@Scheduled` bean (`CommentaryScheduler`, lands in M2).

## Sister apps (same machine, useful for reference)

- **GolferFest** at `C:\dev\golf\` — golf pool app with the original `CommentaryService` that KOTH's commentary feature parallels.
- **Brier** at `C:\dev\brier\` — Kalshi prediction-market trader, currently in Phase 1 shadow mode.
- **NinjaSensation** at `C:\dev\ninjasensation\` — capture-strategy consulting site at ninjasensation.com.
