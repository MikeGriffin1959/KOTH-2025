-- ============================================================================
-- KOTH Commentary feature — M1 schema migration
-- Design doc: KOTH-Commentary-Design.md §5.1, §5.2
-- Apply manually to the dev DB (no Flyway in this repo):
--   mysql -u root -p koth < KOTH/db/M1_commentary.sql
-- Table names are schema-qualified PascalCase to match existing KOTH.PicksPrice
-- conventions (the design doc's lowercase SQL is illustrative).
-- ============================================================================

-- §5.1 — Commentary settings columns on the per-season config table.
--        Booleans use bit(1) to mirror the existing maskPicks / allowSignUp
--        columns (the doc's TINYINT(1) is equivalent under JDBC getBoolean).
--        previewDayOfWeek=5 => Friday (java.time.DayOfWeek convention).
ALTER TABLE KOTH.PicksPrice
    ADD COLUMN snarkLevel INT DEFAULT 5,
    ADD COLUMN commentaryEnabled BIT(1) DEFAULT b'0',
    ADD COLUMN commentaryNotifications BIT(1) DEFAULT b'0',
    ADD COLUMN previewDayOfWeek INT DEFAULT 5;

-- §5.2 — Commentary output table. kothSeason carried for multi-pool insurance
--        (populated but not filtered on in v1).
CREATE TABLE IF NOT EXISTS KOTH.Commentary (
    commentaryId    INT NOT NULL AUTO_INCREMENT,
    season          INT NOT NULL,
    kothSeason      VARCHAR(10) DEFAULT NULL,
    week            INT NOT NULL,
    streamType      VARCHAR(32) NOT NULL,         -- PREVIEW, REVEAL, EVENT, RECAP, TEST
    eventType       VARCHAR(32) DEFAULT NULL,     -- TROUBLE, ELIMINATION, etc. (NULL for non-event streams)
    affectedUserIds VARCHAR(255) DEFAULT NULL,    -- comma-separated idUser values
    gameId          INT DEFAULT NULL,             -- references game.GameID
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
