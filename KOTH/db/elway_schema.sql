-- Nate Silver's ELWAY projections as a fourth Edge source (Sep 2026).
-- Source: Silver Bulletin "ELWAY future game projections" table (paid Substack),
-- imported by the commissioner via paste-in on the Edge page. One row per game.
--
-- CREATE is re-runnable. The ALTER is NOT (MySQL 8 has no ADD COLUMN IF NOT EXISTS);
-- applied to local dev + prod RDS on 2026-09-09.

CREATE TABLE IF NOT EXISTS KOTH.ElwayProjection (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  season       INT NOT NULL,
  week         INT NOT NULL,
  homeTeamId   INT NOT NULL,
  awayTeamId   INT NOT NULL,
  homeWinProb  DECIMAL(6,5) NOT NULL,      -- P(home win), 0..1
  homeSpread   DECIMAL(5,2) NULL,          -- negative = home favored (Elway convention)
  total        DECIMAL(5,2) NULL,
  neutralSite  TINYINT(1) NOT NULL DEFAULT 0,
  importedAt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_elway_game (season, week, homeTeamId, awayTeamId),
  KEY idx_elway_week (season, week)
);

ALTER TABLE KOTH.EdgeSnapshot
  ADD COLUMN elwayHome       DECIMAL(6,5) NULL AFTER eloAway,
  ADD COLUMN elwayAway       DECIMAL(6,5) NULL AFTER elwayHome,
  ADD COLUMN elwaySpreadHome DECIMAL(5,2) NULL AFTER elwayAway;
