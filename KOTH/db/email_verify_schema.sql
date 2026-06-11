-- Email verification columns (ported from GolferFest). One-time apply to
-- dev (localhost koth) AND prod (golf-prod RDS koth):
--   mysql -u <user> -p < KOTH/db/email_verify_schema.sql
-- Re-running errors harmlessly on the duplicate columns.
ALTER TABLE KOTH.User
    ADD COLUMN emailVerified TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN emailVerifiedDate DATETIME NULL,
    ADD COLUMN emailVerifyToken VARCHAR(255) NULL;
