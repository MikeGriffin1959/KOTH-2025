-- ============================================================================
-- Commentary M5: dossier tables (design doc §5.3 / §5.4). Safe to re-run.
-- Apply to dev (localhost) AND prod (golf-prod RDS):
--   mysql -u <user> -p < KOTH/db/dossier_schema.sql
-- ============================================================================

-- Per-user, per-season personality profile. kothSeason carried for multi-pool
-- insurance (populated from picksprice.kothSeason; not filtered on in v1).
CREATE TABLE IF NOT EXISTS KOTH.user_dossier (
    dossierId     INT NOT NULL AUTO_INCREMENT,
    userId        INT NOT NULL,
    season        INT NOT NULL,
    kothSeason    VARCHAR(10) DEFAULT NULL,
    displayName   VARCHAR(100) DEFAULT NULL,    -- commentary name; defaults to firstName
    personality   TEXT,
    rivalries     TEXT,
    sensitivities TEXT,                          -- pull-punches guidance; load-bearing for ELIMINATION
    updatedAt     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dossierId),
    UNIQUE KEY uq_user_season (userId, season, kothSeason),
    CONSTRAINT fk_dossier_user FOREIGN KEY (userId) REFERENCES KOTH.User (idUser)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Pool-level identity/lore, one row per season. kothSeason NOT NULL because it
-- is part of the composite PK (empty-string fallback per design §5.4).
CREATE TABLE IF NOT EXISTS KOTH.pool_dossier (
    season            INT NOT NULL,
    kothSeason        VARCHAR(10) NOT NULL DEFAULT '',
    poolIdentity      TEXT,
    poolHistory       TEXT,
    poolLore          TEXT,
    commissionerNotes TEXT,
    toneGuidance      TEXT,
    updatedAt         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (season, kothSeason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
