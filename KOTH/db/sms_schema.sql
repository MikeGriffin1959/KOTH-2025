-- ============================================================================
-- KOTH SMS notification support (ported from GolferFest's sms_schema.sql,
-- adapted to KOTH's single-pool/season model). Safe to re-run.
-- Apply manually to dev (localhost koth) AND prod (golf-prod RDS koth):
--   mysql -u <user> -p koth < KOTH/db/sms_schema.sql
-- ============================================================================

-- Phone-verification columns on the existing user table (cellNumber already exists).
-- MySQL 8 has no ADD COLUMN IF NOT EXISTS pre-8.0.29-alike guard for all forms;
-- these will error harmlessly if re-run — drop them from the script after first apply.
ALTER TABLE KOTH.User
    ADD COLUMN phoneVerified TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN phoneVerifiedDate DATETIME NULL;

CREATE TABLE IF NOT EXISTS KOTH.sms_notification_types (
  notification_type_id INT AUTO_INCREMENT PRIMARY KEY,
  type_key       VARCHAR(50)  NOT NULL UNIQUE,
  display_name   VARCHAR(100) NOT NULL,
  description    VARCHAR(255) NULL,
  category       VARCHAR(10)  NOT NULL,            -- 'USER' or 'COMMISH'
  default_enabled TINYINT(1)  NOT NULL DEFAULT 1,
  active         TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order     INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS KOTH.user_sms_preferences (
  idUser               INT      NOT NULL,
  notification_type_id INT      NOT NULL,
  enabled              TINYINT(1) NOT NULL DEFAULT 1,
  updated_date         DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (idUser, notification_type_id)
);

-- Idempotency log: KOTH is season/week scoped (no tournaments/groups).
CREATE TABLE IF NOT EXISTS KOTH.sms_notification_log (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  season    INT          NOT NULL,
  week      INT          NOT NULL DEFAULT 0,
  type_key  VARCHAR(50)  NOT NULL,
  ref_key   VARCHAR(100) NOT NULL DEFAULT '',
  sent_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_sms_log (season, week, type_key, ref_key)
);

-- Seed notification types (type_key MUST match SmsNotificationType enum names).
INSERT INTO KOTH.sms_notification_types (type_key, display_name, description, category, default_enabled, sort_order) VALUES
  ('PICKS_REMINDER',       'Picks Reminder',       'Reminder before kickoff if you have not made your pick',     'USER', 1, 10),
  ('ELIMINATION_ALERT',    'Elimination Alert',    'When one of your picks is eliminated',                        'USER', 1, 20),
  ('WEEK_RECAP',           'Week Recap',           'Weekly recap after the last game goes final',                 'USER', 1, 30),
  ('COMMENTARY_EVENT',     'Live Commentary',      'Live game-day commentary alerts (upsets, trouble, drama)',    'USER', 0, 40),
  ('COMMISSIONER_MESSAGE', 'Commissioner Message', 'A direct message from the commissioner',                      'COMMISH', 1, 200),
  ('ACCOUNT_ALERT',        'Account Alert',        'Important account alerts',                                    'COMMISH', 1, 210)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  description  = VALUES(description),
  category     = VALUES(category),
  sort_order   = VALUES(sort_order),
  active       = 1;
